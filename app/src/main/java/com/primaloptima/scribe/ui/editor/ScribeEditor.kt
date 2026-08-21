/*
 * ScribeEditor — Production-Grade Virtualized Canvas Editor
 *
 * Architecture:
 * 1. BasicTextField (Headless / Anchor Mode): Maintains native Android IME state, Gboard autocorrect,
 *    and hardware/software keyboard input transformation without measuring all 100k words.
 * 2. Virtualized Canvas (120fps Silky Rendering): Only measures and draws visible lines in the viewport.
 * 3. Zero-Allocation Line Layout Cache: Hits pre-measured TextLayoutResults with primitive Int checks during scrolling.
 * 4. Native Selection Handles & Text Toolbar: Teardrop draggable handles and floating Action Mode menu (Cut/Copy/Paste/Select All).
 * 5. Instant Tap Response: Direct O(log N) line lookup and zero unneeded auto-scroll locking.
 */

package com.primaloptima.scribe.ui.editor

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalTextToolbar
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
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import com.primaloptima.scribe.engine.FastLineIndexer
import com.primaloptima.scribe.engine.FormatSpan
import com.primaloptima.scribe.engine.ScribeEditorEngine
import com.primaloptima.scribe.engine.toSpanStyle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private enum class SelectionHandleType {
    START,
    END
}

