package com.primaloptima.scribe.engine

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.TextRange
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

data class LineFocusRequest(
    val lineIndex: Int,
    val column: Int = -1,
    val targetOffset: Int = -1,
    val requestId: Long = System.nanoTime()
)

/**
 * Core ViewModel driving the Scribe Prose Editor Engine.
 * Manages document buffer, formatting spans, undo/redo history, background word-count,
 * outline hierarchy extraction, active selection tracking, and seamless touch/typing synchronization.
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

    // Active line and local selection within the active line
    private val _activeLineIndex = mutableIntStateOf(0)
    val activeLineIndex: State<Int> get() = _activeLineIndex

    private val _activeLineSelection = mutableStateOf(TextRange(0, 0))
    val activeLineSelection: State<TextRange> get() = _activeLineSelection

    // Revision tracker to trigger UI updates when document is mutated
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

    private val _proseAnalysis = MutableStateFlow(ProseAnalysisResult())
    val proseAnalysis: StateFlow<ProseAnalysisResult> = _proseAnalysis.asStateFlow()

    // Mutation stream for debounced calculations
    private val mutationEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    // ── Find and Replace ─────────────────────────────────────────────────

    val searchEngine = FindReplaceEngine(
        getBuffer = { buffer },
        onDocumentModified = {
            notifyMutation()
        }
    )

    init {
        // Initial stats
        val initialText = initialContent
        _wordCount.value = countWords(initialText)
        _charCount.value = initialText.length
        extractOutlineAsync(initialText)
        runProseAnalysisAsync(initialText)

        // 1. Debounced word and char count computation
        mutationEvents
            .debounce(250)
            .onEach {
                val snapshot = buffer.asString()
                val words = withContext(Dispatchers.Default) { countWords(snapshot) }
                val chars = buffer.length()
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

        // 3. Debounced deep prose analysis on worker coroutines
        mutationEvents
            .debounce(600)
            .onEach {
                val snapshot = buffer.asString()
                runProseAnalysisAsync(snapshot)
            }
            .flowOn(Dispatchers.Default)
            .launchIn(viewModelScope)
    }

    private fun runProseAnalysisAsync(text: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val result = ProseAnalysisEngine.analyze(text)
            withContext(Dispatchers.Main) {
                _proseAnalysis.value = result
            }
        }
    }

    fun notifyMutation() {
        _lineCount.intValue = buffer.lineCount()
        _canUndo.value = undoStack.canUndo()
        _canRedo.value = undoStack.canRedo()
        _documentRevision.intValue++
        mutationEvents.tryEmit(Unit)
    }

    /**
     * Mirrors the active paragraph's local selection into engine state
     * for formatting, shortcuts, and navigation commands.
     */
    fun updateLineSelection(lineIndex: Int, localStart: Int, localEnd: Int) {
        val totalLines = buffer.lineCount()
        val validLine = lineIndex.coerceIn(0, (totalLines - 1).coerceAtLeast(0))
        val lineLen = buffer.lineLength(validLine)
        val safeStart = localStart.coerceIn(0, lineLen)
        val safeEnd = localEnd.coerceIn(0, lineLen)
        _activeLineIndex.intValue = validLine
        _activeLineSelection.value = TextRange(safeStart, safeEnd)
    }

    val activeDocSelection: TextRange
        get() {
            val totalLines = buffer.lineCount()
            if (totalLines == 0) return TextRange.Zero
            val lineIdx = _activeLineIndex.intValue.coerceIn(0, totalLines - 1)
            val lineStart = buffer.lineStart(lineIdx)
            val lineLen = buffer.lineLength(lineIdx)
            val localSel = _activeLineSelection.value
            val start = (lineStart + localSel.min).coerceIn(lineStart, lineStart + lineLen)
            val end = (lineStart + localSel.max).coerceIn(lineStart, lineStart + lineLen)
            return TextRange(start, end)
        }

    // ── Editing API ──────────────────────────────────────────────────────

    fun onLineChanged(lineIndex: Int, newText: String, cursorPos: Int) {
        val totalLines = buffer.lineCount()
        val validLine = lineIndex.coerceIn(0, (totalLines - 1).coerceAtLeast(0))
        val lineStart = buffer.lineStart(validLine)
        val oldLineText = buffer.lineContent(validLine)
        if (oldLineText == newText) return

        val oldLength = oldLineText.length
        val newLength = newText.length
        val lineEnd = lineStart + oldLength

        val edit = Edit.Compound(
            listOf(
                buffer.delete(lineStart, lineEnd),
                buffer.insert(lineStart, newText)
            )
        )

        formats.adjustForDelete(lineStart, oldLength)
        formats.adjustForInsert(lineStart, newLength)

        undoStack.push(
            UndoEntry(
                bufferEdit = edit,
                formatEdit = null,
                cursorBefore = CursorPos(validLine, oldLength),
                cursorAfter = CursorPos(validLine, cursorPos)
            )
        )

        notifyMutation()

        if (newText.contains('\n')) {
            val firstNewlineIdx = newText.indexOf('\n')
            if (cursorPos > firstNewlineIdx) {
                val nextLineIdx = validLine + 1
                val colInNextLine = (cursorPos - firstNewlineIdx - 1).coerceAtLeast(0)
                requestLineFocus(nextLineIdx, colInNextLine)
            } else {
                requestLineFocus(validLine, cursorPos)
            }
        }
    }

    fun onLineInserted(afterLineIndex: Int, splitAt: Int, currentLineText: String) {
        val totalLines = buffer.lineCount()
        val validLine = afterLineIndex.coerceIn(0, (totalLines - 1).coerceAtLeast(0))
        val lineStart = buffer.lineStart(validLine)
        val splitOffset = lineStart + splitAt.coerceIn(0, currentLineText.length)

        val edit = buffer.insert(splitOffset, "\n")
        formats.adjustForInsert(splitOffset, 1)

        undoStack.push(
            UndoEntry(
                bufferEdit = edit,
                formatEdit = null,
                cursorBefore = CursorPos(validLine, splitAt),
                cursorAfter = CursorPos(validLine + 1, 0),
                label = "New Line"
            )
        )

        notifyMutation()
        requestLineFocus(validLine + 1, 0)
    }

    fun onLineMerge(lineIndex: Int) {
        if (lineIndex <= 0) return
        val prevLineIndex = lineIndex - 1
        val prevLineLength = buffer.lineLength(prevLineIndex)
        val currentLineStart = buffer.lineStart(lineIndex)

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

    fun insertAtCursor(text: String) {
        if (text.isEmpty()) return
        val totalLines = buffer.lineCount()
        val lineIdx = _activeLineIndex.intValue.coerceIn(0, (totalLines - 1).coerceAtLeast(0))
        val cursorPos = _activeLineSelection.value.end
        insertAtCursor(lineIdx, cursorPos, text)
    }

    fun insertAtCursor(lineIndex: Int, cursorPos: Int, text: String) {
        if (text.isEmpty()) return
        val totalLines = buffer.lineCount()
        val validLine = lineIndex.coerceIn(0, (totalLines - 1).coerceAtLeast(0))
        val lineStart = buffer.lineStart(validLine)
        val lineLen = buffer.lineLength(validLine)
        val safeCursor = cursorPos.coerceIn(0, lineLen)
        val insertPos = lineStart + safeCursor

        val edit = buffer.insert(insertPos, text)
        formats.adjustForInsert(insertPos, text.length)

        val newCursorCol = safeCursor + text.length
        undoStack.push(
            UndoEntry(
                bufferEdit = edit,
                formatEdit = null,
                cursorBefore = CursorPos(validLine, safeCursor),
                cursorAfter = CursorPos(validLine, newCursorCol),
                label = "Insert"
            )
        )

        notifyMutation()
        requestLineFocus(validLine, newCursorCol)
    }

    fun applyFormatWrap(open: String, close: String) {
        val totalLines = buffer.lineCount()
        val lineIdx = _activeLineIndex.intValue.coerceIn(0, (totalLines - 1).coerceAtLeast(0))
        val sel = _activeLineSelection.value
        applyFormatWrap(lineIdx, sel.start, sel.end, open, close)
    }

    fun applyFormatWrap(lineIndex: Int, selStart: Int, selEnd: Int, open: String, close: String) {
        val totalLines = buffer.lineCount()
        val validLine = lineIndex.coerceIn(0, (totalLines - 1).coerceAtLeast(0))
        val lineStart = buffer.lineStart(validLine)
        val lineLen = buffer.lineLength(validLine)
        val s = minOf(selStart, selEnd).coerceIn(0, lineLen)
        val e = maxOf(selStart, selEnd).coerceIn(0, lineLen)

        if (s == e) {
            val insertText = "$open$close"
            insertAtCursor(validLine, s, insertText)
            requestLineFocus(validLine, s + open.length)
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
                    cursorBefore = CursorPos(validLine, s),
                    cursorAfter = CursorPos(validLine, s + wrapped.length),
                    label = "Format Wrap"
                )
            )

            notifyMutation()
            requestLineFocus(validLine, s + wrapped.length)
        }
    }

    fun deleteSelection() {
        val totalLines = buffer.lineCount()
        val lineIdx = _activeLineIndex.intValue.coerceIn(0, (totalLines - 1).coerceAtLeast(0))
        val sel = _activeLineSelection.value
        val lineStart = buffer.lineStart(lineIdx)
        val lineLen = buffer.lineLength(lineIdx)
        val s = minOf(sel.start, sel.end).coerceIn(0, lineLen)
        val e = maxOf(sel.start, sel.end).coerceIn(0, lineLen)

        if (s != e) {
            val startPos = lineStart + s
            val endPos = lineStart + e
            val edit = buffer.delete(startPos, endPos)
            formats.adjustForDelete(startPos, endPos - startPos)
            undoStack.push(
                UndoEntry(
                    bufferEdit = edit,
                    formatEdit = null,
                    cursorBefore = CursorPos(lineIdx, e),
                    cursorAfter = CursorPos(lineIdx, s),
                    label = "Delete Selection"
                )
            )
            notifyMutation()
            requestLineFocus(lineIdx, s)
        }
    }

    fun toggleFormat(type: FormatType) {
        val docSel = activeDocSelection
        toggleFormat(type, docSel.min, docSel.max)
    }

    fun toggleFormat(type: FormatType, selStart: Int, selEnd: Int) {
        val docLen = buffer.length()
        val s = minOf(selStart, selEnd).coerceIn(0, docLen)
        val e = maxOf(selStart, selEnd).coerceIn(0, docLen)
        val formatEdit = formats.toggleSpan(type, s, e)

        val lineIdx = _activeLineIndex.intValue
        val col = _activeLineSelection.value.end
        undoStack.push(
            UndoEntry(
                bufferEdit = Edit.Compound(emptyList()),
                formatEdit = formatEdit,
                cursorBefore = CursorPos(lineIdx, col),
                cursorAfter = CursorPos(lineIdx, col),
                label = "Toggle ${type.name}"
            )
        )
        notifyMutation()
    }

    fun toggleFormatOnLine(lineIndex: Int, type: FormatType) {
        val totalLines = buffer.lineCount()
        val validLine = lineIndex.coerceIn(0, (totalLines - 1).coerceAtLeast(0))
        val start = buffer.lineStart(validLine)
        val length = buffer.lineLength(validLine)
        val end = start + length
        val formatEdit = formats.toggleSpan(type, start, end)

        undoStack.push(
            UndoEntry(
                bufferEdit = Edit.Compound(emptyList()),
                formatEdit = formatEdit,
                cursorBefore = CursorPos(validLine, 0),
                cursorAfter = CursorPos(validLine, length),
                label = "Toggle Line ${type.name}"
            )
        )
        notifyMutation()
    }

    fun requestLineFocus(lineIndex: Int, column: Int = -1) {
        val totalLines = buffer.lineCount()
        val validLine = lineIndex.coerceIn(0, (totalLines - 1).coerceAtLeast(0))
        val lineStart = buffer.lineStart(validLine)
        val lineLen = buffer.lineLength(validLine)
        val safeCol = if (column >= 0) column.coerceIn(0, lineLen) else lineLen
        val targetPos = (lineStart + safeCol).coerceIn(0, buffer.length())

        _activeLineIndex.intValue = validLine
        _activeLineSelection.value = TextRange(safeCol)
        _focusRequests.tryEmit(LineFocusRequest(validLine, safeCol, targetPos))
    }

    fun requestOffsetFocus(targetOffset: Int) {
        val safeOffset = targetOffset.coerceIn(0, buffer.length())
        val lineIdx = buffer.lineIndexAt(safeOffset)
        val lineStart = buffer.lineStart(lineIdx)
        val col = (safeOffset - lineStart).coerceAtLeast(0)

        _activeLineIndex.intValue = lineIdx
        _activeLineSelection.value = TextRange(col)
        _focusRequests.tryEmit(LineFocusRequest(lineIdx, col, targetOffset = safeOffset))
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
        val plainText = doc.plainText

        // Replace internal buffer data
        buffer.delete(0, buffer.length())
        if (plainText.isNotEmpty()) {
            buffer.insert(0, plainText)
        }
        val newSpans = doc.spans.mapNotNull {
            try {
                FormatSpan(FormatType.valueOf(it.type), it.start, it.end)
            } catch (_: Exception) {
                null
            }
        }
        formats.loadAll(newSpans)
        undoStack.clear()
        searchEngine.clear()

        _activeLineIndex.intValue = 0
        _activeLineSelection.value = TextRange(0, 0)

        notifyMutation()
        _wordCount.value = countWords(plainText)
        _charCount.value = plainText.length
        extractOutlineAsync(plainText)
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
        fun countWords(text: CharSequence): Int {
            if (text.isEmpty()) return 0
            var count = 0
            var inWord = false
            for (i in 0 until text.length) {
                val c = text[i]
                if (c.isLetterOrDigit()) {
                    if (!inWord) {
                        inWord = true
                        count++
                    }
                } else {
                    inWord = false
                }
            }
            return count
        }
    }
}
