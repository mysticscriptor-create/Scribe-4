package com.primaloptima.scribe.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.rememberBringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.primaloptima.scribe.engine.ScribeEditorEngine
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

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
    val clipboardManager = LocalClipboardManager.current
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current

    // Track which line the cursor is in
    var activeLineIndex by remember { mutableIntStateOf(0) }

    // One FocusRequester per visible line is too expensive.
    // Use a single one; it always points at the active line's BasicTextField.
    val activeFocusRequester = remember { FocusRequester() }
    val activeBringIntoViewRequester = rememberBringIntoViewRequester()

    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuOffset by remember { mutableStateOf(Offset.Zero) }

    // Watch for focus requests from engine (outline jumps, search jumps)
    LaunchedEffect(engine) {
        engine.focusRequests.collectLatest { request ->
            try {
                activeLineIndex = request.lineIndex
                activeFocusRequester.requestFocus()
                keyboardController?.show()
                // Scroll the LazyColumn to the target line
                listState.animateScrollToItem(
                    index = request.lineIndex.coerceIn(0, (engine.buffer.lineCount() - 1).coerceAtLeast(0)),
                    scrollOffset = 0
                )
            } catch (_: Exception) {}
        }
    }

    val lineCount = engine.lineCount.value

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        showContextMenu = false
                        activeFocusRequester.requestFocus()
                        keyboardController?.show()
                    },
                    onLongPress = { pressOffset ->
                        contextMenuOffset = pressOffset
                        showContextMenu = true
                        activeFocusRequester.requestFocus()
                    }
                )
            }
    ) {
        val maxBoxWidth = maxWidth

        LazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                count = lineCount.coerceAtLeast(1),
                key = { index -> index }   // stable keys prevent unnecessary recomposition
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(lineIndex) {
                            detectTapGestures(
                                onTap = {
                                    // When user taps a different paragraph, switch active line
                                    activeLineIndex = lineIndex
                                    activeFocusRequester.requestFocus()
                                    keyboardController?.show()
                                    showContextMenu = false
                                }
                            )
                        }
                )
            }

            // Generous bottom padding so the last line isn't hidden behind the keyboard
            item {
                Spacer(modifier = Modifier.defaultMinSize(minHeight = 240.dp))
            }
        }

        // Floating Context Menu (Paste, Select All, Copy, Cut)
        AnimatedVisibility(
            visible = showContextMenu,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
            modifier = Modifier.wrapContentSize()
        ) {
            val popupX = with(density) {
                contextMenuOffset.x.coerceIn(16.dp.toPx(), (maxBoxWidth - 220.dp).toPx().coerceAtLeast(16.dp.toPx()))
            }
            val popupY = with(density) {
                (contextMenuOffset.y - 64.dp.toPx()).coerceAtLeast(16.dp.toPx())
            }

            Surface(
                modifier = Modifier
                    .offset { IntOffset(popupX.roundToInt(), popupY.roundToInt()) }
                    .shadow(12.dp, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                color = colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val hasClipboard = clipboardManager.hasText()
                    val activeSelection = engine.textFieldState.selection
                    val hasSelection = activeSelection.min < activeSelection.max
                    val textLength = engine.textFieldState.text.length

                    EditorMenuAction(
                        icon = Icons.Default.ContentPaste,
                        label = "Paste",
                        enabled = hasClipboard,
                        onClick = {
                            showContextMenu = false
                            val clipText = clipboardManager.getText()?.text
                            if (!clipText.isNullOrEmpty()) {
                                engine.insertAtCursor(clipText)
                                engine.notifyMutation()
                            }
                            activeFocusRequester.requestFocus()
                            keyboardController?.show()
                        }
                    )

                    if (textLength > 0) {
                        EditorMenuAction(
                            icon = Icons.Default.SelectAll,
                            label = "Select All",
                            enabled = true,
                            onClick = {
                                showContextMenu = false
                                engine.textFieldState.edit {
                                    this.selection = TextRange(0, textLength)
                                }
                                activeFocusRequester.requestFocus()
                                keyboardController?.show()
                            }
                        )
                    }

                    if (hasSelection) {
                        EditorMenuAction(
                            icon = Icons.Default.ContentCopy,
                            label = "Copy",
                            enabled = true,
                            onClick = {
                                showContextMenu = false
                                val selectedText = engine.textFieldState.text.substring(
                                    activeSelection.min, activeSelection.max
                                )
                                clipboardManager.setText(AnnotatedString(selectedText))
                            }
                        )

                        EditorMenuAction(
                            icon = Icons.Default.ContentCut,
                            label = "Cut",
                            enabled = true,
                            onClick = {
                                showContextMenu = false
                                val selectedText = engine.textFieldState.text.substring(
                                    activeSelection.min, activeSelection.max
                                )
                                clipboardManager.setText(AnnotatedString(selectedText))
                                engine.textFieldState.edit {
                                    replace(activeSelection.min, activeSelection.max, "")
                                    this.selection = TextRange(activeSelection.min)
                                }
                                engine.notifyMutation()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorMenuAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    TextButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(18.dp),
            tint = if (enabled) colorScheme.primary else colorScheme.onSurface.copy(alpha = 0.38f)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) colorScheme.onSurface else colorScheme.onSurface.copy(alpha = 0.38f)
        )
    }
}
