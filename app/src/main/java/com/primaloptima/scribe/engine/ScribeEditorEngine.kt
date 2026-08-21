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
import kotlinx.coroutines.Job
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
 * High-performance 100k+ word lag-free architecture:
 * - Zero Main-Thread String Allocation during continuous typing.
 * - Single persistent TextFieldState avoiding IME keyboard lifecycle churn.
 * - Non-blocking background worker pipelines on Dispatchers.Default for buffer sync,
 *   word/char counting, outline extraction, and deep prose analytics.
 * - Fast-path index lookups and native Compose 1.12 TextFieldState rich-text styles.
 */
@OptIn(FlowPreview::class, ExperimentalFoundationApi::class)
class ScribeEditorEngine(
    initialContent: String = ""
) : ViewModel() {

    val buffer = DocumentBuffer(initialContent)
    val lineIndexer = FastLineIndexer().apply { index(initialContent) }
    val undoStack = UndoManager(limit = 200)

    // ── Unified Text & Selection State (Main Thread Text State) ───────────
    val state = TextFieldState(initialContent)

    // ── Public Observable State ──────────────────────────────────────────

    private val _lineCount = mutableIntStateOf(lineIndexer.lineCount)
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

    // Background job trackers to cancel stale computations on rapid typing
    private var analysisJob: Job? = null
    private var outlineJob: Job? = null

    // Mutation stream for debounced calculations
    private val mutationEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private var lastRecordedText: String = initialContent

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
        val initialLen = initialContent.length
        _charCount.value = initialLen
        _wordCount.value = countWords(initialContent)
        extractOutlineAsync(initialContent)
        runProseAnalysisAsync(initialContent)

        // 1. Debounced text diffing and line indexer cache update on worker threads
        snapshotFlow { state.text.toString() }
            .drop(1)
            .debounce(150)
            .onEach { textSnapshot ->
                val oldText = lastRecordedText
                if (oldText != textSnapshot) {
                    recordTextDiff(oldText, textSnapshot)
                    lastRecordedText = textSnapshot
                    lineIndexer.index(textSnapshot)
                    _lineCount.intValue = lineIndexer.lineCount
                    notifyMutation()
                }
            }
            .flowOn(Dispatchers.Default)
            .launchIn(viewModelScope)

        // 2. Selection changes: calculate line index lightly without blocking
        snapshotFlow { state.selection }
            .onEach { sel ->
                syncSelectionFromUI(sel)
            }
            .launchIn(viewModelScope)

        // 3. Debounced word and char count computation on worker threads
        snapshotFlow { state.text }
            .debounce(300)
            .onEach { textSnapshot ->
                val chars = textSnapshot.length
                val words = withContext(Dispatchers.Default) { countWords(textSnapshot) }
                _charCount.value = chars
                _wordCount.value = words
            }
            .flowOn(Dispatchers.Default)
            .launchIn(viewModelScope)

        // 4. Debounced outline extraction (500ms debounce on worker thread)
        snapshotFlow { state.text }
            .debounce(500)
            .onEach { textSnapshot ->
                extractOutlineAsync(textSnapshot.toString())
            }
            .flowOn(Dispatchers.Default)
            .launchIn(viewModelScope)

        // 5. Debounced deep prose analysis on worker thread (700ms debounce)
        snapshotFlow { state.text }
            .debounce(700)
            .onEach { textSnapshot ->
                runProseAnalysisAsync(textSnapshot.toString())
            }
            .flowOn(Dispatchers.Default)
            .launchIn(viewModelScope)

        // 6. Idle piece coalescing on DocumentBuffer
        snapshotFlow { state.text.toString() }
            .debounce(2000)
            .onEach {
                withContext(Dispatchers.Default) {
                    buffer.coalesce()
                }
            }
            .flowOn(Dispatchers.Default)
            .launchIn(viewModelScope)
    }

    private fun recordTextDiff(oldText: String, newText: String) {
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

        if (deleteLen > 0 || insertText.isNotEmpty()) {
            val cursorLine = lineIndexer.lineIndexForOffset(prefix)
            val cursorCol = prefix - lineIndexer.lineStart(cursorLine)

            val entry = UndoEntry(
                bufferEdit = when {
                    deleteLen > 0 && insertText.isNotEmpty() ->
                        Edit.Compound(
                            listOf(
                                Edit.Delete(prefix, prefix + deleteLen, oldText.substring(prefix, prefix + deleteLen)),
                                Edit.Insert(prefix, insertText.length, insertText)
                            )
                        )
                    deleteLen > 0 ->
                        Edit.Delete(prefix, prefix + deleteLen, oldText.substring(prefix, prefix + deleteLen))
                    else ->
                        Edit.Insert(prefix, insertText.length, insertText)
                },
                cursorBefore = CursorPos(cursorLine, cursorCol),
                cursorAfter = CursorPos(cursorLine, (cursorCol + insertText.length - deleteLen).coerceAtLeast(0))
            )
            undoStack.push(entry)
            _canUndo.value = undoStack.canUndo()
            _canRedo.value = undoStack.canRedo()
        }
    }

    private fun syncSelectionFromUI(sel: TextRange) {
        val totalLines = lineIndexer.lineCount
        val docLen = state.text.length
        val safeStart = sel.min.coerceIn(0, docLen)
        val safeEnd = sel.max.coerceIn(0, docLen)
        val lineIdx = if (totalLines > 0) lineIndexer.lineIndexForOffset(safeEnd) else 0
        val lineStart = if (totalLines > 0) lineIndexer.lineStart(lineIdx) else 0
        _activeLineIndex.intValue = lineIdx
        _activeLineSelection.value = TextRange(
            (safeStart - lineStart).coerceAtLeast(0),
            (safeEnd - lineStart).coerceAtLeast(0)
        )
    }

    private fun runProseAnalysisAsync(text: String) {
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch(Dispatchers.Default) {
            val result = ProseAnalysisEngine.analyze(text)
            withContext(Dispatchers.Main) {
                _proseAnalysis.value = result
            }
        }
    }

    fun notifyMutation() {
        lineIndexer.index(state.text)
        _lineCount.intValue = lineIndexer.lineCount
        _canUndo.value = undoStack.canUndo()
        _canRedo.value = undoStack.canRedo()
        _documentRevision.intValue++
        mutationEvents.tryEmit(Unit)
    }

    /**
     * Mirrors the active paragraph's local selection into engine state.
     */
    fun updateLineSelection(lineIndex: Int, localStart: Int, localEnd: Int) {
        val totalLines = lineIndexer.lineCount
        val validLine = lineIndex.coerceIn(0, (totalLines - 1).coerceAtLeast(0))
        val lineStart = lineIndexer.lineStart(validLine)
        val lineLen = lineIndexer.lineLength(validLine)
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
        val totalLines = lineIndexer.lineCount
        val validLine = lineIndex.coerceIn(0, (totalLines - 1).coerceAtLeast(0))
        val lineStart = lineIndexer.lineStart(validLine)
        val lineEnd = lineStart + lineIndexer.lineLength(validLine)

        state.edit {
            replace(lineStart, lineEnd, newText)
            selection = TextRange((lineStart + cursorPos).coerceIn(0, length))
        }
        notifyMutation()
    }

    fun onLineInserted(afterLineIndex: Int, splitAt: Int, currentLineText: String) {
        val totalLines = lineIndexer.lineCount
        val validLine = afterLineIndex.coerceIn(0, (totalLines - 1).coerceAtLeast(0))
        val lineStart = lineIndexer.lineStart(validLine)
        val splitOffset = lineStart + splitAt.coerceIn(0, currentLineText.length)

        state.edit {
            insert(splitOffset, "\n")
            selection = TextRange(splitOffset + 1)
        }
        notifyMutation()
    }

    fun onLineMerge(lineIndex: Int) {
        if (lineIndex <= 0) return
        val currentLineStart = lineIndexer.lineStart(lineIndex)
        if (currentLineStart > 0) {
            state.edit {
                delete(currentLineStart - 1, currentLineStart)
                selection = TextRange(currentLineStart - 1)
            }
            notifyMutation()
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
        notifyMutation()
    }

    fun insertAtCursor(lineIndex: Int, cursorPos: Int, text: String) {
        if (text.isEmpty()) return
        val totalLines = lineIndexer.lineCount
        val validLine = lineIndex.coerceIn(0, (totalLines - 1).coerceAtLeast(0))
        val lineStart = lineIndexer.lineStart(validLine)
        val lineLen = lineIndexer.lineLength(validLine)
        val safeCursor = cursorPos.coerceIn(0, lineLen)
        val insertPos = lineStart + safeCursor

        state.edit {
            insert(insertPos, text)
            selection = TextRange(insertPos + text.length)
        }
        notifyMutation()
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
        notifyMutation()
    }

    fun applyFormatWrap(lineIndex: Int, selStart: Int, selEnd: Int, open: String, close: String) {
        val totalLines = lineIndexer.lineCount
        val validLine = lineIndex.coerceIn(0, (totalLines - 1).coerceAtLeast(0))
        val lineStart = lineIndexer.lineStart(validLine)
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
        notifyMutation()
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
            notifyMutation()
        }
    }

    fun toggleFormat(type: FormatType) {
        val sel = state.selection
        val s: Int
        val e: Int
        if (sel.collapsed) {
            val lineIdx = lineIndexer.lineIndexForOffset(sel.start)
            s = lineIndexer.lineStart(lineIdx)
            e = s + lineIndexer.lineLength(lineIdx)
        } else {
            s = sel.min
            e = sel.max
        }
        if (s >= e) return
        applyOrToggleFormat(type, s, e)
        notifyMutation()
    }

    fun toggleFormat(type: FormatType, selStart: Int, selEnd: Int) {
        val s = minOf(selStart, selEnd).coerceIn(0, state.text.length)
        val e = maxOf(selStart, selEnd).coerceIn(0, state.text.length)
        if (s >= e) return
        applyOrToggleFormat(type, s, e)
        notifyMutation()
    }

    fun toggleFormatOnLine(lineIndex: Int, type: FormatType) {
        val totalLines = lineIndexer.lineCount
        val validLine = lineIndex.coerceIn(0, (totalLines - 1).coerceAtLeast(0))
        val s = lineIndexer.lineStart(validLine)
        val e = s + lineIndexer.lineLength(validLine)
        if (s >= e) return
        applyOrToggleFormat(type, s, e)
        notifyMutation()
    }

    private fun applyOrToggleFormat(type: FormatType, start: Int, end: Int) {
        state.edit {
            val styles = getSpanStyles()
            val matchingRanges = styles.filter { range ->
                val rangeType = spanStyleToFormatType(range.item)
                rangeType == type && range.start <= start && range.end >= end
            }

            if (matchingRanges.isNotEmpty()) {
                matchingRanges.forEach { it.clearStyle() }
            } else {
                if (type.isHeading()) {
                    styles.filter { range ->
                        val rangeType = spanStyleToFormatType(range.item)
                        rangeType?.isHeading() == true && range.start < end && range.end > start
                    }.forEach { it.clearStyle() }
                }
                addStyle(type.toSpanStyle(), start, end)
            }
        }
    }

    fun requestLineFocus(lineIndex: Int, column: Int = -1) {
        val totalLines = lineIndexer.lineCount
        val validLine = lineIndex.coerceIn(0, (totalLines - 1).coerceAtLeast(0))
        val lineStart = lineIndexer.lineStart(validLine)
        val lineLen = lineIndexer.lineLength(validLine)
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
        val lineIdx = if (lineIndexer.lineCount > 0) lineIndexer.lineIndexForOffset(safeOffset) else 0
        val lineStart = if (lineIndexer.lineCount > 0) lineIndexer.lineStart(lineIdx) else 0
        val col = (safeOffset - lineStart).coerceAtLeast(0)

        state.edit {
            selection = TextRange(safeOffset)
        }
        _activeLineIndex.intValue = lineIdx
        _activeLineSelection.value = TextRange(col)
        _focusRequests.tryEmit(LineFocusRequest(lineIdx, col, targetOffset = safeOffset))
    }

    // ── History ───────────────────────────────────────────────────────────

    private fun applyEditToState(edit: Edit) {
        state.edit {
            when (edit) {
                is Edit.Insert -> replace(edit.pos, edit.pos, edit.text)
                is Edit.Delete -> delete(edit.start, edit.end)
                is Edit.Compound -> {
                    for (subEdit in edit.edits) {
                        when (subEdit) {
                            is Edit.Insert -> replace(subEdit.pos, subEdit.pos, subEdit.text)
                            is Edit.Delete -> delete(subEdit.start, subEdit.end)
                            is Edit.Compound -> applyEditToState(subEdit)
                        }
                    }
                }
            }
        }
    }

    private fun applyInverseEditToState(edit: Edit) {
        state.edit {
            when (edit) {
                is Edit.Insert -> delete(edit.pos, edit.pos + edit.length)
                is Edit.Delete -> replace(edit.start, edit.start, edit.text)
                is Edit.Compound -> {
                    for (subEdit in edit.edits.reversed()) {
                        when (subEdit) {
                            is Edit.Insert -> delete(subEdit.pos, subEdit.pos + subEdit.length)
                            is Edit.Delete -> replace(subEdit.start, subEdit.start, subEdit.text)
                            is Edit.Compound -> applyInverseEditToState(subEdit)
                        }
                    }
                }
            }
        }
    }

    fun undo() {
        val entry = undoStack.undo() ?: return
        applyInverseEditToState(entry.bufferEdit)
        val targetPos = (lineIndexer.lineStart(entry.cursorBefore.line) + entry.cursorBefore.column)
            .coerceIn(0, state.text.length)
        state.edit { selection = TextRange(targetPos) }
        notifyMutation()
    }

    fun redo() {
        val entry = undoStack.redo() ?: return
        applyEditToState(entry.bufferEdit)
        val targetPos = (lineIndexer.lineStart(entry.cursorAfter.line) + entry.cursorAfter.column)
            .coerceIn(0, state.text.length)
        state.edit { selection = TextRange(targetPos) }
        notifyMutation()
    }

    fun saveSnapshot(label: String) {
        undoStack.saveCheckpoint(label)
        notifyMutation()
    }

    // ── Serialization & Document Loading ─────────────────────────────────

    fun exportPlainText(): String = state.text.toString()

    fun exportWithFormats(): SerializedDocument {
        val text = state.text.toString()
        val styles = state.textStyles
        val spans = styles.mapNotNull { trackedRange ->
            val spanType = spanStyleToFormatType(trackedRange.item)
            if (spanType != null) {
                SerializedSpan(spanType.name, trackedRange.start, trackedRange.end)
            } else {
                null
            }
        }
        return SerializedDocument(
            version = 2,
            plainText = text,
            spans = spans
        )
    }

    fun loadDocument(doc: SerializedDocument) {
        val plainText = doc.plainText

        buffer.delete(0, buffer.length())
        if (plainText.isNotEmpty()) {
            buffer.insert(0, plainText)
        }

        state.edit {
            replace(0, length, plainText)
            selection = TextRange(0)
            doc.spans.forEach { span ->
                try {
                    val type = FormatType.valueOf(span.type)
                    val s = span.start.coerceIn(0, plainText.length)
                    val e = span.end.coerceIn(0, plainText.length)
                    if (s < e) {
                        addStyle(type.toSpanStyle(), s, e)
                    }
                } catch (_: Exception) {}
            }
        }

        undoStack.clear()
        searchEngine.clear()
        lastRecordedText = plainText
        lineIndexer.index(plainText)
        _lineCount.intValue = lineIndexer.lineCount

        _activeLineIndex.intValue = 0
        _activeLineSelection.value = TextRange(0, 0)

        notifyMutation()
        _wordCount.value = countWords(plainText)
        _charCount.value = plainText.length
        extractOutlineAsync(plainText)
    }

    // ── Internal Helpers ─────────────────────────────────────────────────

    private fun extractOutlineAsync(text: String) {
        outlineJob?.cancel()
        outlineJob = viewModelScope.launch(Dispatchers.Default) {
            val totalLines = lineIndexer.lineCount
            val entries = mutableListOf<OutlineEntry>()

            for (i in 0 until totalLines) {
                val content = lineIndexer.getLineContent(text, i).trim()
                if (content.isEmpty()) continue

                val lineStart = lineIndexer.lineStart(i)
                val lineEnd = lineStart + content.length
                val headingStyle = state.textStyles.firstOrNull {
                    it.end > lineStart && it.start < lineEnd && spanStyleToFormatType(it.item)?.isHeading() == true
                }

                if (headingStyle != null) {
                    val formatType = spanStyleToFormatType(headingStyle.item)
                    if (formatType != null && formatType.isHeading()) {
                        entries.add(
                            OutlineEntry(
                                level = formatType.headingLevel(),
                                text = content,
                                lineIndex = i
                            )
                        )
                    }
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
