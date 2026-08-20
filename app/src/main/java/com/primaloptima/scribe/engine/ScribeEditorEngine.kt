package com.primaloptima.scribe.engine

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.primaloptima.scribe.util.model.OutlineEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

data class LineFocusRequest(
    val lineIndex: Int,
    val column: Int = -1,
    val requestId: Long = System.nanoTime()
)

/**
 * Core ViewModel driving the Scribe Prose Editor Engine.
 * Manages document buffer, formatting spans, undo/redo history, background word-count,
 * outline hierarchy extraction, and line-level virtualization sync.
 */
@OptIn(FlowPreview::class)
class ScribeEditorEngine(
    initialContent: String = "",
    initialFormats: List<FormatSpan> = emptyList()
) : ViewModel() {

    val buffer = DocumentBuffer(initialContent)
    val formats = FormatRegistry().also { it.loadAll(initialFormats) }
    val undoStack = UndoManager(limit = 200)

    // ── Public Observable State ──────────────────────────────────────────

    private val _lineCount = mutableIntStateOf(buffer.lineCount())
    val lineCount: State<Int> get() = _lineCount

    private val _canUndo = mutableStateOf(undoStack.canUndo())
    val canUndo: State<Boolean> get() = _canUndo

    private val _canRedo = mutableStateOf(undoStack.canRedo())
    val canRedo: State<Boolean> get() = _canRedo

    // Revision tracker to trigger per-line LaunchedEffect updates when lines are modified externally
    private val _documentRevision = mutableIntStateOf(0)
    val documentRevision: State<Int> get() = _documentRevision

    // Focus navigation channel
    private val _focusRequests = MutableSharedFlow<LineFocusRequest>(extraBufferCapacity = 8)
    val focusRequests: SharedFlow<LineFocusRequest> = _focusRequests.asSharedFlow()

    // ── Background Computed States ───────────────────────────────────────

    private val _wordCount = MutableStateFlow(0)
    val wordCount: StateFlow<Int> = _wordCount.asStateFlow()

    private val _charCount = MutableStateFlow(0)
    val charCount: StateFlow<Int> = _charCount.asStateFlow()

    private val _outline = MutableStateFlow<List<OutlineEntry>>(emptyList())
    val outline: StateFlow<List<OutlineEntry>> = _outline.asStateFlow()

    // Mutation stream for debounced calculations
    private val mutationEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    // ── Find and Replace ─────────────────────────────────────────────────

    val searchEngine = FindReplaceEngine(
        getBuffer = { buffer },
        onDocumentModified = {
            onBufferMutatedExternally()
        }
    )

    init {
        // Initial stats
        val initialText = buffer.asString()
        _wordCount.value = countWords(initialText)
        _charCount.value = initialText.length
        extractOutlineAsync(initialText)

        // 1. Debounced word and char count computation
        mutationEvents
            .debounce(300)
            .onEach {
                val snapshot = buffer.asString()
                val words = withContext(Dispatchers.Default) { countWords(snapshot) }
                val chars = snapshot.length
                _wordCount.value = words
                _charCount.value = chars
            }
            .flowOn(Dispatchers.Default)
            .launchIn(viewModelScope)

        // 2. Debounced outline extraction
        mutationEvents
            .debounce(500)
            .onEach {
                val snapshot = buffer.asString()
                extractOutlineAsync(snapshot)
            }
            .flowOn(Dispatchers.Default)
            .launchIn(viewModelScope)
    }

    private fun notifyMutation() {
        _lineCount.intValue = buffer.lineCount()
        _canUndo.value = undoStack.canUndo()
        _canRedo.value = undoStack.canRedo()
        _documentRevision.intValue++
        mutationEvents.tryEmit(Unit)
    }

    private fun onBufferMutatedExternally() {
        notifyMutation()
    }

    // ── Line-Level Editing API ───────────────────────────────────────────

    fun onLineChanged(lineIndex: Int, newText: String, cursorPos: Int) {
        val lineStart = buffer.lineStart(lineIndex)
        val oldLineText = buffer.lineContent(lineIndex)
        if (oldLineText == newText) return

        val oldLength = oldLineText.length
        val newLength = newText.length
        val lineEnd = lineStart + oldLength

        // Record Undo
        val edit = Edit.Compound(
            listOf(
                buffer.delete(lineStart, lineEnd),
                buffer.insert(lineStart, newText)
            )
        )

        // Adjust formatting spans
        formats.adjustForDelete(lineStart, oldLength)
        formats.adjustForInsert(lineStart, newLength)

        undoStack.push(
            UndoEntry(
                bufferEdit = edit,
                formatEdit = null,
                cursorBefore = CursorPos(lineIndex, oldLength),
                cursorAfter = CursorPos(lineIndex, cursorPos)
            )
        )

        notifyMutation()
    }

    fun onLineInserted(afterLineIndex: Int, splitAt: Int, currentLineText: String) {
        val lineStart = buffer.lineStart(afterLineIndex)
        val splitOffset = lineStart + splitAt.coerceIn(0, currentLineText.length)

        // Insert newline at split position
        val edit = buffer.insert(splitOffset, "\n")
        formats.adjustForInsert(splitOffset, 1)

        undoStack.push(
            UndoEntry(
                bufferEdit = edit,
                formatEdit = null,
                cursorBefore = CursorPos(afterLineIndex, splitAt),
                cursorAfter = CursorPos(afterLineIndex + 1, 0),
                label = "New Line"
            )
        )

        notifyMutation()
        requestLineFocus(afterLineIndex + 1, 0)
    }

    fun onLineMerge(lineIndex: Int) {
        if (lineIndex <= 0) return
        val prevLineIndex = lineIndex - 1
        val prevLineLength = buffer.lineLength(prevLineIndex)
        val currentLineStart = buffer.lineStart(lineIndex)

        // Delete the newline character right before currentLineStart
        if (currentLineStart > 0) {
            val deletePos = currentLineStart - 1
            val edit = buffer.delete(deletePos, currentLineStart)
            formats.adjustForDelete(deletePos, 1)

            undoStack.push(
                UndoEntry(
                    bufferEdit = edit,
                    formatEdit = null,
                    cursorBefore = CursorPos(lineIndex, 0),
                    cursorAfter = CursorPos(prevLineIndex, prevLineLength),
                    label = "Merge Line"
                )
            )

            notifyMutation()
            requestLineFocus(prevLineIndex, prevLineLength)
        }
    }

    fun insertAtCursor(lineIndex: Int, cursorPos: Int, text: String) {
        if (text.isEmpty()) return
        val lineStart = buffer.lineStart(lineIndex)
        val insertPos = lineStart + cursorPos.coerceAtLeast(0)

        val edit = buffer.insert(insertPos, text)
        formats.adjustForInsert(insertPos, text.length)

        undoStack.push(
            UndoEntry(
                bufferEdit = edit,
                formatEdit = null,
                cursorBefore = CursorPos(lineIndex, cursorPos),
                cursorAfter = CursorPos(lineIndex, cursorPos + text.length),
                label = "Insert"
            )
        )

        notifyMutation()
        requestLineFocus(lineIndex, cursorPos + text.length)
    }

    fun applyFormatWrap(lineIndex: Int, selStart: Int, selEnd: Int, open: String, close: String) {
        val lineStart = buffer.lineStart(lineIndex)
        val s = minOf(selStart, selEnd)
        val e = maxOf(selStart, selEnd)

        if (s == e) {
            val insertText = "$open$close"
            insertAtCursor(lineIndex, s, insertText)
            requestLineFocus(lineIndex, s + open.length)
        } else {
            val startPos = lineStart + s
            val endPos = lineStart + e
            val selectedText = buffer.substring(startPos, endPos)
            val wrapped = "$open$selectedText$close"

            val edit = Edit.Compound(
                listOf(
                    buffer.delete(startPos, endPos),
                    buffer.insert(startPos, wrapped)
                )
            )
            formats.adjustForDelete(startPos, endPos - startPos)
            formats.adjustForInsert(startPos, wrapped.length)

            undoStack.push(
                UndoEntry(
                    bufferEdit = edit,
                    formatEdit = null,
                    cursorBefore = CursorPos(lineIndex, s),
                    cursorAfter = CursorPos(lineIndex, s + wrapped.length),
                    label = "Format Wrap"
                )
            )
            notifyMutation()
            requestLineFocus(lineIndex, s + wrapped.length)
        }
    }

    fun toggleFormat(type: FormatType, selStart: Int, selEnd: Int) {
        val s = minOf(selStart, selEnd)
        val e = maxOf(selStart, selEnd)
        val formatEdit = formats.toggleSpan(type, s, e)

        undoStack.push(
            UndoEntry(
                bufferEdit = Edit.Compound(emptyList()),
                formatEdit = formatEdit,
                cursorBefore = CursorPos(0, s),
                cursorAfter = CursorPos(0, e),
                label = "Toggle ${type.name}"
            )
        )
        notifyMutation()
    }

    fun toggleFormatOnLine(lineIndex: Int, type: FormatType) {
        val start = buffer.lineStart(lineIndex)
        val length = buffer.lineLength(lineIndex)
        val end = start + length
        val formatEdit = formats.toggleSpan(type, start, end)

        undoStack.push(
            UndoEntry(
                bufferEdit = Edit.Compound(emptyList()),
                formatEdit = formatEdit,
                cursorBefore = CursorPos(lineIndex, 0),
                cursorAfter = CursorPos(lineIndex, length),
                label = "Toggle Line ${type.name}"
            )
        )
        notifyMutation()
    }

    fun requestLineFocus(lineIndex: Int, column: Int = -1) {
        _focusRequests.tryEmit(LineFocusRequest(lineIndex, column))
    }

    // ── History ───────────────────────────────────────────────────────────

    fun undo() {
        val entry = undoStack.undo(buffer, formats) ?: return
        notifyMutation()
        requestLineFocus(entry.cursorBefore.line, entry.cursorBefore.column)
    }

    fun redo() {
        val entry = undoStack.redo(buffer, formats) ?: return
        notifyMutation()
        requestLineFocus(entry.cursorAfter.line, entry.cursorAfter.column)
    }

    fun saveSnapshot(label: String) {
        undoStack.saveCheckpoint(label)
        notifyMutation()
    }

    // ── Serialization & Document Loading ─────────────────────────────────

    fun exportPlainText(): String = buffer.asString()

    fun exportWithFormats(): SerializedDocument {
        return SerializedDocument(
            version = 2,
            plainText = buffer.asString(),
            spans = formats.exportAll().map { it.toSerializedSpan() }
        )
    }

    fun loadDocument(doc: SerializedDocument) {
        // Reset buffer and formats
        val newBuffer = DocumentBuffer(doc.plainText)
        val newSpans = doc.spans.mapNotNull {
            try {
                FormatSpan(FormatType.valueOf(it.type), it.start, it.end)
            } catch (_: Exception) {
                null
            }
        }

        // Replace internal buffer data
        buffer.delete(0, buffer.length())
        if (doc.plainText.isNotEmpty()) {
            buffer.insert(0, doc.plainText)
        }
        formats.loadAll(newSpans)
        undoStack.clear()
        searchEngine.clear()

        notifyMutation()
        val text = buffer.asString()
        _wordCount.value = countWords(text)
        _charCount.value = text.length
        extractOutlineAsync(text)
    }

    // ── Internal Helpers ─────────────────────────────────────────────────

    private fun extractOutlineAsync(text: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val totalLines = buffer.lineCount()
            val entries = mutableListOf<OutlineEntry>()

            for (i in 0 until totalLines) {
                val content = buffer.lineContent(i).trim()
                if (content.isEmpty()) continue

                val lineStart = buffer.lineStart(i)
                val lineEnd = lineStart + content.length
                val spans = formats.spansIn(lineStart, lineEnd)
                val headingSpan = spans.firstOrNull { it.type.isHeading() }

                if (headingSpan != null) {
                    entries.add(
                        OutlineEntry(
                            level = headingSpan.type.headingLevel(),
                            text = content,
                            lineIndex = i
                        )
                    )
                } else if (content.startsWith("#")) {
                    // Markdown heading fallback (# H1, ## H2, ### H3)
                    val hashes = content.takeWhile { it == '#' }
                    if (hashes.length in 1..4) {
                        val headingText = content.drop(hashes.length).trim()
                        if (headingText.isNotEmpty()) {
                            entries.add(
                                OutlineEntry(
                                    level = hashes.length,
                                    text = headingText,
                                    lineIndex = i
                                )
                            )
                        }
                    }
                }
            }

            withContext(Dispatchers.Main) {
                _outline.value = entries
            }
        }
    }

    companion object {
        private val WORD_PATTERN = Pattern.compile("\\b\\w+\\b")

        fun countWords(text: String): Int {
            if (text.isBlank()) return 0
            var count = 0
            val matcher = WORD_PATTERN.matcher(text)
            while (matcher.find()) {
                count++
            }
            return count
        }
    }
}
