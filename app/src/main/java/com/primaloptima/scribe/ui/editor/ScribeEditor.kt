package com.primaloptima.scribe.ui.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.rememberBringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.primaloptima.scribe.engine.ScribeEditorEngine
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.collectLatest

/**
 * Virtualized Prose Editor — LazyColumn of paragraphs.
 *
 * Only the paragraph the cursor is in uses a live BasicTextField.
 * Every other paragraph renders as a plain Text() composable.
 * This gives RecyclerView-style virtualization to the editor, making
 * 50,000+ word documents smooth on even low-end Android devices.
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
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current

    // Track which line the cursor is in
    var activeLineIndex by remember { mutableIntStateOf(0) }

    // One FocusRequester per visible line is too expensive.
    // Use a single one; it always points at the active line's BasicTextField.
    val activeFocusRequester = remember { FocusRequester() }
    val activeBringIntoViewRequester = rememberBringIntoViewRequester()

    // Watch for focus requests from engine (outline jumps, search jumps)
    LaunchedEffect(engine) {
        engine.focusRequests.collectLatest { request ->
            activeLineIndex = request.lineIndex
        }
    }

    // A lazy item must be composed before its FocusRequester can work.
    LaunchedEffect(activeLineIndex) {
        val target = activeLineIndex.coerceIn(
            0,
            (engine.buffer.lineCount() - 1).coerceAtLeast(0)
        )
        listState.scrollToItem(target)
        withFrameNanos { }
        activeFocusRequester.requestFocus()
        keyboardController?.show()
    }

    val lineCount = engine.lineCount.value

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                count = lineCount.coerceAtLeast(1),
                key = { index -> engine.buffer.lineKey(index) }
            ) { lineIndex ->
                // Pre-compute this paragraph's global document start offset
                val lineDocStart = remember(lineIndex, engine.documentRevision.value) {
                    engine.buffer.lineStart(lineIndex)
                }

                ScribeEditorLine(
                    lineIndex = lineIndex,
                    engine = engine,
                    textStyle = textStyle,
                    cursorBrush = cursorBrush,
                    focusRequester = if (lineIndex == activeLineIndex) activeFocusRequester
                                     else remember { FocusRequester() },
                    bringIntoViewRequester = if (lineIndex == activeLineIndex) activeBringIntoViewRequester
                                             else rememberBringIntoViewRequester(),
                    isActive = (lineIndex == activeLineIndex),
                    lineDocStart = lineDocStart,
                    onActivate = {
                        activeLineIndex = lineIndex
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Generous bottom padding so the last line isn't hidden behind the keyboard
            item {
                Spacer(modifier = Modifier.defaultMinSize(minHeight = 240.dp))
            }
        }

    }
}
