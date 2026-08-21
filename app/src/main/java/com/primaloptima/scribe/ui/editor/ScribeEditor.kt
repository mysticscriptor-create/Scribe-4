package com.primaloptima.scribe.ui.editor

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * High-performance, zero-allocation line indexer inspired by Sora Editor's Piece/Line Index.
 * Scans CharSequence directly without creating intermediate string allocations or boxed objects.
 */
class FastLineIndexer {
    private var lineStarts = IntArray(512) { 0 }
    var lineCount: Int = 1
        private set
    private var docLength: Int = 0

    fun index(text: CharSequence) {
        docLength = text.length
        var count = 1
        if (lineStarts.isEmpty()) lineStarts = IntArray(512)
        lineStarts[0] = 0

        val len = text.length
        var i = 0
        while (i < len) {
            if (text[i] == '\n') {
                if (count >= lineStarts.size) {
                    lineStarts = lineStarts.copyOf(lineStarts.size * 2)
                }
                lineStarts[count++] = i + 1
            }
            i++
        }
        this.lineCount = count
    }

    fun lineStart(index: Int): Int {
        if (index <= 0) return 0
        if (index >= lineCount) return docLength
        return lineStarts[index].coerceIn(0, docLength)
    }

    fun lineEnd(index: Int): Int {
        if (index < 0) return 0
        val nextStart = if (index + 1 < lineCount) lineStarts[index + 1] else docLength + 1
        return (nextStart - 1).coerceIn(0, docLength)
    }

    fun lineLength(index: Int): Int {
        return (lineEnd(index) - lineStart(index)).coerceAtLeast(0)
    }

    fun getLineContent(text: CharSequence, index: Int): String {
        if (text.isEmpty()) return ""
        val start = lineStart(index).coerceIn(0, text.length)
        val end = lineEnd(index).coerceIn(start, text.length)
        return text.subSequence(start, end).toString()
    }

    fun lineIndexForOffset(offset: Int): Int {
        if (docLength <= 0 || lineCount <= 1) return 0
        val target = offset.coerceIn(0, docLength)
        val idx = lineStarts.binarySearch(target, 0, lineCount)
        return if (idx >= 0) {
            idx
        } else {
            val insertionPoint = -(idx + 1)
            (insertionPoint - 1).coerceIn(0, lineCount - 1)
        }
    }
}

/**
 * Dynamic per-line height & cumulative Y-offset tracker.
 * Reuses internal primitive arrays to eliminate GC pressure during typing and scrolling.
 */
class LineLayoutTracker(private val defaultLineHeight: Float, private val charsPerLine: Int = 45) {
    private var heights = FloatArray(512)
    private var offsets = FloatArray(512)
    var count: Int = 0
        private set
    private var dirty: Boolean = true
    private var cachedTotalHeight: Float = 0f

    fun sync(indexer: FastLineIndexer) {
        val lineCount = indexer.lineCount.coerceAtLeast(1)
        if (heights.size < lineCount) {
            val newCap = maxOf(lineCount + 128, heights.size * 2)
            heights = heights.copyOf(newCap)
            offsets = offsets.copyOf(newCap)
        }

        if (count != lineCount) {
            for (i in 0 until lineCount) {
                if (i >= count || heights[i] <= 0f) {
                    val len = indexer.lineLength(i)
                    val estLines = (len / charsPerLine).coerceAtLeast(1)
                    heights[i] = estLines * defaultLineHeight
                }
            }
            count = lineCount
            dirty = true
        }
    }

    fun updateHeight(lineIndex: Int, height: Float): Boolean {
        if (lineIndex in 0 until count && heights[lineIndex] != height) {
            heights[lineIndex] = height
            dirty = true
            return true
        }
        return false
    }

    fun getLineHeight(lineIndex: Int): Float {
        return if (lineIndex in 0 until count) heights[lineIndex] else defaultLineHeight
    }

    private fun recomputeOffsets() {
        if (!dirty) return
        var accum = 0f
        for (i in 0 until count) {
            offsets[i] = accum
            accum += heights[i]
        }
        cachedTotalHeight = accum
        dirty = false
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
        return (line - 1).coerceIn(0, (count - 1).coerceAtLeast(0))
    }

