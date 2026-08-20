package com.primaloptima.scribe.ui.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.primaloptima.scribe.engine.ScribeEditorEngine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Pure Jetpack Compose Prose Editor container.
 * Virtualizes lines via LazyColumn and eliminates all AndroidView / Sora gesture interop conflicts.
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
    val scope = rememberCoroutineScope()

    // Store FocusRequesters & BringIntoViewRequesters keyed by line index
    val focusRequesters = remember { mutableMapOf<Int, FocusRequester>() }
    val bringIntoViewRequesters = remember { mutableMapOf<Int, BringIntoViewRequester>() }

    // Respond to cross-line focus & jump requests (outline jump, enter split, merge)
    LaunchedEffect(engine) {
        engine.focusRequests.collectLatest { request ->
            val targetLine = request.lineIndex.coerceIn(0, (engine.buffer.lineCount() - 1).coerceAtLeast(0))
            scope.launch {
                listState.animateScrollToItem(targetLine)
            }
            try {
                bringIntoViewRequesters[targetLine]?.bringIntoView()
                focusRequesters[targetLine]?.requestFocus()
            } catch (_: Exception) {
                // Requester might not be composed yet
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(
            count = maxOf(1, lineCount),
            key = { index -> "scribe_line_$index" }
        ) { lineIndex ->
            val focusRequester = focusRequesters.getOrPut(lineIndex) { FocusRequester() }
            val bringIntoViewRequester = bringIntoViewRequesters.getOrPut(lineIndex) { BringIntoViewRequester() }

            ScribeEditorLine(
                lineIndex = lineIndex,
                engine = engine,
                textStyle = textStyle,
                cursorBrush = cursorBrush,
                focusRequester = focusRequester,
                bringIntoViewRequester = bringIntoViewRequester
            )
        }
    }
}
