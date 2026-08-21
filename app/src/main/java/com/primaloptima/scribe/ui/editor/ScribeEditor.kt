/*
 * ScribeEditor — Pure Compose Virtualized Canvas Prose Editor (Researched August 2026).
 *
 * ARCHITECTURAL RESEARCH FINDINGS (Compose BOM 2026.08.00 / Compose 1.12):
 * 1. Cursor Placement API:
 *    `TextLayoutResult.getCursorRect(offset: Int): Rect` is used for cursor positioning.
 *    Unlike `getBoundingBox(offset)`, which returns character bounds and can fail or return
 *    empty rects at line boundaries and end-of-line (EOL), `getCursorRect` accurately computes
 *    the exact cursor insertion caret position across bidirectional (RTL/LTR) text and wraps.
 * 2. Selection Highlight:
 *    `TextLayoutResult.getPathForRange(start: Int, end: Int): Path` is stable and accurately
 *    constructs the complex multi-line selection bounding path within the measured layout.
 * 3. Hoisted ScrollState & No Duplicate Gestures:
 *    `BasicTextField` (Foundation 1.8+ / 1.12) accepts a hoisted `scrollState: ScrollState`
 *    parameter and handles vertical drag/fling gestures directly. Applying an external
 *    `Modifier.verticalScroll(scrollState)` would attach conflicting gesture detectors and cause jank.
 * 4. Cache Invalidation & SnapshotFlow:
 *    `snapshotFlow { engine.state.text.toString() }` emits on every fine-grained snapshot commit
 *    (keystroke). We clear `ScribeLineCache` on change, guaranteeing that only visible lines
 *    (firstVisible..lastVisible) are measured on the next draw frame in O(visible_lines).
 * 5. IME Lifecycle via Off-Screen Anchor:
 *    `innerTextField()` is invoked inside `Box(Modifier.offset(y = (-9999).dp).size(1.dp))` inside
 *    the `decorator` lambda. This keeps the InputConnection alive with the soft keyboard without
 *    causing accessibility bounds mis-reporting or layout clipping glitches.
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
 * Unified High-Performance Scribe Virtualized Canvas Prose Editor.
 *
 * Implements a virtualized Canvas text renderer directly on top of Compose BasicTextField:
 * - Single unbroken IME lifecycle: soft keyboard never stutters, closes, or flickers on tap.
 * - Virtualized per-line measurement & rendering: draws only visible lines for 100k+ word documents.
 * - Pixel-accurate tap-to-cursor placement: uses TextLayoutResult hit testing on measured lines.
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

    val outputTransformation = remember(engine, colorScheme, typography) {
        ScribeOutputTransformation(
            engine = engine,
            colorScheme = colorScheme,
            typography = typography
        )
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
            val targetScrollY = (request.lineIndex * lineHeightPx)
                .toInt()
                .coerceIn(0, scrollState.maxValue)
            scrollState.animateScrollTo(targetScrollY)
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    // --- Cache invalidation on document change ---
    // DocumentBuffer has no dirty-line tracking API, so full lineCache.clear() is performed.
    // TextMeasurer measures only visible lines, so this is an O(visible_lines) operation.
    LaunchedEffect(engine) {
        snapshotFlow { engine.state.text.toString() }
            .drop(1) // skip the initial emission on subscription
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
        outputTransformation = outputTransformation,
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
            // We hide it by placing it far above the visible area.
            // The offset approach (vs size=0.dp) avoids accessibility cursor
            // mis-reporting that the zero-size pattern can cause.
            Box(
                Modifier
                    .offset(x = 0.dp, y = (-9999).dp)
                    .size(1.dp)
            ) {
                innerTextField()
            }

            // ── Virtualized Canvas ─────────────────────────────────────────────────
            // BoxWithConstraints gives us the viewport height in pixels.
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
            ) {
                val viewportHeightPx = constraints.maxHeight.toFloat()
                val totalLines = engine.buffer.lineCount().coerceAtLeast(1)
                // Add 200 dp breathing room so the last line is never clipped.
                val totalHeightPx = totalLines * lineHeightPx + with(density) { 200.dp.toPx() }

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(with(density) { totalHeightPx.toDp() })
                        .pointerInput(engine) {
                            detectTapGestures(
                                onTap = { tapOffset ->
                                    val scrollY = scrollState.value.toFloat()
                                    // tapOffset.y is relative to the Canvas top
                                    val docY = tapOffset.y + scrollY
                                    val lineIndex = (docY / lineHeightPx)
                                        .toInt()
                                        .coerceIn(0, totalLines - 1)
                                    val lineStart = engine.buffer.lineStart(lineIndex)
                                    val lineContent = engine.buffer.lineContent(lineIndex)
                                    val spans = engine.formats.spansIn(
                                        lineStart,
                                        lineStart + lineContent.length
                                    )
                                    val hash = contentHash(lineContent, spans)
                                    val layoutResult = lineCache.get(lineIndex, hash)

                                    // getOffsetForPosition expects offset relative to the
                                    // top-left of the measured layout, not the document.
                                    val localY = docY - lineIndex * lineHeightPx
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
                                    val lineIndex = (docY / lineHeightPx)
                                        .toInt()
                                        .coerceIn(0, totalLines - 1)
                                    // Delegate word selection to the engine.
                                    engine.requestLineFocus(lineIndex)
                                }
                            )
                        }
                ) {
                    val scrollY = scrollState.value.toFloat()

                    // Viewport line calculation.
                    // The +1/-1 buffers prevent lines from popping in/out at edges.
                    val firstVisible = ((scrollY / lineHeightPx).toInt() - 1)
                        .coerceAtLeast(0)
                    val lastVisible = (((scrollY + viewportHeightPx) / lineHeightPx).toInt() + 1)
                        .coerceAtMost(totalLines - 1)

                    val selStart = engine.state.selection.min
                    val selEnd = engine.state.selection.max
                    val cursorPos = engine.state.selection.start

                    for (lineIndex in firstVisible..lastVisible) {
                        val lineStart = engine.buffer.lineStart(lineIndex)
                        val lineLen = engine.buffer.lineLength(lineIndex)
                        val lineEnd = lineStart + lineLen
                        val lineContent = engine.buffer.lineContent(lineIndex)
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
                            ).also { lineCache.put(lineIndex, hash, it) }

                        // lineTopY: distance from canvas top to this line, minus current scroll.
                        val lineTopY = lineIndex * lineHeightPx - scrollY

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
