package com.primaloptima.scribe.ui.editor

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
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
import com.primaloptima.scribe.engine.FastLineIndexer
import com.primaloptima.scribe.engine.FormatSpan
import com.primaloptima.scribe.engine.ScribeEditorEngine
import com.primaloptima.scribe.engine.toSpanStyle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Dynamic per-line height & cumulative Y-offset tracker with dirty-range prefix-sum recomputation.
 * Reuses internal primitive arrays to eliminate GC pressure during typing and scrolling.
 */
class LineLayoutTracker(private val defaultLineHeight: Float, private val charsPerLine: Int = 38) {
    private var heights = FloatArray(512)
    private var offsets = FloatArray(512)
    private var isMeasured = BooleanArray(512)
    var count: Int = 0
        private set
    private var dirtyStartLine: Int = 0
    private var cachedTotalHeight: Float = 0f

    fun sync(indexer: FastLineIndexer) {
        val lineCount = indexer.lineCount.coerceAtLeast(1)
        if (heights.size < lineCount) {
            val newCap = maxOf(lineCount + 128, heights.size * 2)
            heights = heights.copyOf(newCap)
            offsets = offsets.copyOf(newCap)
            isMeasured = isMeasured.copyOf(newCap)
        }
        val countChanged = (count != lineCount)
        var firstUnmeasured = -1
        for (i in 0 until lineCount) {
            if (countChanged || !isMeasured[i] || heights[i] <= 0f) {
                if (!isMeasured[i]) {
                    val len = indexer.lineLength(i)
                    val estLines = (len / charsPerLine).coerceAtLeast(1)
                    heights[i] = estLines * defaultLineHeight
                }
                if (firstUnmeasured == -1) firstUnmeasured = i
            }
        }
        count = lineCount
        if (countChanged || firstUnmeasured != -1) {
            dirtyStartLine = minOf(dirtyStartLine, if (firstUnmeasured != -1) firstUnmeasured else 0)
        }
    }

    fun updateHeight(lineIndex: Int, height: Float): Boolean {
        if (lineIndex in 0 until count) {
            val diff = kotlin.math.abs(heights[lineIndex] - height)
            if (!isMeasured[lineIndex] || diff > 0.5f) {
                heights[lineIndex] = height
                isMeasured[lineIndex] = true
                dirtyStartLine = minOf(dirtyStartLine, lineIndex)
                return true
            }
        }
        return false
    }

    fun getLineHeight(lineIndex: Int): Float {
        return if (lineIndex in 0 until count) heights[lineIndex] else defaultLineHeight
    }

    private fun recomputeOffsets() {
        if (dirtyStartLine >= count) return
        val start = dirtyStartLine.coerceIn(0, count)
        var accum = if (start == 0) 0f else offsets[start - 1] + heights[start - 1]
        for (i in start until count) {
            offsets[i] = accum
            accum += heights[i]
        }
        cachedTotalHeight = if (count > 0) offsets[count - 1] + heights[count - 1] else 0f
        dirtyStartLine = Int.MAX_VALUE
    }

    fun getLineTop(lineIndex: Int): Float {
        recomputeOffsets()
        return if (lineIndex in 0 until count) offsets[lineIndex] else 0f
    }

    fun getTotalHeight(): Float {
        recomputeOffsets()
        return cachedTotalHeight
    }

    fun findLineAt(y: Float): Int {
        recomputeOffsets()
        if (count <= 0) return 0
        if (y <= 0f) return 0
        val idx = offsets.binarySearch(y, 0, count)
        return if (idx >= 0) {
            idx.coerceIn(0, count - 1)
        } else {
            val insertionPoint = -(idx + 1)
            (insertionPoint - 1).coerceIn(0, count - 1)
        }
    }

    fun findFirstVisible(scrollY: Float): Int {
        val line = findLineAt(scrollY.coerceAtLeast(0f))
        return (line - 2).coerceIn(0, (count - 1).coerceAtLeast(0))
    }