/**
 * High-performance line layout tracker with cached prefix-sum geometry.
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

    fun recomputeOffsets() {
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
 * ScribeEditor — Primary virtualized prose editor.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScribeEditor(
    engine: ScribeEditorEngine,
    textStyle: TextStyle = TextStyle.Default,
    cursorBrush: Brush = SolidColor(MaterialTheme.colorScheme.primary),
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
    fontSizeSp: Float = 16.5f,
    lineHeightSp: Float = 27.5f,
) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val density = LocalDensity.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val textMeasurer = rememberTextMeasurer()
    val textToolbar = LocalTextToolbar.current
    val clipboardManager = LocalClipboardManager.current

    val effectiveTextStyle = remember(textStyle, fontSizeSp, lineHeightSp, colorScheme.onSurface) {
        val base = if (textStyle != TextStyle.Default) {
            textStyle
        } else {
            TextStyle(
                fontSize = fontSizeSp.sp,
                lineHeight = lineHeightSp.sp,
                color = colorScheme.onSurface,
                letterSpacing = 0.15.sp
            )
        }
        val lineH = if (base.lineHeight.isUnspecified || base.lineHeight.value <= 0f) {
            val fs = if (base.fontSize.isUnspecified || base.fontSize.value <= 0f) fontSizeSp else base.fontSize.value
            (fs * 1.6f).sp
        } else {
            base.lineHeight
        }
        base.copy(lineHeight = lineH)
    }

    val lineHeightPx = with(density) { effectiveTextStyle.lineHeight.toPx() }
    val lineIndexer = engine.lineIndexer
    val layoutTracker = remember(lineHeightPx) { LineLayoutTracker(lineHeightPx) }
    val lineCache = remember { ScribeLineCache(1024) }

    val coroutineScope = rememberCoroutineScope()
    var viewportHeightPx by remember { mutableFloatStateOf(0f) }
    var viewportWidthPx by remember { mutableFloatStateOf(0f) }
    var scrollY by remember { mutableFloatStateOf(0f) }
    var scrollAnimationJob by remember { mutableStateOf<Job?>(null) }
    val bottomPaddingPx = with(density) { 320.dp.toPx() }
    val handleTouchRadiusPx = with(density) { 32.dp.toPx() }
    val handleVisualRadiusPx = with(density) { 10.dp.toPx() }

    fun computeMaxScrollY(): Float {
        val total = layoutTracker.getTotalHeight() + bottomPaddingPx
        return (total - viewportHeightPx).coerceAtLeast(0f)
    }

    // High-performance virtual scrollable state with 120fps inertial fling
    val scrollableState = rememberScrollableState { delta ->
        scrollAnimationJob?.cancel()
        textToolbar.hide()
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

    // Zero-allocation line layout resolver with primitive Int cache checking
    fun getOrMeasureLineLayout(
        lineIndex: Int,
        lineStart: Int,
        lineLen: Int,
        canvasWidth: Int,
        docRev: Int,
        searchVer: Int
    ): TextLayoutResult {
        val cached = lineCache.get(
            lineIndex = lineIndex,
            revision = docRev,
            searchVersion = searchVer,
            lineStart = lineStart,
            lineLength = lineLen,
            width = canvasWidth
        )
        if (cached != null) return cached

        val lineContent = lineIndexer.getLineContent(engine.state.text, lineIndex)
        val spans = engine.formats.spansIn(lineStart, lineStart + lineLen)
        val measured = textMeasurer.measure(
            text = buildAnnotatedString(
                lineContent = lineContent,
                spans = spans,
                lineStart = lineStart,
                lineEnd = lineStart + lineLen,
                engine = engine,
                colorScheme = colorScheme,
                typography = typography
            ),
            style = effectiveTextStyle,
            constraints = Constraints.fixedWidth(canvasWidth.coerceAtLeast(1)),
            softWrap = true
        )
        lineCache.put(
            lineIndex = lineIndex,
            revision = docRev,
            searchVersion = searchVer,
            lineStart = lineStart,
            lineLength = lineLen,
            width = canvasWidth,
            result = measured
        )
        return measured
    }

    // Synchronize line indexer and layout tracker only on text modifications
    var lastRecordedTextLength by remember { mutableIntStateOf(engine.state.text.length) }
    LaunchedEffect(engine.state.text) {
        val prevCount = layoutTracker.count
        lineIndexer.index(engine.state.text)
        if (layoutTracker.count != lineIndexer.lineCount) {
            layoutTracker.sync(lineIndexer)
        }

        // Only auto-scroll to cursor when text content was actually modified (typing/deleting)
        val currentLen = engine.state.text.length
        if (currentLen != lastRecordedTextLength) {
            lastRecordedTextLength = currentLen
            val cursorPos = engine.state.selection.start
            val lineIndex = lineIndexer.lineIndexForOffset(cursorPos)
            val lineTop = layoutTracker.getLineTop(lineIndex)
            val lineHeight = layoutTracker.getLineHeight(lineIndex)
            val lineBottom = lineTop + lineHeight
            val viewport = viewportHeightPx
            if (viewport > 0f) {
                val paddingPx = with(density) { 32.dp.toPx() }
                if (lineBottom > scrollY + viewport) {
                    val target = (lineBottom - viewport + paddingPx).coerceAtLeast(0f)
                    animateScrollTo(target)
                } else if (lineTop < scrollY) {
                    val target = (lineTop - paddingPx).coerceAtLeast(0f)
                    animateScrollTo(target)
                }
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

    // Floating Contextual Text Toolbar Menu helper
    fun showFloatingToolbar() {
        val sel = engine.state.selection
        if (sel.collapsed) {
            textToolbar.hide()
            return
        }
        val selMin = sel.min.coerceIn(0, engine.state.text.length)
        val selMax = sel.max.coerceIn(0, engine.state.text.length)
        if (selMin >= selMax) {
            textToolbar.hide()
            return
        }

        val startLine = lineIndexer.lineIndexForOffset(selMin)
        val endLine = lineIndexer.lineIndexForOffset(selMax)
        val startTop = layoutTracker.getLineTop(startLine) - scrollY
        val endTop = layoutTracker.getLineTop(endLine) - scrollY
        val endHeight = layoutTracker.getLineHeight(endLine)
        val vh = if (viewportHeightPx > 0f) viewportHeightPx else 1000f

        val menuRect = Rect(
            left = 0f,
            top = startTop.coerceIn(0f, vh),
            right = if (viewportWidthPx > 0f) viewportWidthPx else 1000f,
            bottom = (endTop + endHeight).coerceIn(0f, vh)
        )

        textToolbar.showMenu(
            rect = menuRect,
            onCopyRequested = {
                val txt = engine.getSelectedText()
                if (txt.isNotEmpty()) {
                    clipboardManager.setText(AnnotatedString(txt))
                }
                textToolbar.hide()
            },
            onCutRequested = {
                val txt = engine.getSelectedText()
                if (txt.isNotEmpty()) {
                    clipboardManager.setText(AnnotatedString(txt))
                    engine.deleteSelection()
                }
                textToolbar.hide()
            },
            onPasteRequested = {
                val txt = clipboardManager.getText()?.text
                if (!txt.isNullOrEmpty()) {
                    engine.insertAtCursor(txt)
                }
                textToolbar.hide()
            },
            onSelectAllRequested = {
                engine.selectAll()
            }
        )
    }

    // Clean up text toolbar on dispose
    DisposableEffect(Unit) {
        onDispose {
            textToolbar.hide()
        }
    }

    // Store handle screen coordinates for hit testing & dragging
    var startHandleScreenPos by remember { mutableStateOf(Offset.Zero) }
    var endHandleScreenPos by remember { mutableStateOf(Offset.Zero) }
    var activeDraggingHandle by remember { mutableStateOf<SelectionHandleType?>(null) }

    // Headless IME input coordinator
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
        onTextLayout = { /* Virtualized canvas handles presentation */ },
        decorator = { innerTextField ->
            Box(Modifier.fillMaxSize()) {
                // Headless offscreen IME anchor
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
                    viewportWidthPx = constraints.maxWidth.toFloat()
                    val canvasWidth = constraints.maxWidth

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .scrollable(
                                orientation = Orientation.Vertical,
                                state = scrollableState,
                                flingBehavior = ScrollableDefaults.flingBehavior()
                            )
                            // 1. Handle Selection Handles Dragging
                            .pointerInput(canvasWidth) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val downPos = down.position
                                    val sel = engine.state.selection

                                    if (!sel.collapsed) {
                                        val distStart = (downPos - startHandleScreenPos).getDistance()
                                        val distEnd = (downPos - endHandleScreenPos).getDistance()

                                        if (distStart <= handleTouchRadiusPx) {
                                            activeDraggingHandle = SelectionHandleType.START
                                        } else if (distEnd <= handleTouchRadiusPx) {
                                            activeDraggingHandle = SelectionHandleType.END
                                        }
                                    }

                                    val dragging = activeDraggingHandle
                                    if (dragging != null) {
                                        down.consume()
                                        textToolbar.hide()

                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Main)
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) break
                                            change.consume()

                                            val dragDocY = change.position.y + scrollY
                                            val dragLine = layoutTracker.findLineAt(dragDocY)
                                            val lineStart = lineIndexer.lineStart(dragLine)
                                            val lineLen = lineIndexer.lineLength(dragLine)
                                            val docRev = engine.documentRevision.value
                                            val searchVer = engine.searchEngine.currentIndex
                                            val dragLayout = getOrMeasureLineLayout(
                                                dragLine,
                                                lineStart,
                                                lineLen,
                                                canvasWidth,
                                                docRev,
                                                searchVer
                                            )
                                            val lineTop = layoutTracker.getLineTop(dragLine)
                                            val charInLine = dragLayout.getOffsetForPosition(
                                                Offset(change.position.x, dragDocY - lineTop)
                                            )
                                            val targetOffset = (lineStart + charInLine).coerceIn(
                                                0,
                                                engine.state.text.length
                                            )

                                            val currentMin = engine.state.selection.min
                                            val currentMax = engine.state.selection.max
                                            if (dragging == SelectionHandleType.START) {
                                                engine.state.edit {
                                                    selection = TextRange(
                                                        targetOffset.coerceAtMost(currentMax),
                                                        currentMax
                                                    )
                                                }
                                            } else {
                                                engine.state.edit {
                                                    selection = TextRange(
                                                        currentMin,
                                                        targetOffset.coerceAtLeast(currentMin)
                                                    )
                                                }
                                            }
                                        }

                                        activeDraggingHandle = null
                                        showFloatingToolbar()
                                    }
                                }
                            }
                            // 2. Tap, Double-Tap, and Long-Press Gesture Detection
                            .pointerInput(canvasWidth) {
                                detectTapGestures(
                                    onTap = { offset ->
                                        val docY = offset.y + scrollY
                                        val lineIndex = layoutTracker.findLineAt(docY)
                                        val lineStart = lineIndexer.lineStart(lineIndex)
                                        val lineLen = lineIndexer.lineLength(lineIndex)
                                        val docRev = engine.documentRevision.value
                                        val searchVer = engine.searchEngine.currentIndex
                                        val layoutResult = getOrMeasureLineLayout(
                                            lineIndex,
                                            lineStart,
                                            lineLen,
                                            canvasWidth,
                                            docRev,
                                            searchVer
                                        )
                                        val lineTop = layoutTracker.getLineTop(lineIndex)
                                        val localY = docY - lineTop
                                        val charInLine = layoutResult.getOffsetForPosition(
                                            Offset(offset.x, localY)
                                        )
                                        val targetPos = (lineStart + charInLine).coerceIn(
                                            0,
                                            engine.state.text.length
                                        )

                                        engine.state.edit {
                                            selection = TextRange(targetPos)
                                        }
                                        cursorVisible = true
                                        focusRequester.requestFocus()
                                        keyboardController?.show()
                                        textToolbar.hide()
                                    },
                                    onDoubleTap = { offset ->
                                        val docY = offset.y + scrollY
                                        val lineIndex = layoutTracker.findLineAt(docY)
                                        val lineStart = lineIndexer.lineStart(lineIndex)
                                        val lineLen = lineIndexer.lineLength(lineIndex)
                                        val docRev = engine.documentRevision.value
                                        val searchVer = engine.searchEngine.currentIndex
                                        val layoutResult = getOrMeasureLineLayout(
                                            lineIndex,
                                            lineStart,
                                            lineLen,
                                            canvasWidth,
                                            docRev,
                                            searchVer
                                        )
                                        val lineTop = layoutTracker.getLineTop(lineIndex)
                                        val localY = docY - lineTop
                                        val charInLine = layoutResult.getOffsetForPosition(
                                            Offset(offset.x, localY)
                                        )
                                        val targetPos = (lineStart + charInLine).coerceIn(
                                            0,
                                            engine.state.text.length
                                        )

                                        val wordRange = engine.selectWordAt(targetPos)
                                        engine.state.edit {
                                            selection = wordRange
                                        }
                                        focusRequester.requestFocus()
                                        keyboardController?.show()
                                        showFloatingToolbar()
                                    },
                                    onLongPress = { offset ->
                                        val docY = offset.y + scrollY
                                        val lineIndex = layoutTracker.findLineAt(docY)
                                        val lineStart = lineIndexer.lineStart(lineIndex)
                                        val lineLen = lineIndexer.lineLength(lineIndex)
                                        val docRev = engine.documentRevision.value
                                        val searchVer = engine.searchEngine.currentIndex
                                        val layoutResult = getOrMeasureLineLayout(
                                            lineIndex,
                                            lineStart,
                                            lineLen,
                                            canvasWidth,
                                            docRev,
                                            searchVer
                                        )
                                        val lineTop = layoutTracker.getLineTop(lineIndex)
                                        val localY = docY - lineTop
                                        val charInLine = layoutResult.getOffsetForPosition(
                                            Offset(offset.x, localY)
                                        )
                                        val targetPos = (lineStart + charInLine).coerceIn(
                                            0,
                                            engine.state.text.length
                                        )

                                        val wordRange = engine.selectWordAt(targetPos)
                                        engine.state.edit {
                                            selection = wordRange
                                        }
                                        focusRequester.requestFocus()
                                        keyboardController?.show()
                                        showFloatingToolbar()
                                    }
                                )
                            }
                    ) {
                        val currentScrollY = scrollY
                        val vh = if (viewportHeightPx > 0f) viewportHeightPx else size.height
                        val firstVisible = layoutTracker.findFirstVisible(currentScrollY)
                        val lastVisible = layoutTracker.findLastVisible(currentScrollY + vh)
                        val sel = engine.state.selection
                        val selStart = sel.min.coerceIn(0, engine.state.text.length)
                        val selEnd = sel.max.coerceIn(0, engine.state.text.length)
                        val cursorPos = sel.start.coerceIn(0, engine.state.text.length)
                        val isRangeSelected = (selStart < selEnd)
                        val docRev = engine.documentRevision.value
                        val searchVer = engine.searchEngine.currentIndex

                        withTransform({ translate(left = 0f, top = -currentScrollY) }) {
                            for (lineIndex in firstVisible..lastVisible) {
                                val lineStart = lineIndexer.lineStart(lineIndex)
                                val lineLen = lineIndexer.lineLength(lineIndex)
                                val lineEnd = lineStart + lineLen

                                val layoutResult = getOrMeasureLineLayout(
                                    lineIndex = lineIndex,
                                    lineStart = lineStart,
                                    lineLen = lineLen,
                                    canvasWidth = size.width.toInt(),
                                    docRev = docRev,
                                    searchVer = searchVer
                                )
                                layoutTracker.updateHeight(lineIndex, layoutResult.size.height.toFloat())
                                val lineTopY = layoutTracker.getLineTop(lineIndex)

                                // Draw Selection Highlight
                                if (isRangeSelected) {
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
                                if (cursorVisible && !isRangeSelected && cursorPos in lineStart..lineEnd) {
                                    val offsetInLine = (cursorPos - lineStart).coerceIn(0, lineLen)
                                    val cursorRect = layoutResult.getCursorRect(offsetInLine)
                                    drawRect(
                                        color = colorScheme.primary,
                                        topLeft = Offset(cursorRect.left, lineTopY + cursorRect.top),
                                        size = Size(2.dp.toPx(), cursorRect.height)
                                    )
                                }
                            }

                            // Draw Selection Teardrop Handles
                            if (isRangeSelected) {
                                val startLine = lineIndexer.lineIndexForOffset(selStart)
                                val startLineStart = lineIndexer.lineStart(startLine)
                                val startLineLen = lineIndexer.lineLength(startLine)
                                val startLayout = getOrMeasureLineLayout(
                                    startLine,
                                    startLineStart,
                                    startLineLen,
                                    size.width.toInt(),
                                    docRev,
                                    searchVer
                                )
                                val startLineTop = layoutTracker.getLineTop(startLine)
                                val startRect = startLayout.getCursorRect((selStart - startLineStart).coerceIn(0, startLineLen))
                                val startX = startRect.left
                                val startY = startLineTop + startRect.bottom
                                startHandleScreenPos = Offset(startX, startY - currentScrollY)

                                val endLine = lineIndexer.lineIndexForOffset(selEnd)
                                val endLineStart = lineIndexer.lineStart(endLine)
                                val endLineLen = lineIndexer.lineLength(endLine)
                                val endLayout = getOrMeasureLineLayout(
                                    endLine,
                                    endLineStart,
                                    endLineLen,
                                    size.width.toInt(),
                                    docRev,
                                    searchVer
                                )
                                val endLineTop = layoutTracker.getLineTop(endLine)
                                val endRect = endLayout.getCursorRect((selEnd - endLineStart).coerceIn(0, endLineLen))
                                val endX = endRect.left
                                val endY = endLineTop + endRect.bottom
                                endHandleScreenPos = Offset(endX, endY - currentScrollY)

                                val r = handleVisualRadiusPx

                                // Left Start Teardrop Handle (points up-right)
                                drawCircle(
                                    color = colorScheme.primary,
                                    radius = r,
                                    center = Offset(startX - r, startY + r)
                                )
                                drawRect(
                                    color = colorScheme.primary,
                                    topLeft = Offset(startX - r, startY),
                                    size = Size(r, r)
                                )

                                // Right End Teardrop Handle (points up-left)
                                drawCircle(
                                    color = colorScheme.primary,
                                    radius = r,
                                    center = Offset(endX + r, endY + r)
                                )
                                drawRect(
                                    color = colorScheme.primary,
                                    topLeft = Offset(endX, endY),
                                    size = Size(r, r)
                                )
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
