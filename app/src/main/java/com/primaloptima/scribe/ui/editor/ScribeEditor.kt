package com.primaloptima.scribe.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.primaloptima.scribe.engine.FormatType
import com.primaloptima.scribe.engine.ScribeEditorEngine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Unified High-Performance Prose Editor.
 *
 * Implements a single persistent InputConnection via Compose Foundation BasicTextField,
 * delivering Sora Editor-level speed with full multi-paragraph selection, instant
 * reactivity, zero keyboard churn, and interval-bounded output transformations.
 */
@Composable
fun ScribeEditor(
    engine: ScribeEditorEngine,
    textStyle: TextStyle,
    cursorBrush: Brush,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
) {
    val scrollState = rememberScrollState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val clipboardManager = LocalClipboardManager.current
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val focusRequester = remember { FocusRequester() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuOffset by remember { mutableStateOf(Offset.Zero) }

    // High-performance output transformation for prose formatting & search highlights
    val outputTransformation = remember(engine, colorScheme, typography) {
        ScribeOutputTransformation(engine, colorScheme, typography)
    }

    // Smart markdown & typography input transformation
    val inputTransformation = remember { ScribeInputTransformation() }

    // Listen for navigation requests (Search jumps, Outline jumps)
    LaunchedEffect(engine) {
        engine.focusRequests.collectLatest { request ->
            try {
                focusRequester.requestFocus()
                keyboardController?.show()
                // Approximate scroll position based on line ratio
                val totalLines = (engine.buffer.lineCount()).coerceAtLeast(1)
                val targetFraction = request.lineIndex.toFloat() / totalLines.toFloat()
                val targetScrollY = (scrollState.maxValue * targetFraction).toInt().coerceIn(0, scrollState.maxValue)
                scrollState.animateScrollTo(targetScrollY)
                bringIntoViewRequester.bringIntoView()
            } catch (_: Exception) {}
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        showContextMenu = false
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    },
                    onLongPress = { pressOffset ->
                        contextMenuOffset = pressOffset
                        showContextMenu = true
                        focusRequester.requestFocus()
                    }
                )
            }
    ) {
        val maxBoxWidth = maxWidth

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(contentPadding)
        ) {
            BasicTextField(
                state = engine.textFieldState,
                textStyle = textStyle,
                cursorBrush = cursorBrush,
                lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 1),
                outputTransformation = outputTransformation,
                inputTransformation = inputTransformation,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    autoCorrectEnabled = true,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Default
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 400.dp)
                    .focusRequester(focusRequester)
                    .bringIntoViewRequester(bringIntoViewRequester)
            )

            // Generous breathing room so the last lines stay comfortably above the IME keyboard
            Spacer(modifier = Modifier.defaultMinSize(minHeight = 260.dp))
        }

        // Floating Context Menu (Paste, Select All, Copy, Cut, Bold, Italic)
        AnimatedVisibility(
            visible = showContextMenu,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
            modifier = Modifier.wrapContentSize()
        ) {
            val popupX = with(density) {
                contextMenuOffset.x.coerceIn(16.dp.toPx(), (maxBoxWidth - 260.dp).toPx().coerceAtLeast(16.dp.toPx()))
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
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                            }
                            focusRequester.requestFocus()
                            keyboardController?.show()
                        }
                    )

                    if (textLength > 0) {
                        EditorMenuAction(
                            icon = Icons.Default.SelectAll,
                            label = "All",
                            enabled = true,
                            onClick = {
                                showContextMenu = false
                                engine.textFieldState.edit {
                                    this.selection = TextRange(0, textLength)
                                }
                                focusRequester.requestFocus()
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
                            }
                        )

                        EditorMenuAction(
                            icon = Icons.Default.FormatBold,
                            label = "Bold",
                            enabled = true,
                            onClick = {
                                showContextMenu = false
                                engine.toggleFormat(FormatType.BOLD)
                            }
                        )

                        EditorMenuAction(
                            icon = Icons.Default.FormatItalic,
                            label = "Italic",
                            enabled = true,
                            onClick = {
                                showContextMenu = false
                                engine.toggleFormat(FormatType.ITALIC)
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
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(16.dp),
            tint = if (enabled) colorScheme.primary else colorScheme.onSurface.copy(alpha = 0.38f)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) colorScheme.onSurface else colorScheme.onSurface.copy(alpha = 0.38f)
        )
    }
}
