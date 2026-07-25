package com.primaloptima.scribe.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.graphics.toColorInt

/**
 * Custom EditText that provides all of Scribe's smart-editing features:
 *
 *  • Auto-pair:         typing ( inserts () and places cursor between them
 *  • Skip-over:         typing ) when cursor is already before ) just moves forward
 *  • Smart Enter:       Enter before a close char moves cursor past it instead of newline
 *  • Paired Backspace:  Backspace between an empty pair deletes both chars
 *  • Local Undo/Redo:   200-entry stack, 700ms grouping window
 *  • Goal bar:          thin progress bar painted at the very top of the view
 *  • Word-count badge:  delegated to the hosting Activity via a listener
 *  • Typewriter scroll: listener callback so the Activity can react
 *
 * All smart-keyboard interception happens in [ScribeInputConnection], which wraps
 * the default InputConnection. This is the only reliable way to intercept soft-
 * keyboard characters on Android without fighting IME auto-correct / composing text.
 */
class ScribeEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    // ── Smart pair maps ───────────────────────────────────────────────────────

    val pairMap: Map<Char, Char> = mapOf(
        '(' to ')', '[' to ']', '{' to '}', '`' to '`',
        '"' to '"', '\'' to '\'',
        '\u201C' to '\u201D',   // " → "
        '\u2018' to '\u2019',   // ' → '
        '\u00AB' to '\u00BB'    // « → »
    )
    val closeChars: Set<Char> = pairMap.values.toSet()
    val openChars: Set<Char> = pairMap.keys.toSet()

    // ── Undo / Redo stack ─────────────────────────────────────────────────────

    private data class EditState(val text: String, val cursor: Int)

    private val undoStack = ArrayDeque<EditState>()
    private val redoStack = ArrayDeque<EditState>()
    private var lastHistoryMs = 0L
    private val historyGroupMs = 700L
    private val historyLimit = 200
    var isApplyingEdit = false
        private set

    // Track state just before an edit happens
    private var pendingUndoPush: EditState? = null

    // ── Listeners ─────────────────────────────────────────────────────────────

    /** Fired after each text change with the new content string. */
    var onTextChangedListener: ((String) -> Unit)? = null

    /** Fired whenever the undo/redo stack state changes. */
    var onUndoRedoChanged: ((canUndo: Boolean, canRedo: Boolean) -> Unit)? = null

    /** Fired after text changes so the Activity can update typewriter scroll. */
    var onCursorMovedListener: (() -> Unit)? = null

    // ── Goal bar painting ─────────────────────────────────────────────────────

    private val goalBarPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val goalBarBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val goalBarRect = RectF()

    /** 0f – 1f progress; set via [setGoalProgress]. */
    private var goalProgress = 0f
    private var goalReached = false
    private var goalBarHeight = 3f  // dp, scaled below
    private val goalBarHeightNormal = 3f
    private val goalBarHeightCelebrate = 6f

    /** Accent colour for goal bar (set from theme). */
    private var goalBarColor: Int = "#a8651e".toColorInt()
    private var goalBarBgColor: Int = 0x22000000

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        val density = resources.displayMetrics.density
        goalBarHeight = goalBarHeightNormal * density
    }

    // ── InputConnection override ──────────────────────────────────────────────

    override fun onCreateInputConnection(editorInfo: EditorInfo): InputConnection {
        val base = super.onCreateInputConnection(editorInfo) ?: return super.onCreateInputConnection(editorInfo)!!
        return ScribeInputConnection(base, this)
    }

    // ── Text change tracking (for undo/redo) ──────────────────────────────────

    /** Called by [ScribeInputConnection] right before a destructive edit. */
    fun captureUndoState() {
        if (isApplyingEdit) return
        val t = text?.toString() ?: ""
        val c = selectionStart.coerceAtLeast(0)
        pendingUndoPush = EditState(t, c)
    }

    /** Called by [ScribeInputConnection] right after a successful edit. */
    fun commitUndoState() {
        if (isApplyingEdit) return
        val before = pendingUndoPush ?: return
        pendingUndoPush = null
        val now = System.currentTimeMillis()
        if (now - lastHistoryMs > historyGroupMs || undoStack.isEmpty()) {
            undoStack.addLast(before)
            if (undoStack.size > historyLimit) undoStack.removeFirst()
        }
        redoStack.clear()
        lastHistoryMs = now
        notifyUndoRedo()

        val current = text?.toString() ?: ""
        onTextChangedListener?.invoke(current)
    }

    fun canUndo() = undoStack.isNotEmpty()
    fun canRedo() = redoStack.isNotEmpty()

    fun undo() {
        if (undoStack.isEmpty()) return
        val prev = undoStack.removeLast()
        val currentText = text?.toString() ?: ""
        val currentCursor = selectionStart.coerceAtLeast(0)
        redoStack.addLast(EditState(currentText, currentCursor))
        applyState(prev)
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val next = redoStack.removeLast()
        val currentText = text?.toString() ?: ""
        val currentCursor = selectionStart.coerceAtLeast(0)
        undoStack.addLast(EditState(currentText, currentCursor))
        applyState(next)
    }

    private fun applyState(state: EditState) {
        isApplyingEdit = true
        try {
            setText(state.text)
            val safePos = state.cursor.coerceIn(0, state.text.length)
            setSelection(safePos)
        } finally {
            isApplyingEdit = false
        }
        notifyUndoRedo()
        onTextChangedListener?.invoke(state.text)
        post { onCursorMovedListener?.invoke() }
    }

    private fun notifyUndoRedo() {
        onUndoRedoChanged?.invoke(undoStack.isNotEmpty(), redoStack.isNotEmpty())
    }

    /** Completely replace the editor content without pushing to the undo stack. */
    fun setContentSilently(content: String) {
        isApplyingEdit = true
        try {
            setText(content)
            setSelection(0)
        } finally {
            isApplyingEdit = false
        }
        undoStack.clear()
        redoStack.clear()
        notifyUndoRedo()
    }

    fun clearHistory() {
        undoStack.clear()
        redoStack.clear()
        notifyUndoRedo()
    }

    // ── Apply shortcut ────────────────────────────────────────────────────────

    /**
     * Apply a ShortcutAction to the current selection/cursor position.
     * kind = "insert" | "wrap" | "pair"
     */
    fun applyShortcut(kind: String, payload: String, closing: String?) {
        captureUndoState()
        val t = text ?: return
        val start = selectionStart
        val end = selectionEnd

        when (kind) {
            "insert" -> {
                t.replace(start, end, payload)
                setSelection((start + payload.length).coerceAtMost(t.length))
            }
            "wrap", "pair" -> {
                val close = closing ?: payload
                if (start == end) {
                    // No selection: insert open+close, place cursor between
                    val ins = payload + close
                    t.insert(start, ins)
                    setSelection(start + payload.length)
                } else {
                    // Wrap selection
                    val selected = t.subSequence(start, end).toString()
                    val wrapped = payload + selected + close
                    t.replace(start, end, wrapped)
                    setSelection(start + wrapped.length)
                }
            }
        }
        commitUndoState()
        post { onCursorMovedListener?.invoke() }
    }

    // ── Find & replace ────────────────────────────────────────────────────────

    private var findQuery = ""
    private var findRegex = false
    private var findCaseSensitive = false
    private var findMatches = listOf<IntRange>()
    private var findIndex = -1

    fun startFind(query: String, regex: Boolean, caseSensitive: Boolean) {
        findQuery = query
        findRegex = regex
        findCaseSensitive = caseSensitive
        findMatches = computeMatches(query, regex, caseSensitive)
        findIndex = if (findMatches.isEmpty()) -1 else 0
        highlightCurrentMatch()
    }

    fun findNext() {
        if (findMatches.isEmpty()) return
        findIndex = (findIndex + 1) % findMatches.size
        highlightCurrentMatch()
    }

    fun findPrev() {
        if (findMatches.isEmpty()) return
        findIndex = if (findIndex <= 0) findMatches.size - 1 else findIndex - 1
        highlightCurrentMatch()
    }

    fun replaceCurrentMatch(replacement: String) {
        if (findIndex < 0 || findIndex >= findMatches.size) return
        val range = findMatches[findIndex]
        captureUndoState()
        text?.replace(range.first, range.last + 1, replacement)
        commitUndoState()
        // Recompute matches after replacement
        startFind(findQuery, findRegex, findCaseSensitive)
    }

    fun replaceAll(replacement: String): Int {
        if (findMatches.isEmpty()) return 0
        val t = text?.toString() ?: return 0
        captureUndoState()
        // Replace from end to start so offsets stay valid
        val sorted = findMatches.sortedByDescending { it.first }
        var result = t
        for (range in sorted) {
            result = result.substring(0, range.first) + replacement + result.substring(range.last + 1)
        }
        setContentSilently(result)
        commitUndoState()
        val count = findMatches.size
        startFind(findQuery, findRegex, findCaseSensitive)
        return count
    }

    fun clearFind() {
        findQuery = ""; findMatches = emptyList(); findIndex = -1
    }

    fun matchCount() = findMatches.size
    fun currentMatchIndex() = findIndex

    private fun computeMatches(query: String, regex: Boolean, caseSensitive: Boolean): List<IntRange> {
        if (query.isEmpty()) return emptyList()
        val content = text?.toString() ?: return emptyList()
        return try {
            val flags = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
            val re = if (regex) Regex(query, flags) else Regex(Regex.escape(query), flags)
            re.findAll(content).map { it.range }.toList()
        } catch (_: Exception) { emptyList() }
    }

    private fun highlightCurrentMatch() {
        if (findIndex < 0 || findIndex >= findMatches.size) return
        val range = findMatches[findIndex]
        setSelection(range.first, range.last + 1)
        post { bringPointIntoView(range.first) }
    }

    // ── Line navigation (outline) ─────────────────────────────────────────────

    fun jumpToLine(lineIndex: Int) {
        val t = text?.toString() ?: return
        val lines = t.split('\n')
        if (lineIndex < 0 || lineIndex >= lines.size) return
        val offset = lines.take(lineIndex).sumOf { it.length + 1 }
        setSelection(offset.coerceAtMost(t.length))
        post {
            bringPointIntoView(offset)
            onCursorMovedListener?.invoke()
        }
    }

    // ── Goal bar ──────────────────────────────────────────────────────────────

    fun setGoalProgress(progress: Float, reached: Boolean, accentHex: String) {
        goalProgress = progress.coerceIn(0f, 1f)
        goalReached = reached
        goalBarColor = try { accentHex.toColorInt() } catch (_: Exception) { goalBarColor }
        val density = resources.displayMetrics.density
        goalBarHeight = (if (reached) goalBarHeightCelebrate else goalBarHeightNormal) * density
        goalBarPaint.color = if (reached) 0xFF4CAF50.toInt() else goalBarColor
        goalBarBgPaint.color = goalBarBgColor
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Draw goal bar at the very top of the view (above all text)
        if (goalProgress > 0f) {
            val w = width.toFloat()
            goalBarRect.set(0f, scrollY.toFloat(), w, scrollY + goalBarHeight)
            canvas.drawRect(goalBarRect, goalBarBgPaint)
            goalBarRect.set(0f, scrollY.toFloat(), w * goalProgress, scrollY + goalBarHeight)
            canvas.drawRect(goalBarRect, goalBarPaint)
        }
    }

    // ── ScribeInputConnection ─────────────────────────────────────────────────

    /**
     * Wraps the platform InputConnection to intercept character insertions and
     * deletions before the IME processes them, enabling all smart-editing features.
     *
     * We use InputConnectionWrapper rather than overriding onKeyDown/onKeyUp
     * because soft keyboards do NOT reliably fire key events — they go through
     * commitText / deleteSurroundingText exclusively.
     */
    inner class ScribeInputConnection(
        base: InputConnection,
        private val editor: ScribeEditText
    ) : InputConnectionWrapper(base, true) {

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            if (text == null || editor.isApplyingEdit) {
                return super.commitText(text, newCursorPosition)
            }

            val editable = editor.text ?: return super.commitText(text, newCursorPosition)
            val cursorStart = editor.selectionStart
            val cursorEnd   = editor.selectionEnd

            // Single-character fast path — where all smart-edit logic lives
            if (text.length == 1) {
                val ch = text[0]

                // ── Smart Enter ───────────────────────────────────────────────
                if (ch == '\n') {
                    // If cursor is before a closing pair char (no selection), skip over it
                    if (cursorStart == cursorEnd) {
                        val charAfter = editable.getOrNull(cursorStart)
                        if (charAfter != null && charAfter in editor.closeChars) {
                            editor.captureUndoState()
                            editor.setSelection(cursorStart + 1)
                            editor.commitUndoState()
                            return true
                        }
                    }
                    return super.commitText(text, newCursorPosition)
                }

                // ── Skip-over ─────────────────────────────────────────────────
                if (ch in editor.closeChars) {
                    if (cursorStart == cursorEnd) {
                        val charAfter = editable.getOrNull(cursorStart)
                        if (charAfter == ch) {
                            editor.captureUndoState()
                            editor.setSelection(cursorStart + 1)
                            editor.commitUndoState()
                            return true
                        }
                    }
                }

                // ── Auto-pair ─────────────────────────────────────────────────
                if (ch in editor.openChars) {
                    val closeChar = editor.pairMap[ch]!!
                    // Don't auto-pair if we already have the same close char after cursor
                    // (except for chars that are their own close, like `)
                    val charAfter = editable.getOrNull(cursorStart)
                    val shouldPair = when {
                        cursorStart != cursorEnd -> true  // wrap selection
                        charAfter == null || charAfter == '\n' || charAfter == ' ' -> true
                        charAfter == closeChar && ch != closeChar -> false  // already paired
                        else -> true
                    }
                    if (shouldPair) {
                        editor.captureUndoState()
                        if (cursorStart != cursorEnd) {
                            // Wrap existing selection
                            val selected = editable.subSequence(cursorStart, cursorEnd).toString()
                            editable.replace(cursorStart, cursorEnd, "$ch$selected$closeChar")
                            editor.setSelection(cursorStart + 1 + selected.length + 1)
                        } else {
                            // Insert pair, position cursor between
                            val pair = "$ch$closeChar"
                            val result = super.commitText(pair, newCursorPosition)
                            if (result) {
                                // Move cursor back before the close char
                                val pos = editor.selectionStart
                                editor.setSelection(pos - 1)
                            }
                            editor.commitUndoState()
                            return result
                        }
                        editor.commitUndoState()
                        return true
                    }
                }
            }

            // Default path
            editor.captureUndoState()
            val result = super.commitText(text, newCursorPosition)
            if (result) editor.commitUndoState()
            return result
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            if (editor.isApplyingEdit) return super.deleteSurroundingText(beforeLength, afterLength)

            // ── Paired Backspace ──────────────────────────────────────────────
            if (beforeLength == 1 && afterLength == 0) {
                val editable = editor.text ?: return super.deleteSurroundingText(beforeLength, afterLength)
                val cursor = editor.selectionStart
                if (editor.selectionStart == editor.selectionEnd && cursor > 0) {
                    val charBefore = editable.getOrNull(cursor - 1)
                    val charAfter  = editable.getOrNull(cursor)
                    if (charBefore != null && charAfter != null
                            && editor.pairMap[charBefore] == charAfter) {
                        editor.captureUndoState()
                        editable.delete(cursor - 1, cursor + 1)
                        editor.commitUndoState()
                        return true
                    }
                }
            }

            editor.captureUndoState()
            val result = super.deleteSurroundingText(beforeLength, afterLength)
            if (result) editor.commitUndoState()
            return result
        }
    }
}
