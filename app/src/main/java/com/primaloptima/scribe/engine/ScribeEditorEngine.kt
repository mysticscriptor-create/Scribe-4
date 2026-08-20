package com.primaloptima.scribe.engine

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.insert
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LineFocusRequest(
    val lineIndex: Int,
    val column: Int = -1,
    val targetOffset: Int = -1,
    val requestId: Long = System.nanoTime()
)

/**
 * Core ViewModel driving the Scribe Prose Editor Engine.
 *
 * Implements a unified, high-performance editor architecture inspired by Sora Editor:
 * - Single persistent TextFieldState avoiding IME churn, keyboard flickering, and focus drops.
 * - Native continuous multi-line selection spanning arbitrary paragraphs.
 * - Accurate touch-to-character offset resolution.
 * - Background debounced stats, outline trees, and prose analysis.
 */
@OptIn(FlowPreview::class, ExperimentalFoundationApi::class)
class ScribeEditorEngine(
    initialContent: String = "",
    initialFormats: List<FormatSpan> = emptyList()
) : ViewModel() {

    val buffer = DocumentBuffer(initialContent)
    val formats = FormatRegistry().also { it.loadAll(initialFormats) }
    val undoStack = UndoManager(limit = 200)

    // ── Unified Text & Selection State ──────────────────────────────────
    val state = TextFieldState(initialContent)

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

    // Revision tracker to trigger UI updates when formatting/buffer changes
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
            val updated = buffer.asString()
            if (state.text.toString() != updated) {
                state.edit {
                    replace(0, length, updated)
                }
            }
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

        // Sync keystrokes from TextFieldState into DocumentBuffer
        snapshotFlow { state.text.toString() }
            .drop(1)
            .onEach { newText ->
                syncBufferFromUI(newText)
            }
            .launchIn(viewModelScope)

        // Sync selection changes to active line and local selection
        snapshotFlow { state.selection }
            .onEach { sel ->
                syncSelectionFromUI(sel)
            }
            .launchIn(viewModelScope)

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

    private fun syncBufferFromUI(newText: String) {
        val oldText = buffer.asString()
        if (oldText == newText) return

        var prefix = 0
        val minLen = minOf(oldText.length, newText.length)
        while (prefix < minLen && oldText[prefix] == newText[prefix]) {
            prefix++
        }
        var suffixOld = oldText.length
        var suffixNew = newText.length
        while (suffixOld > prefix && suffixNew > prefix && oldText[suffixOld - 1] == newText[suffixNew - 1]) {
            suffixOld--
            suffixNew--
        }

        val deleteLen = suffixOld - prefix
        val insertText = newText.substring(prefix, suffixNew)

        if (deleteLen > 0) {
            buffer.delete(prefix, prefix + deleteLen)
            formats.adjustForDelete(prefix, deleteLen)
        }
        if (insertText.isNotEmpty()) {
            buffer.insert(prefix, insertText)
            formats.adjustForInsert(prefix, insertText.length)
        }

        notifyMutation()
    }

    private fun syncSelectionFromUI(sel: TextRange) {
        val totalLines = buffer.lineCount()
        val docLen = state.text.length
        val safeStart = sel.min.coerceIn(0, docLen)
        val safeEnd = sel.max.coerceIn(0, docLen)
        val lineIdx = if (totalLines > 0) buffer.lineIndexAt(safeEnd) else 0
        val lineStart = if (totalLines > 0) buffer.lineStart(lineIdx) else 0
        _activeLineIndex.intValue = lineIdx
        _activeLineSelection.value = TextRange(
            (safeStart - lineStart).coerceAtLeast(0),
            (safeEnd - lineStart).coerceAtLeast(0)
        )
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
     * Mirrors the active paragraph's local selection into engine state.
     */
    fun updateLineSelection(lineIndex: Int, localStart: Int, localEnd: Int) {
        val totalLines = buffer.lineCount()
        val validLine = lineIndex.coerceIn(0, (totalLines - 1).coerceAtLeast(0))
        val lineStart = buffer.lineStart(validLine)
        val lineLen = buffer.lineLength(validLine)
        val safeStart = localStart.coerceIn(0, lineLen)
        val safeEnd = localEnd.coerceIn(0, lineLen)
        _activeLineIndex.intValue = validLine
        _activeLineSelection.value = TextRange(safeStart, safeEnd)
        state.edit {
            selection = TextRange(lineStart + safeStart, lineStart + safeEnd)
        }
    }

    val activeDocSelection: TextRange
        get() = state.selection

    // ── Editing API ──────────────────────────────────────────────────────

    fun onLineChanged(lineIndex: Int, newText: String, cursorPos: Int) {
        val totalLines = buffer.lineCount()
        val validLine = lineIndex.coerceIn(0, (totalLines - 1).coerceAtLeast(0))
        val lineStart = buffer.lineStart(validLine)
        val lineEnd = lineStart + buffer.lineLength(validLine)

        state.edit {
            replace(lineStart, lineEnd, newText)
            selection = TextRange((lineStart + cursorPos).coerceIn(0, length))
        }
    }

    fun onLineInserted(afterLineIndex: Int, splitAt: Int, currentLineText: String) {
        val totalLines = buffer.lineCount()
        val validLine = afterLineIndex.coerceIn(0, (totalLines - 1).coerceAtLeast(0))
        val lineStart = buffer.lineStart(validLine)
        val splitOffset = lineStart + splitAt.coerceIn(0, currentLineText.length)

        state.edit {
            insert(splitOffset, "\n")
            selection = TextRange(splitOffset + 1)
        }
    }

    fun onLineMerge(lineIndex: Int) {
        if (lineIndex <= 0) return
        val currentLineStart = buffer.lineStart(lineIndex)
        if (currentLineStart > 0) {
            state.edit {
                delete(currentLineStart - 1, currentLineStart)
                selection = TextRange(currentLineStart - 1)
            }
        }
    }

    fun insertAtCursor(text: String) {
        if (text.isEmpty()) return
        val sel = state.selection
        val s = sel.min
        val e = sel.max
        state.edit {
            replace(s, e, text)
            selection = TextRange(s + text.length)
        }
    }

    fun insertAtCursor(lineIndex: Int, cursorPos: Int, text: String) {
        if (text.isEmpty()) return
        val totalLines = buffer.lineCount()
        val validLine = lineIndex.coerceIn(0, (totalLines - 1).coerceAtLeast(0))
        val lineStart = buffer.lineStart(validLine)
        val lineLen = buffer.lineLength(validLine)
        val safeCursor = cursorPos.coerceIn(0, lineLen)
        val insertPos = lineStart + safeCursor

        state.edit {
            insert(insertPos, text)
            selection = TextRange(insertPos + text.length)
        }
    }

    fun applyFormatWrap(open: String, close: String) {
        val sel = state.selection
        val s = sel.min
        val e = sel.max
        if (s == e) {
            state.edit {
                insert(s, "$open$close")
                selection = TextRange(s + open.length)
            }
        } else {
            val current = state.text.toString()
            val safeStart = s.coerceIn(0, current.length)
            val safeEnd = e.coerceIn(0, current.length)
            val selected = current.substring(safeStart, safeEnd)
            val wrapped = "$open$selected$close"
            state.edit {
                replace(safeStart, safeEnd, wrapped)
                selection = TextRange(safeStart, safeStart + wrapped.length)
            }
        }
    }

    fun applyFormatWrap(lineIndex: Int, selStart: Int, selEnd: Int, open: String, close: String) {
        val totalLines = buffer.lineCount()
        val validLine = lineIndex.coerceIn(0, (totalLines - 1).coerceAtLeast(0))
        val lineStart = buffer.lineStart(validLine)
        val s = (lineStart + minOf(selStart, selEnd)).coerceIn(0, state.text.length)
        val e = (lineStart + maxOf(selStart, selEnd)).coerceIn(0, state.text.length)

        if (s == e) {
            state.edit {
                insert(s, "$open$close")
                selection = TextRange(s + open.length)
            }
        } else {
            val current = state.text.toString()
            val selected = current.substring(s, e)
            val wrapped = "$open$selected$close"
            state.edit {
                replace(s, e, wrapped)
                selection = TextRange(s, s + wrapped.length)
            }
        }
    }

    fun deleteSelection() {
        val sel = state.selection
        val s = sel.min
        val e = sel.max
        if (s != e) {
            state.edit {
                delete(s, e)
                selection = TextRange(s)
            }
        }
    }

    fun toggleFormat(type: FormatType) {
        val sel = state.selection
        val s = sel.min
        val e = sel.max
        if (s == e) {
            if (buffer.lineCount() > 0) {
                val lineIdx = buffer.lineIndexAt(s)
                val lineStart = buffer.lineStart(lineIdx)
                val lineLen = buffer.lineLength(lineIdx)
                formats.toggleSpan(type, lineStart, lineStart + lineLen)
            }
        } else {
            formats.toggleSpan(type, s, e)
        }
        notifyMutation()
    }

    fun toggleFormat(type: FormatType, selStart: Int, selEnd: Int) {
        val docLen = buffer.length()
        val s = minOf(selStart, selEnd).coerceIn(0, docLen)
        val e = maxOf(selStart, selEnd).coerceIn(0, docLen)
        formats.toggleSpan(type, s, e)
        notifyMutation()
    }

    fun toggleFormatOnLine(lineIndex: Int, type: FormatType) {
        val totalLines = buffer.lineCount()
        val validLine = lineIndex.coerceIn(0, (totalLines - 1).coerceAtLeast(0))
        val start = buffer.lineStart(validLine)
        val length = buffer.lineLength(validLine)
        val end = start + length
        formats.toggleSpan(type, start, end)
        notifyMutation()
    }

    fun requestLineFocus(lineIndex: Int, column: Int = -1) {
        val totalLines = buffer.lineCount()
        val validLine = lineIndex.coerceIn(0, (totalLines - 1).coerceAtLeast(0))
        val lineStart = buffer.lineStart(validLine)
        val lineLen = buffer.lineLength(validLine)
        val safeCol = if (column >= 0) column.coerceIn(0, lineLen) else lineLen
        val targetPos = (lineStart + safeCol).coerceIn(0, state.text.length)

        state.edit {
            selection = TextRange(targetPos)
        }
        _activeLineIndex.intValue = validLine
        _activeLineSelection.value = TextRange(safeCol)
        _focusRequests.tryEmit(LineFocusRequest(validLine, safeCol, targetPos))
    }

    fun requestOffsetFocus(targetOffset: Int) {
        val safeOffset = targetOffset.coerceIn(0, state.text.length)
        val lineIdx = if (buffer.lineCount() > 0) buffer.lineIndexAt(safeOffset) else 0
        val lineStart = if (buffer.lineCount() > 0) buffer.lineStart(lineIdx) else 0
        val col = (safeOffset - lineStart).coerceAtLeast(0)

        state.edit {
            selection = TextRange(safeOffset)
        }
        _activeLineIndex.intValue = lineIdx
        _activeLineSelection.value = TextRange(col)
        _focusRequests.tryEmit(LineFocusRequest(lineIdx, col, targetOffset = safeOffset))
    }

    // ── History ───────────────────────────────────────────────────────────

    fun undo() {
        val entry = undoStack.undo(buffer, formats) ?: return
        val newDocText = buffer.asString()
        state.edit {
            replace(0, length, newDocText)
            val targetPos = (buffer.lineStart(entry.cursorBefore.line) + entry.cursorBefore.column).coerceIn(0, length)
            selection = TextRange(targetPos)
        }
        notifyMutation()
    }

    fun redo() {
        val entry = undoStack.redo(buffer, formats) ?: return
        val newDocText = buffer.asString()
        state.edit {
            replace(0, length, newDocText)
            val targetPos = (buffer.lineStart(entry.cursorAfter.line) + entry.cursorAfter.column).coerceIn(0, length)
            selection = TextRange(targetPos)
        }
        notifyMutation()
    }

    fun saveSnapshot(label: String) {
        undoStack.saveCheckpoint(label)
        notifyMutation()
    }

    // ── Serialization & Document Loading ─────────────────────────────────

    fun exportPlainText(): String = state.text.toString()

    fun exportWithFormats(): SerializedDocument {
        return SerializedDocument(
            version = 2,
            plainText = state.text.toString(),
            spans = formats.exportAll().map { it.toSerializedSpan() }
        )
    }

    fun loadDocument(doc: SerializedDocument) {
        val plainText = doc.plainText

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

        if (state.text.toString() != plainText) {
            state.edit {
                replace(0, length, plainText)
                selection = TextRange(0)
            }
        }

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
