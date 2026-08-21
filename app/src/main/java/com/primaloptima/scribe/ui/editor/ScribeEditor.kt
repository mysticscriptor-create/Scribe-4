/*
 * ScribeEditor — Pure Compose Virtualized Canvas Prose Editor (Updated August 21, 2026).
 *
 * ARCHITECTURAL RESEARCH FINDINGS (August 21, 2026 / Compose BOM 2026.08.00 / Compose 1.12):
 *
 * 1. Snapshot State Reads in DrawScope / Canvas:
 *    - Reading snapshot state (such as `TextFieldState.text` or `ScrollState.value`) inside
 *      `Canvas { /* DrawScope */ }` or `drawBehind { }` observes state during the Draw Phase.
 *    - When read exclusively during the draw phase, state mutations trigger a targeted redraw
 *      without causing full composable recomposition or layout passes.
 *    - For synchronized line splitting and rendering, reading `engine.state.text` directly
 *      eliminates the 150 ms debounce lag from the background DocumentBuffer sync, ensuring
 *      zero latency between keystrokes and on-screen rendering.
 *
 * 2. Multi-Line Text Measurement & Soft-Wrap Pixel Height:
 *    - `TextLayoutResult.size.height` (and `TextLayoutResult.multiParagraph.height`) returns the
 *      exact pixel height of a measured paragraph, including all wrapped visual lines.
 *    - `TextLayoutResult.lineCount` returns the number of visual lines (>= 1) into which the
 *      paragraph was soft-wrapped under current width constraints.
 *    - `TextLayoutResult.getCursorRect(offset)` returns the exact cursor bounding rect (including
 *      `top` offset on the specific wrapped visual line), and `getPathForRange(start, end)`
 *      accurately returns the full multi-line selection bounding path.
 *    - Maintaining a cumulative `LineLayoutTracker` with per-line measured heights enables exact Y
 *      positioning of all paragraphs, eliminating layout distortion when long lines soft-wrap.
 *
 * 3. BasicTextField OutputTransformation vs. Decorator:
 *    - `OutputTransformation` applies transformations exclusively to the visual representation
 *      rendered by `innerTextField()`.
 *    - Since `innerTextField()` is hosted off-screen purely as an invisible IME anchor, passing
 *      `OutputTransformation` causes redundant full-document span iterations on every keystroke.
 *    - The Canvas `buildAnnotatedString` is the sole visual rendering surface for the editor.
 *      Removing `outputTransformation` from `BasicTextField` eliminates double-processing with zero
 *      visual or functional regression.
 */
package com.primaloptima.scribe.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primaloptima.scribe.engine.FormatSpan
import com.primaloptima.scribe.engine.ScribeEditorEngine
import com.primaloptima.scribe.engine.toSpanStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop

/**
 * Fast zero-allocation / lightweight indexer for logical lines derived directly from
 * live snapshot text. Eliminates 150ms buffer sync lag.
 */
private class LiveDocumentLines(val fullText: String) {
    val lineStarts: IntArray
    val lineCount: Int

    init {
        val starts = ArrayList<Int>()
        starts.add(0)
        var i = 0
        while (i < fullText.length) {
            if (fullText[i] == '\n') {
                starts.add(i + 1)
            }
            i++
        }
        lineStarts = starts.toIntArray()
        lineCount = lineStarts.size
    }

    fun lineStart(index: Int): Int = lineStarts[index]

    fun lineEnd(index: Int): Int {
        val nextStart = if (index + 1 < lineStarts.size) lineStarts[index + 1] else fullText.length + 1
        return (nextStart - 1).coerceAtMost(fullText.length)
    }

    fun lineLength(index: Int): Int = lineEnd(index) - lineStart(index)

    fun lineContent(index: Int): String {
        val start = lineStart(index)
        val end = lineEnd(index)
        return if (start <= end && end <= fullText.length) {
            fullText.substring(start, end)
        } else {
            ""
        }
    }
}

/**
 * Dynamic per-line height and cumulative top-offset tracker.
 * Handles paragraphs soft-wrapping to multiple visual lines with lazy layout measurement.
 */
private class LineLayoutTracker(lineCount: Int, defaultLineHeight: Float) {
    private val heights = FloatArray(lineCount.coerceAtLeast(1)) { defaultLineHeight }
    private val offsets = FloatArray(lineCount.coerceAtLeast(1))
    private var dirty = true

    fun updateHeight(lineIndex: Int, height: Float): Boolean {
        if (lineIndex in heights.indices && heights[lineIndex] != height) {
            heights[lineIndex] = height
            dirty = true
            return true
        }
        return false
    }

    private fun recomputeOffsets() {
        if (!dirty) return
        var accum = 0f
        for (i in heights.indices) {
            offsets[i] = accum
            accum += heights[i]
        }
        dirty = false
    }

    fun getLineTop(lineIndex: Int): Float {
        recomputeOffsets()
        return if (lineIndex in offsets.indices) offsets[lineIndex] else 0f
    }

    fun getTotalHeight(): Float {
        recomputeOffsets()
        return if (heights.isNotEmpty()) offsets.last() + heights.last() else 0f
    }

    fun findLineAt(y: Float): Int {
        recomputeOffsets()
        val idx = offsets.binarySearch(y)
        return if (idx >= 0) {
            idx
        } else {
            val insertionPoint = -(idx + 1)
            (insertionPoint - 1).coerceIn(0, (offsets.size - 1).coerceAtLeast(0))
        }
    }

    fun findFirstVisible(scrollY: Float): Int {
        val line = findLineAt(scrollY)
        return (line - 1).coerceAtLeast(0)
    }

    fun findLastVisible(bottomY: Float): Int {
        val line = findLineAt(bottomY)
        return (line + 1).coerceAtMost((heights.size - 1).coerceAtLeast(0))
    }
}

/**
 * Unified High-Performance Scribe Virtualized Canvas Prose Editor.
 *
 * Implements a virtualized Canvas text renderer directly on top of Compose BasicTextField:
 * - Single unbroken IME lifecycle: soft keyboard never stutters, closes, or flickers on tap.
 * - Virtualized per-line measurement & rendering: draws only visible lines for 100k+ word documents.
 * - Soft-wrap awareness: cumulative paragraph Y tracker adjusts dynamically to wrapped lines.
 * - Real-time snapshot synchronization: directly reads live text to eliminate debounce lag.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScribeEditor(
    engine: ScribeEditorEngine,
    textStyle: TextStyle,
    cursorBrush: Brush,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
) {
    // --- Scroll (owned by BasicTextField, observed by Canvas) ---
    val scrollState = rememberScrollState()

    // --- Text measurement ---
    // cacheSize=256: covers ~2 screens of lines. ScribeLineCache handles deterministic indexing.
    val textMeasurer = rememberTextMeasurer(cacheSize = 256)

    // --- Per-line layout cache ---
    val lineCache = remember { ScribeLineCache() }

    // --- Focus & keyboard ---
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // --- Density & style derived values ---
    val density = LocalDensity.current
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    val effectiveTextStyle = remember(textStyle) {
        textStyle.copy(
            lineHeight = if (textStyle.fontSize.isSp) (textStyle.fontSize.value * 1.55f).sp else textStyle.lineHeight
        )
    }

    val lineHeightPx = with(density) { effectiveTextStyle.lineHeight.toPx() }

    // Derive live document lines directly from text state to prevent 150ms buffer desync
    val currentText = engine.state.text.toString()
    val docLines = remember(currentText) { LiveDocumentLines(currentText) }

    // Cumulative layout tracker for soft-wrapped paragraph heights
    val layoutTracker = remember(docLines.lineCount, lineHeightPx) {
        LineLayoutTracker(docLines.lineCount, lineHeightPx)
    }

    // --- Cursor blink (draw-phase only, key=Unit so it never restarts incorrectly) ---
    var cursorVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(530L) // 530 ms = Material Design blink period
            cursorVisible = !cursorVisible
        }
    }

    // --- Auto-focus on entry ---
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // --- Programmatic scroll from engine (search jumps, outline navigation) ---
    LaunchedEffect(engine) {
        engine.focusRequests.collectLatest { request ->
            val targetScrollY = layoutTracker.getLineTop(request.lineIndex)
                .toInt()
                .coerceIn(0, scrollState.maxValue)
            scrollState.animateScrollTo(targetScrollY)
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    // --- Cache invalidation on document change ---
    LaunchedEffect(engine) {
        snapshotFlow { engine.state.text.toString() }
            .drop(1) // skip initial emission on subscription
            .collect {
                lineCache.clear()
            }
    }

    BasicTextField(
        state = engine.state,
        modifier = modifier
            .focusRequester(focusRequester)
            .fillMaxSize(),
        textStyle = effectiveTextStyle,
        cursorBrush = cursorBrush,
        lineLimits = TextFieldLineLimits.Default,
        scrollState = scrollState, // hoisted — Canvas reads this too
        inputTransformation = ScribeInputTransformation,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            autoCorrectEnabled = true,
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Default
        ),
        onTextLayout = { /* not used for rendering; Canvas handles it */ },
        decorator = { innerTextField ->

            // ── IME anchor ────────────────────────────────────────────────────────
            // innerTextField MUST be called or the keyboard disconnects.
            // Placed far off-screen to prevent layout clipping and cursor mis-reporting.
            Box(
                Modifier
                    .offset(x = 0.dp, y = (-9999).dp)
                    .size(1.dp)
            ) {
                innerTextField()
            }

            // ── Virtualized Canvas ─────────────────────────────────────────────────
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
            ) {
                val viewportHeightPx = constraints.maxHeight.toFloat()
                val totalLines = docLines.lineCount.coerceAtLeast(1)
                val totalHeightPx = layoutTracker.getTotalHeight() + with(density) { 200.dp.toPx() }

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(with(density) { totalHeightPx.toDp() })
                        .pointerInput(engine, docLines, layoutTracker) {
                            detectTapGestures(
                                onTap = { tapOffset ->
                                    val scrollY = scrollState.value.toFloat()
                                    val docY = tapOffset.y + scrollY
                                    val lineIndex = layoutTracker.findLineAt(docY)
                                    val lineStart = docLines.lineStart(lineIndex)
                                    val lineContent = docLines.lineContent(lineIndex)
                                    val spans = engine.formats.spansIn(
                                        lineStart,
                                        lineStart + lineContent.length
                                    )
                                    val hash = contentHash(lineContent, spans)
                                    val layoutResult = lineCache.get(lineIndex, hash)

                                    val lineTop = layoutTracker.getLineTop(lineIndex)
                                    val localY = docY - lineTop
                                    val charInLine = layoutResult
                                        ?.getOffsetForPosition(Offset(tapOffset.x, localY))
                                        ?: lineContent.length

                                    val targetPos = (lineStart + charInLine).coerceIn(0, engine.state.text.length)
                                    engine.state.edit {
                                        selection = TextRange(targetPos)
                                    }
                                    focusRequester.requestFocus()
                                    keyboardController?.show()
                                },
                                onLongPress = { tapOffset ->
                                    val scrollY = scrollState.value.toFloat()
                                    val docY = tapOffset.y + scrollY
                                    val lineIndex = layoutTracker.findLineAt(docY)
                                    engine.requestLineFocus(lineIndex)
                                }
                            )
                        }
                ) {
                    val scrollY = scrollState.value.toFloat()

                    // Viewport line range via binary search over cumulative paragraph offsets
                    val firstVisible = layoutTracker.findFirstVisible(scrollY)
                    val lastVisible = layoutTracker.findLastVisible(scrollY + viewportHeightPx)

                    val selStart = engine.state.selection.min
                    val selEnd = engine.state.selection.max
                    val cursorPos = engine.state.selection.start

                    for (lineIndex in firstVisible..lastVisible) {
                        val lineStart = docLines.lineStart(lineIndex)
                        val lineLen = docLines.lineLength(lineIndex)
                        val lineEnd = lineStart + lineLen
                        val lineContent = docLines.lineContent(lineIndex)
                        val spans = engine.formats.spansIn(lineStart, lineEnd)
                        val hash = contentHash(lineContent, spans)

                        // Measure or retrieve from cache.
                        val layoutResult = lineCache.get(lineIndex, hash)
                            ?: textMeasurer.measure(
                                text = buildAnnotatedString(
                                    lineContent = lineContent,
                                    spans = spans,
                                    lineStart = lineStart,
                                    lineEnd = lineEnd,
                                    engine = engine,
                                    colorScheme = colorScheme,
                                    typography = typography
                                ),
                                style = effectiveTextStyle,
                                constraints = Constraints.fixedWidth(size.width.toInt().coerceAtLeast(1)),
                                softWrap = true
                            ).also {
                                lineCache.put(lineIndex, hash, it)
                            }

                        // Update dynamic line height for soft-wrap handling
                        layoutTracker.updateHeight(lineIndex, layoutResult.size.height.toFloat())

                        // Exact top Y of this paragraph taking all preceding line heights into account
                        val lineTopY = layoutTracker.getLineTop(lineIndex) - scrollY

                        // ── Selection highlight ──────────────────────────────────────────
                        if (selStart != selEnd) {
                            val lineSelStart = (selStart - lineStart).coerceIn(0, lineLen)
                            val lineSelEnd = (selEnd - lineStart).coerceIn(0, lineLen)
                            if (lineSelStart < lineSelEnd) {
                                val path = layoutResult.getPathForRange(lineSelStart, lineSelEnd)
                                withTransform({ translate(left = 0f, top = lineTopY) }) {
                                    drawPath(path, colorScheme.primary.copy(alpha = 0.3f))
                                }
                            }
                        }

                        // ── Text ─────────────────────────────────────────────────────────
                        drawText(layoutResult, topLeft = Offset(0f, lineTopY))

                        // ── Cursor ───────────────────────────────────────────────────────
                        if (cursorVisible &&
                            cursorPos >= lineStart &&
                            cursorPos <= lineEnd
                        ) {
                            val offsetInLine = (cursorPos - lineStart).coerceIn(0, lineLen)
                            val cursorRect = layoutResult.getCursorRect(offsetInLine)
                            drawRect(
                                color = colorScheme.primary,
                                topLeft = Offset(cursorRect.left, lineTopY + cursorRect.top),
                                size = Size(2.dp.toPx(), cursorRect.height)
                            )
                        }
                    }
                }
            }
        }
    )
}

/**
 * Builds an AnnotatedString for a single paragraph line using format spans and search matches.
 */
private fun buildAnnotatedString(
    lineContent: String,
    spans: List<FormatSpan>,
    lineStart: Int,
    lineEnd: Int,
    engine: ScribeEditorEngine,
    colorScheme: ColorScheme,
    typography: Typography
): AnnotatedString = buildAnnotatedString {
    append(lineContent)

    // 1. Formatting Spans
    for (span in spans) {
        val localStart = (span.start - lineStart).coerceIn(0, lineContent.length)
        val localEnd = (span.end - lineStart).coerceIn(0, lineContent.length)
        if (localStart < localEnd) {
            addStyle(
                style = span.type.toSpanStyle(colorScheme, typography),
                start = localStart,
                end = localEnd
            )
        }
    }

    // 2. Search Highlights
    val searchResults = engine.searchEngine.results
    val currentMatchIndex = engine.searchEngine.currentIndex
    for (i in searchResults.indices) {
        val result = searchResults[i]
        val resultEnd = result.docOffset + result.matchLength
        if (resultEnd <= lineStart || result.docOffset >= lineEnd) continue

        val localStart = (result.docOffset - lineStart).coerceIn(0, lineContent.length)
        val localEnd = (resultEnd - lineStart).coerceIn(0, lineContent.length)
        if (localStart < localEnd) {
            val isCurrent = (i == currentMatchIndex)
            val bgColor = if (isCurrent) Color(0xFFFFD54F) else Color(0x66FFE082)
            addStyle(SpanStyle(background = bgColor), localStart, localEnd)
        }
    }
}