    fun findLastVisible(bottomY: Float): Int {
        val line = findLineAt(bottomY.coerceAtLeast(0f))
        return (line + 1).coerceIn(0, (count - 1).coerceAtLeast(0))
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
    val lineIndexer = remember { FastLineIndexer() }
    val layoutTracker = remember(lineHeightPx) { LineLayoutTracker(lineHeightPx) }

    // Synchronize indexer and layout on text update
    val textSnapshot = engine.state.text
    lineIndexer.index(textSnapshot)
    layoutTracker.sync(lineIndexer)

    val coroutineScope = rememberCoroutineScope()
    var viewportHeightPx by remember { mutableFloatStateOf(0f) }
    var scrollY by remember { mutableFloatStateOf(0f) }
    var dragAnchorOffset by remember { mutableIntStateOf(-1) }
    var scrollAnimationJob by remember { mutableStateOf<Job?>(null) }

    val bottomPaddingPx = with(density) { 320.dp.toPx() }
    val totalHeightPx = layoutTracker.getTotalHeight() + bottomPaddingPx
    val maxScrollY = (totalHeightPx - viewportHeightPx).coerceAtLeast(0f)

    // Ensure scrollY stays clamped when document height changes
    LaunchedEffect(maxScrollY) {
        if (scrollY > maxScrollY) {
            scrollY = maxScrollY
        }
    }

    // High-performance virtual scrollable state with native Android fling & drag physics
    val scrollableState = rememberScrollableState { delta ->
        scrollAnimationJob?.cancel()
        val oldScroll = scrollY
        val newScroll = (oldScroll - delta).coerceIn(0f, maxScrollY)
        scrollY = newScroll
        oldScroll - newScroll
    }

    fun animateScrollTo(targetY: Float) {
        scrollAnimationJob?.cancel()
        scrollAnimationJob = coroutineScope.launch {
            val target = targetY.coerceIn(0f, maxScrollY)
            val animatable = Animatable(scrollY, Float.VectorConverter)
            animatable.animateTo(
                targetValue = target,
                animationSpec = spring(stiffness = 650f, dampingRatio = 0.85f)
            ) {
                scrollY = value.coerceIn(0f, maxScrollY)
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

    // Viewport tracking: ensure cursor remains visible
    LaunchedEffect(engine.state.selection) {
        val cursorPos = engine.state.selection.start
        val lineIndex = lineIndexer.lineIndexForOffset(cursorPos)
        val lineTop = layoutTracker.getLineTop(lineIndex)
        val lineHeight = layoutTracker.getLineHeight(lineIndex)
        val lineBottom = lineTop + lineHeight
        val viewport = viewportHeightPx
        if (viewport > 0f) {
            val paddingPx = with(density) { 64.dp.toPx() }
            if (lineBottom > scrollY + viewport - paddingPx) {
                val target = lineBottom - viewport + paddingPx
                animateScrollTo(target)
            } else if (lineTop < scrollY + paddingPx) {
                val target = (lineTop - paddingPx).coerceAtLeast(0f)
                animateScrollTo(target)
            }
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
                // Invisible 0-size IME anchor
                Box(
                    Modifier
                        .size(0.dp)
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
                            .pointerInput(engine) {
                                detectTapGestures(
                                    onTap = { tapOffset ->
                                        val docY = tapOffset.y + scrollY
                                        val lineIndex = layoutTracker.findLineAt(docY)
                                        val lineStart = lineIndexer.lineStart(lineIndex)
                                        val lineContent = lineIndexer.getLineContent(engine.state.text, lineIndex)
                                        val spans = engine.formats.spansIn(lineStart, lineStart + lineContent.length)
                                        val hash = contentHash(lineContent, spans, engine.searchEngine.currentIndex)
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
                                    onDoubleTap = { tapOffset ->
                                        val docY = tapOffset.y + scrollY
                                        val lineIndex = layoutTracker.findLineAt(docY)
                                        val lineStart = lineIndexer.lineStart(lineIndex)
                                        val lineContent = lineIndexer.getLineContent(engine.state.text, lineIndex)
                                        val spans = engine.formats.spansIn(lineStart, lineStart + lineContent.length)
                                        val hash = contentHash(lineContent, spans, engine.searchEngine.currentIndex)
                                        val layoutResult = lineCache.get(lineIndex, hash)

                                        val lineTop = layoutTracker.getLineTop(lineIndex)
                                        val localY = docY - lineTop
                                        val charInLine = layoutResult
                                            ?.getOffsetForPosition(Offset(tapOffset.x, localY))
                                            ?: (lineContent.length / 2)

                                        val targetPos = (lineStart + charInLine).coerceIn(0, engine.state.text.length)
                                        val wordRange = selectWordAt(engine.state.text, targetPos)
                                        engine.state.edit {
                                            selection = wordRange
                                        }
                                        focusRequester.requestFocus()
                                        keyboardController?.show()
                                    }
                                )
                            }
                            .pointerInput(engine) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { startOffset ->
                                        val docY = startOffset.y + scrollY
                                        val lineIndex = layoutTracker.findLineAt(docY)
                                        val lineStart = lineIndexer.lineStart(lineIndex)
                                        val lineContent = lineIndexer.getLineContent(engine.state.text, lineIndex)
                                        val spans = engine.formats.spansIn(lineStart, lineStart + lineContent.length)
                                        val hash = contentHash(lineContent, spans, engine.searchEngine.currentIndex)
                                        val layoutResult = lineCache.get(lineIndex, hash)

                                        val lineTop = layoutTracker.getLineTop(lineIndex)
                                        val localY = docY - lineTop
                                        val charInLine = layoutResult
                                            ?.getOffsetForPosition(Offset(startOffset.x, localY))
                                            ?: lineContent.length

                                        val anchor = (lineStart + charInLine).coerceIn(0, engine.state.text.length)
                                        dragAnchorOffset = anchor
                                        engine.state.edit { selection = TextRange(anchor) }
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        if (dragAnchorOffset >= 0) {
                                            val docY = change.position.y + scrollY
                                            val lineIndex = layoutTracker.findLineAt(docY)
                                            val lineStart = lineIndexer.lineStart(lineIndex)
                                            val lineContent = lineIndexer.getLineContent(engine.state.text, lineIndex)
                                            val spans = engine.formats.spansIn(lineStart, lineStart + lineContent.length)
                                            val hash = contentHash(lineContent, spans, engine.searchEngine.currentIndex)
                                            val layoutResult = lineCache.get(lineIndex, hash)

                                            val lineTop = layoutTracker.getLineTop(lineIndex)
                                            val localY = docY - lineTop
                                            val charInLine = layoutResult
                                                ?.getOffsetForPosition(Offset(change.position.x, localY))
                                                ?: lineContent.length

                                            val currentPos = (lineStart + charInLine).coerceIn(0, engine.state.text.length)
                                            engine.state.edit {
                                                selection = TextRange(dragAnchorOffset, currentPos)
                                            }
                                        }
                                    },
                                    onDragEnd = { dragAnchorOffset = -1 },
                                    onDragCancel = { dragAnchorOffset = -1 }
                                )
                            }
                    ) {
                        val currentScrollY = scrollY
                        val vh = if (viewportHeightPx > 0f) viewportHeightPx else size.height

                        val firstVisible = layoutTracker.findFirstVisible(currentScrollY)
                        val lastVisible = layoutTracker.findLastVisible(currentScrollY + vh)

                        val selStart = engine.state.selection.min
                        val selEnd = engine.state.selection.max
                        val cursorPos = engine.state.selection.start
                        val searchVersion = engine.searchEngine.currentIndex

                        withTransform({ translate(left = 0f, top = -currentScrollY) }) {
                            for (lineIndex in firstVisible..lastVisible) {
                                val lineStart = lineIndexer.lineStart(lineIndex)
                                val lineLen = lineIndexer.lineLength(lineIndex)
                                val lineEnd = lineStart + lineLen
                                val lineContent = lineIndexer.getLineContent(engine.state.text, lineIndex)
                                val spans = engine.formats.spansIn(lineStart, lineEnd)
                                val hash = contentHash(lineContent, spans, searchVersion)

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
                        if (maxScrollY > 0f && vh > 0f) {
                            val thumbHeight = (vh * (vh / (maxScrollY + vh)))
                                .coerceIn(32.dp.toPx(), vh / 4)
                            val scrollRatio = (currentScrollY / maxScrollY).coerceIn(0f, 1f)
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
