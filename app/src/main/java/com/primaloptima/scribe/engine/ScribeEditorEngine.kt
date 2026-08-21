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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

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
    val formats = FormatRegistry()
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

        // 1. Synchronous main-thread line indexer and selection sync (Zero String Allocation)
        snapshotFlow { state.text }
            .onEach { textSnapshot ->
                lineIndexer.index(textSnapshot)
                _lineCount.intValue = lineIndexer.lineCount
                _documentRevision.intValue++
                syncSelectionFromUI(state.selection)
            }
            .launchIn(viewModelScope)

        snapshotFlow { state.selection }
            .onEach { sel ->
                syncSelectionFromUI(sel)
            }
            .launchIn(viewModelScope)

        // 2. Debounced text diffing and undo recording on worker threads
        snapshotFlow { state.text }
            .drop(1)
            .debounce(200)
            .onEach { textSnapshot ->
                val newText = textSnapshot.toString()
                val oldText = lastRecordedText
                if (oldText != newText) {
                    recordTextDiff(oldText, newText)
                    lastRecordedText = newText
                    notifyMutation()
                }
            }
            .flowOn(Dispatchers.Default)
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
        snapshotFlow { state.text }
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

        if (deleteLen > 0) {
            formats.adjustForDelete(prefix, deleteLen)
        }
        if (insertText.isNotEmpty()) {
            formats.adjustForInsert(prefix, insertText.length)
        }

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

        val safeLocalStart = localStart.coerceIn(0, lineLen)
        val safeLocalEnd = localEnd.coerceIn(0, lineLen)

        _activeLineIndex.intValue = validLine
        _activeLineSelection.value = TextRange(safeLocalStart, safeLocalEnd)

        val docStart = lineStart + safeLocalStart
        val docEnd = lineStart + safeLocalEnd
        if (state.selection.start != docStart || state.selection.end != docEnd) {
            state.edit {
                selection = TextRange(docStart, docEnd)
            }
        }
    }

    // ── Formatting Commands ───────────────────────────────────────────────
    fun insertAtCursor(text: String) {
        if (text.isEmpty()) return
        val sel = state.selection
        val s = sel.min.coerceIn(0, state.text.length)
        val e = sel.max.coerceIn(0, state.text.length)
        state.edit {
            replace(s, e, text)
            selection = TextRange(s + text.length)
        }
        notifyMutation()
    }

    fun applyFormatWrap(open: String, close: String) {
        val sel = state.selection
        val s = sel.min.coerceIn(0, state.text.length)
        val e = sel.max.coerceIn(0, state.text.length)
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


    fun toggleFormat(type: FormatType) {
        val sel = state.selection
        val s = sel.min.coerceIn(0, state.text.length)
        val e = sel.max.coerceIn(0, state.text.length)
        if (s >= e && type != FormatType.SCENE_SEPARATOR) return

        val formatEdit = formats.toggleSpan(type, s, e)
        val activeLine = _activeLineIndex.intValue
        val cursorPos = CursorPos(activeLine, (s - lineIndexer.lineStart(activeLine)).coerceAtLeast(0))
        undoStack.push(UndoEntry(formatEdit = formatEdit, cursorBefore = cursorPos, cursorAfter = cursorPos))
        notifyMutation()
    }

    fun applyHeading(type: FormatType) {
        if (!type.isHeading()) return
        val activeLine = _activeLineIndex.intValue
        val lineStart = lineIndexer.lineStart(activeLine)
        val lineEnd = lineIndexer.lineEnd(activeLine)
        val formatEdit = formats.addSpan(FormatSpan(type, lineStart, lineEnd))
        val cursorPos = CursorPos(activeLine, 0)
        undoStack.push(UndoEntry(formatEdit = formatEdit, cursorBefore = cursorPos, cursorAfter = cursorPos))
        notifyMutation()
    }

    fun removeHeading() {
        val activeLine = _activeLineIndex.intValue
        val lineStart = lineIndexer.lineStart(activeLine)
        val lineEnd = lineIndexer.lineEnd(activeLine)
        val edits = mutableListOf<FormatEdit>()
        for (hType in listOf(FormatType.H1, FormatType.H2, FormatType.H3)) {
            edits.add(formats.removeSpan(hType, lineStart, lineEnd))
        }
        val cursorPos = CursorPos(activeLine, 0)
        undoStack.push(UndoEntry(formatEdit = FormatEdit.Compound(edits), cursorBefore = cursorPos, cursorAfter = cursorPos))
        notifyMutation()
    }

    fun insertSceneSeparator() {
        val sel = state.selection
        val pos = sel.max.coerceIn(0, state.text.length)
        val separatorText = "\n* * *\n"
        state.edit {
            insert(pos, separatorText)
        }
        formats.addSpan(FormatSpan(FormatType.SCENE_SEPARATOR, pos + 1, pos + 6))
        notifyMutation()
    }

    fun formatActiveLine(type: FormatType) {
        val totalLines = lineIndexer.lineCount
        val validLine = _activeLineIndex.intValue.coerceIn(0, (totalLines - 1).coerceAtLeast(0))
        val lineStart = lineIndexer.lineStart(validLine)
        val lineEnd = lineIndexer.lineEnd(validLine)
        if (lineStart >= lineEnd && type != FormatType.SCENE_SEPARATOR) return

        val formatEdit = formats.toggleSpan(type, lineStart, lineEnd)
        val cursorPos = CursorPos(validLine, 0)
        undoStack.push(UndoEntry(formatEdit = formatEdit, cursorBefore = cursorPos, cursorAfter = cursorPos))
        notifyMutation()
    }

    fun formatSelection(type: FormatType) {
        val sel = state.selection
        val s = sel.min.coerceIn(0, state.text.length)
        val e = sel.max.coerceIn(0, state.text.length)
        val activeLine = _activeLineIndex.intValue
        val validLine = activeLine.coerceIn(0, (lineIndexer.lineCount - 1).coerceAtLeast(0))
        if (s >= e && type != FormatType.SCENE_SEPARATOR) return

        val formatEdit = formats.toggleSpan(type, s, e)
        val cursorPos = CursorPos(validLine, 0)
        undoStack.push(UndoEntry(formatEdit = formatEdit, cursorBefore = cursorPos, cursorAfter = cursorPos))
        notifyMutation()
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
        entry.bufferEdit?.let { applyInverseEditToState(it) }
        entry.formatEdit?.let { formats.invertFormatEdit(it) }
        val targetPos = (lineIndexer.lineStart(entry.cursorBefore.line) + entry.cursorBefore.column)
            .coerceIn(0, state.text.length)
        state.edit { selection = TextRange(targetPos) }
        notifyMutation()
    }

    fun redo() {
        val entry = undoStack.redo() ?: return
        entry.bufferEdit?.let { applyEditToState(it) }
        entry.formatEdit?.let { formats.applyFormatEdit(it) }
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
        return SerializedDocument(
            version = 2,
            plainText = state.text.toString(),
            spans = formats.exportAll().map { it.toSerializedSpan() }
        )
    }

    fun loadDocument(doc: SerializedDocument) {
        val plainText = doc.plainText
        buffer.delete(0, buffer.length)
        if (plainText.isNotEmpty()) {
            buffer.insert(0, plainText)
        }
        state.edit {
            replace(0, length, plainText)
            selection = TextRange(0)
        }
        val loadedSpans = doc.spans.mapNotNull { span ->
            try {
                FormatSpan(FormatType.valueOf(span.type), span.start, span.end)
            } catch (_: Exception) {
                null
            }
        }
        formats.loadAll(loadedSpans)
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
                coroutineContext.ensureActive()
                val content = lineIndexer.getLineContent(text, i).trim()
                if (content.isEmpty()) continue
                val lineStart = lineIndexer.lineStart(i)
                val lineEnd = lineStart + content.length
                val headingSpan = formats.spansIn(lineStart, lineEnd).firstOrNull { it.type.isHeading() }
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
