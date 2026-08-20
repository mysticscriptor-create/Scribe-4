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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.ui.unit.sp
import com.primaloptima.scribe.engine.ScribeEditorEngine
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

/**
 * Pure Jetpack Compose Prose Editor container.
 * Features:
 * - Full-surface tap detection: clicking anywhere in the editor immediately focuses and shows keyboard.
 * - Long-press floating context menu: quick Paste, Select All, Copy, Cut anywhere in the editor.
 * - Full multi-line prose typography with real-time formatting spans & live search highlights.
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
    val scrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val clipboardManager = LocalClipboardManager.current
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val density = LocalDensity.current

    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuOffset by remember { mutableStateOf(Offset.Zero) }

    // Respond to focus requests (such as outline jump, search match jump)
    LaunchedEffect(engine) {
        engine.focusRequests.collectLatest { request ->
            try {
                focusRequester.requestFocus()
                keyboardController?.show()
                if (request.targetOffset >= 0 && engine.textFieldState.text.isNotEmpty()) {
                    val progress = request.targetOffset.toFloat() / engine.textFieldState.text.length.toFloat()
                    val targetScroll = (scrollState.maxValue * progress).toInt()
                    scrollState.animateScrollTo(targetScroll)
                }
            } catch (_: Exception) {
            }
        }
    }

    val outputTransformation = remember(engine, colorScheme, typography) {
        ScribeOutputTransformation(
            engine = engine,
            colorScheme = colorScheme,
            typography = typography
        )
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
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 480.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            keyboardController?.show()
                        }
                    },
                textStyle = textStyle.copy(
                    lineHeight = if (textStyle.fontSize.isSp) (textStyle.fontSize.value * 1.55f).sp else textStyle.lineHeight
                ),
                cursorBrush = cursorBrush,
                lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 1),
                outputTransformation = outputTransformation,
                inputTransformation = ScribeInputTransformation
            )

            // Extra generous bottom breathing room for distraction-free typing
            Spacer(modifier = Modifier.defaultMinSize(minHeight = 240.dp))
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

                    // 1. Paste Button
                    EditorMenuAction(
                        icon = Icons.Default.ContentPaste,
                        label = "Paste",
                        enabled = hasClipboard,
                        onClick = {
                            showContextMenu = false
                            val clipText = clipboardManager.getText()?.text
                            if (!clipText.isNullOrEmpty()) {
                                engine.textFieldState.edit {
                                    val s = activeSelection.min
                                    val e = activeSelection.max
                                    replace(s, e, clipText)
                                    this.selection = TextRange(s + clipText.length)
                                }
                                engine.notifyMutation()
                            }
                            focusRequester.requestFocus()
                            keyboardController?.show()
                        }
                    )

                    // 2. Select All Button
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
                                focusRequester.requestFocus()
                                keyboardController?.show()
                            }
                        )
                    }

                    // 3. Copy Button (when text is selected)
                    if (hasSelection) {
                        EditorMenuAction(
                            icon = Icons.Default.ContentCopy,
                            label = "Copy",
                            enabled = true,
                            onClick = {
                                showContextMenu = false
                                val selectedText = engine.textFieldState.text.substring(
                                    activeSelection.min,
                                    activeSelection.max
                                )
                                clipboardManager.setText(AnnotatedString(selectedText))
                            }
                        )

                        // 4. Cut Button (when text is selected)
                        EditorMenuAction(
                            icon = Icons.Default.ContentCut,
                            label = "Cut",
                            enabled = true,
                            onClick = {
                                showContextMenu = false
                                val selectedText = engine.textFieldState.text.substring(
                                    activeSelection.min,
                                    activeSelection.max
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
