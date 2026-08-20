package com.primaloptima.scribe.ui.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.layout.rememberLazyListPrefetchState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.primaloptima.scribe.engine.ScribeEditorEngine
import kotlinx.coroutines.flow.collectLatest

/**
 * Pure Jetpack Compose Prose Editor container.
 * Features:
 * - LazyColumn line/paragraph virtualization for buttery smooth 100k+ word documents (§6.1).
 * - Focus management across virtualized lines via FocusRequester and BringIntoViewRequester.
 * - ScribeEditorLine invocation per line with smart quote transformation, rich spans, and scene breaks.
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
    val lineCount by engine.lineCount
    val listState = rememberLazyListState()
    val prefetchState = rememberLazyListPrefetchState()
    val focusRequesters = remember { mutableMapOf<Int, FocusRequester>() }
    val bringIntoViewRequesters = remember { mutableMapOf<Int, BringIntoViewRequester>() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Respond to focus requests (such as outline jump, search match jump, line navigation)
    LaunchedEffect(engine) {
        engine.focusRequests.collectLatest { request ->
            try {
                val total = engine.buffer.lineCount()
                val targetLine = request.lineIndex.coerceIn(0, (total - 1).coerceAtLeast(0))
                listState.animateScrollToItem(targetLine)
                val requester = focusRequesters[targetLine]
                requester?.requestFocus()
                keyboardController?.show()
            } catch (_: Exception) {
            }
        }
    }

    LazyColumn(
        state = listState,
        prefetchState = prefetchState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        items(
            count = lineCount,
            key = { index -> "line_$index" }
        ) { lineIndex ->
            val focusRequester = remember(lineIndex) {
                focusRequesters.getOrPut(lineIndex) { FocusRequester() }
            }
            val bringIntoViewRequester = remember(lineIndex) {
                bringIntoViewRequesters.getOrPut(lineIndex) { BringIntoViewRequester() }
            }

            ScribeEditorLine(
                lineIndex = lineIndex,
                engine = engine,
                textStyle = textStyle,
                cursorBrush = cursorBrush,
                focusRequester = focusRequester,
                bringIntoViewRequester = bringIntoViewRequester
            )
        }

        item(key = "bottom_breathing_room") {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 240.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                if (lineCount > 0) {
                                    engine.requestLineFocus(lineCount - 1)
                                    keyboardController?.show()
                                }
                            }
                        )
                    }
            )
        }
    }
}