    fun findLastVisible(bottomY: Float): Int {
        val line = findLineAt(bottomY.coerceAtLeast(0f))
        return (line + 2).coerceIn(0, (count - 1).coerceAtLeast(0))
    }
}

/**
 * Crash-Proof, Zero-Allocation Virtualized Canvas Prose Editor (Sora Editor Architecture).
 *
 * Key Architecture Highlights:
 * 1. Infinite-Length Crash Proofing: The Canvas is strictly bounded to viewport size (fillMaxSize),
 *    never allocating giant layout nodes that violate Compose Constraints limits (262,143px).
 * 2. Virtual Scroll Engine: Hardware-accelerated momentum fling & drag via Modifier.scrollable.
 * 3. Viewport-Only Layout & Render: Only lines within viewport are measured & drawn.
 * 4. Zero-Recomposition Draw Phase: Cursor blinking & text rendering run purely in draw phase.
 * 5. Full Prose Touch Gestures: Sub-pixel tap positioning, double-tap word selection, and long-press drag selection.
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
    val textMeasurer = rememberTextMeasurer(cacheSize = 512)
    val lineCache = remember { ScribeLineCache(maxCapacity = 1024) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val density = LocalDensity.current
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val defaultTextColor = colorScheme.onBackground
    val effectiveTextStyle = remember(textStyle, defaultTextColor) {
        textStyle.copy(
            color = if (textStyle.color != Color.Unspecified) textStyle.color else defaultTextColor,
            lineHeight = if (textStyle.fontSize.isSp) (textStyle.fontSize.value * 1.55f).sp else textStyle.lineHeight
        )
    }
    val lineHeightPx = with(density) { effectiveTextStyle.lineHeight.toPx() }

    // Fast Line Indexer and Layout Tracker
    val lineIndexer = engine.lineIndexer
    val layoutTracker = remember(lineHeightPx) { LineLayoutTracker(lineHeightPx) }

    // Synchronize line indexer and layout tracker on text change
    LaunchedEffect(engine.state.text) {
        val prevCount = layoutTracker.count
        lineIndexer.index(engine.state.text)
        if (layoutTracker.count != lineIndexer.lineCount) {
            layoutTracker.sync(lineIndexer)
        }
    }

    val coroutineScope = rememberCoroutineScope()
    var viewportHeightPx by remember { mutableFloatStateOf(0f) }
    var scrollY by remember { mutableFloatStateOf(0f) }
    var dragAnchorOffset by remember { mutableIntStateOf(-1) }
    var scrollAnimationJob by remember { mutableStateOf<Job?>(null) }
    var isUserTapSelection by remember { mutableStateOf(false) }

    val bottomPaddingPx = with(density) { 320.dp.toPx() }

    fun computeMaxScrollY(): Float {
        val total = layoutTracker.getTotalHeight() + bottomPaddingPx
        return (total - viewportHeightPx).coerceAtLeast(0f)
    }

    // High-performance virtual scrollable state with dynamic document-height clamping
    val scrollableState = rememberScrollableState { delta ->
        scrollAnimationJob?.cancel()
        val currentMax = computeMaxScrollY()
        val oldScroll = scrollY
        val newScroll = (oldScroll - delta).coerceIn(0f, currentMax)
        scrollY = newScroll
        oldScroll - newScroll
    }

    fun animateScrollTo(targetY: Float) {
        scrollAnimationJob?.cancel()
        val target = targetY.coerceIn(0f, computeMaxScrollY())
        if (kotlin.math.abs(scrollY - target) < 1f) return
        scrollAnimationJob = coroutineScope.launch {
            val animatable = Animatable(scrollY, Float.VectorConverter)
            animatable.animateTo(
                targetValue = target,
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
            ) {
                val stepMax = computeMaxScrollY()
                scrollY = value.coerceIn(0f, stepMax)
            }
        }
    }

    // Cursor blink timer (draw-phase only)
    var cursorVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(530L)
            cursorVisible = !cursorVisible
        }
    }

    // Auto-focus on initial appearance
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // External focus navigation (from search or outline jumps)
    LaunchedEffect(engine) {
        engine.focusRequests.collectLatest { request ->
            val targetScrollY = layoutTracker.getLineTop(request.lineIndex)
            animateScrollTo(targetScrollY)
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    // Viewport tracking: ensure cursor remains visible only when cursor goes outside viewport
    LaunchedEffect(engine.state.selection) {
        if (isUserTapSelection) {
            isUserTapSelection = false
            return@LaunchedEffect
        }
        val cursorPos = engine.state.selection.start
        val lineIndex = lineIndexer.lineIndexForOffset(cursorPos)
        val lineTop = layoutTracker.getLineTop(lineIndex)
        val lineHeight = layoutTracker.getLineHeight(lineIndex)
        val lineBottom = lineTop + lineHeight
        val viewport = viewportHeightPx

        if (viewport > 0f) {
            val paddingPx = with(density) { 16.dp.toPx() }
            val marginPx = with(density) { 48.dp.toPx() } // ignore tiny out-of-bounds due to height estimation
            if (lineBottom > scrollY + viewport + marginPx) {
                val target = (lineBottom - viewport + paddingPx).coerceAtLeast(0f)
                animateScrollTo(target)
            } else if (lineTop < scrollY - marginPx) {
                val target = (lineTop - paddingPx).coerceAtLeast(0f)
                animateScrollTo(target)
            }
        }
    }

    fun getOrMeasureLineLayout(
        lineIndex: Int,
        canvasWidth: Int
    ): TextLayoutResult {
        val lineStart = lineIndexer.lineStart(lineIndex)
        val lineLen = lineIndexer.lineLength(lineIndex)
        val lineEnd = lineStart + lineLen
        val lineContent = lineIndexer.getLineContent(engine.state.text, lineIndex)
        val spans = engine.formats.spansIn(lineStart, lineEnd)
        val hash = contentHash(lineContent, spans, engine.searchEngine.currentIndex)

        return lineCache.get(lineIndex, hash) ?: textMeasurer.measure(
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
            constraints = Constraints.fixedWidth(canvasWidth.coerceAtLeast(1)),
            softWrap = true
        ).also {
            lineCache.put(lineIndex, hash, it)
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
        inputTransformation = ScribeInputTransformation,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            autoCorrectEnabled = true,
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Default
        ),
        onTextLayout = { /* Canvas performs virtualized text layout */ },
        decorator = { innerTextField ->
            Box(Modifier.fillMaxSize()) {
                // Off-screen layout anchor for Android IME cursor rect anchoring
                Box(
                    Modifier
                        .offset(y = (-9999).dp)
                        .alpha(0f)
                ) {
                    innerTextField()
                }

                // Crash-Proof Virtualized Canvas Viewport (strictly bounded by screen size)
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                ) {
                    viewportHeightPx = constraints.maxHeight.toFloat()

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .scrollable(
                                orientation = Orientation.Vertical,
                                state = scrollableState
                            )
                            .pointerInput(Unit) {
                                var lastTapTime = 0L
                                var lastTapOffset = Offset.Zero
                                val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
                                val doubleTapMinTime = viewConfiguration.doubleTapMinTimeMillis
                                val touchSlop = viewConfiguration.touchSlop
                                val longPressTimeout = viewConfiguration.longPressTimeoutMillis

                                awaitEachGesture {
                                    // 1. Immediately cancel any running programmatic scroll animations on finger down
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    scrollAnimationJob?.cancel()
                                    scrollAnimationJob = null
                                    cursorVisible = true

                                    val downPos = down.position
                                    val downTime = System.currentTimeMillis()
                                    val isDoubleTap = (downTime - lastTapTime in doubleTapMinTime..doubleTapTimeout) &&
                                            ((downPos - lastTapOffset).getDistance() <= touchSlop * 2f)

                                    if (isDoubleTap) {
                                        down.consume()
                                        lastTapTime = 0L
                                        val docY = downPos.y + scrollY
                                        val lineIndex = layoutTracker.findLineAt(docY)
                                        val lineStart = lineIndexer.lineStart(lineIndex)
                                        val layoutResult = getOrMeasureLineLayout(lineIndex, size.width)
                                        val lineTop = layoutTracker.getLineTop(lineIndex)
                                        val localY = docY - lineTop
                                        val charInLine = layoutResult.getOffsetForPosition(Offset(downPos.x, localY))
                                        val targetPos = (lineStart + charInLine).coerceIn(0, engine.state.text.length)
                                        val wordRange = selectWordAt(engine.state.text, targetPos)

                                        isUserTapSelection = true
                                        engine.state.edit {
                                            selection = wordRange
                                        }
                                        focusRequester.requestFocus()
                                        keyboardController?.show()
                                        return@awaitEachGesture
                                    }

                                    // Check if this gesture turns into a tap, long press, or scroll
                                    var pointerMovedBeyondSlop = false
                                    val tapUpOrNull = withTimeoutOrNull(longPressTimeout) {
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Main)
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed || change.isConsumed) {
                                                return@withTimeoutOrNull if (!change.pressed) change else null
                                            }
                                            if ((change.position - downPos).getDistance() > touchSlop) {
                                                pointerMovedBeyondSlop = true
                                                return@withTimeoutOrNull null
                                            }
                                        }
                                        null
                                    }

                                    if (pointerMovedBeyondSlop) {
                                        // Scrolling is handled natively by Modifier.scrollable
                                        return@awaitEachGesture
                                    }

                                    if (tapUpOrNull != null) {
                                        // Finger lifted -> ZERO-LATENCY SINGLE TAP
                                        tapUpOrNull.consume()
                                        lastTapTime = downTime
                                        lastTapOffset = downPos

                                        val docY = downPos.y + scrollY
                                        val lineIndex = layoutTracker.findLineAt(docY)
                                        val lineStart = lineIndexer.lineStart(lineIndex)
                                        val layoutResult = getOrMeasureLineLayout(lineIndex, size.width)
                                        val lineTop = layoutTracker.getLineTop(lineIndex)
                                        val localY = docY - lineTop
                                        val charInLine = layoutResult.getOffsetForPosition(Offset(downPos.x, localY))
                                        val targetPos = (lineStart + charInLine).coerceIn(0, engine.state.text.length)

                                        isUserTapSelection = true
                                        engine.state.edit {
                                            selection = TextRange(targetPos)
                                        }
                                        focusRequester.requestFocus()
                                        keyboardController?.show()
                                    } else {
                                        // Pointer held past long-press timeout -> initiate drag selection
                                        val docY = downPos.y + scrollY
                                        val lineIndex = layoutTracker.findLineAt(docY)
                                        val lineStart = lineIndexer.lineStart(lineIndex)
                                        val layoutResult = getOrMeasureLineLayout(lineIndex, size.width)
                                        val lineTop = layoutTracker.getLineTop(lineIndex)
                                        val localY = docY - lineTop
                                        val charInLine = layoutResult.getOffsetForPosition(Offset(downPos.x, localY))
                                        val anchor = (lineStart + charInLine).coerceIn(0, engine.state.text.length)
                                        dragAnchorOffset = anchor
                                        engine.state.edit { selection = TextRange(anchor) }

                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Main)
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) break
                                            change.consume()

                                            val dragDocY = change.position.y + scrollY
                                            val dragLineIdx = layoutTracker.findLineAt(dragDocY)
                                            val dragLineStart = lineIndexer.lineStart(dragLineIdx)
                                            val dragLayout = getOrMeasureLineLayout(dragLineIdx, size.width)
                                            val dragLineTop = layoutTracker.getLineTop(dragLineIdx)
                                            val dragLocalY = dragDocY - dragLineTop
                                            val dragChar = dragLayout.getOffsetForPosition(Offset(change.position.x, dragLocalY))
                                            val currentPos = (dragLineStart + dragChar).coerceIn(0, engine.state.text.length)

                                            engine.state.edit {
                                                selection = TextRange(dragAnchorOffset, currentPos)
                                            }
                                        }
                                        dragAnchorOffset = -1
                                    }
                                }
                            }
                    ) {
                        val currentScrollY = scrollY
                        val vh = if (viewportHeightPx > 0f) viewportHeightPx else size.height
                        val firstVisible = layoutTracker.findFirstVisible(currentScrollY)
                        val lastVisible = layoutTracker.findLastVisible(currentScrollY + vh)
                        val selStart = engine.state.selection.min
                        val selEnd = engine.state.selection.max
                        val cursorPos = engine.state.selection.start

                        withTransform({ translate(left = 0f, top = -currentScrollY) }) {
                            for (lineIndex in firstVisible..lastVisible) {
                                val lineStart = lineIndexer.lineStart(lineIndex)
                                val lineLen = lineIndexer.lineLength(lineIndex)
                                val lineEnd = lineStart + lineLen

                                val layoutResult = getOrMeasureLineLayout(lineIndex, size.width.toInt())
                                layoutTracker.updateHeight(lineIndex, layoutResult.size.height.toFloat())
                                val lineTopY = layoutTracker.getLineTop(lineIndex)

                                // Draw Selection Highlight
                                if (selStart != selEnd) {
                                    val lineSelStart = (selStart - lineStart).coerceIn(0, lineLen)
                                    val lineSelEnd = (selEnd - lineStart).coerceIn(0, lineLen)
                                    if (lineSelStart < lineSelEnd) {
                                        val path = layoutResult.getPathForRange(lineSelStart, lineSelEnd)
                                        withTransform({ translate(left = 0f, top = lineTopY) }) {
                                            drawPath(path, colorScheme.primary.copy(alpha = 0.28f))
                                        }
                                    }
                                }

                                // Draw Paragraph Text
                                drawText(layoutResult, topLeft = Offset(0f, lineTopY))

                                // Draw Active Blinking Cursor
                                if (cursorVisible && cursorPos in lineStart..lineEnd) {
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

                        // Minimal Elegant Scrollbar Indicator
                        val maxScroll = computeMaxScrollY()
                        if (maxScroll > 0f && vh > 0f) {
                            val thumbHeight = (vh * (vh / (maxScroll + vh)))
                                .coerceIn(32.dp.toPx(), vh / 4)
                            val scrollRatio = (currentScrollY / maxScroll).coerceIn(0f, 1f)
                            val thumbTop = scrollRatio * (vh - thumbHeight)

                            drawRoundRect(
                                color = colorScheme.onSurface.copy(alpha = 0.25f),
                                topLeft = Offset(size.width - 3.dp.toPx(), thumbTop),
                                size = Size(2.5.dp.toPx(), thumbHeight),
                                cornerRadius = CornerRadius(1.5.dp.toPx())
                            )
                        }
                    }
                }
            }
        }
    )
}

/**
 * Identifies word boundaries around a character offset for double-tap selection.
 */
private fun selectWordAt(text: CharSequence, offset: Int): TextRange {
    if (text.isEmpty()) return TextRange(0, 0)
    val pos = offset.coerceIn(0, text.length - 1)
    var start = pos
    var end = pos
    while (start > 0 && isWordChar(text[start - 1])) {
        start--
    }
    while (end < text.length && isWordChar(text[end])) {
        end++
    }
    return if (start < end) TextRange(start, end) else TextRange(pos, (pos + 1).coerceAtMost(text.length))
}

private fun isWordChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_'

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
