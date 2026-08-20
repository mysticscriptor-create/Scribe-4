package com.primaloptima.scribe.ui.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primaloptima.scribe.R
import com.primaloptima.scribe.engine.ScribeEditorEngine
import kotlinx.coroutines.flow.drop

/**
 * Single paragraph composable.
 *
 * When [isActive] is true (cursor is in this paragraph), renders a live
 * BasicTextField so the user can type. When false, renders a plain Text()
 * composable — extremely cheap, enabling a LazyColumn of thousands of
 * paragraphs without lag.
 *
 * [lineDocStart] is the global document character offset where this paragraph
 * begins. Used so ScribeOutputTransformation knows which spans belong here.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScribeEditorLine(
    lineIndex: Int,
    engine: ScribeEditorEngine,
    textStyle: TextStyle,
    cursorBrush: Brush,
    focusRequester: FocusRequester,
    bringIntoViewRequester: BringIntoViewRequester,
    isActive: Boolean,
    lineDocStart: Int,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val initialContent = remember(lineIndex) { engine.buffer.lineContent(lineIndex) }
    val state = rememberTextFieldState(initialContent)
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val keyboardController = LocalSoftwareKeyboardController.current

    // Sync external changes (Undo, Redo, Find/Replace, Load) into this line's state
    val docRevision = engine.documentRevision.value
    LaunchedEffect(docRevision, lineIndex) {
        val currentEngineText = engine.buffer.lineContent(lineIndex)
        if (state.text.toString() != currentEngineText) {
            state.setTextAndPlaceCursorAtEnd(currentEngineText)
        }
    }

    // Sync user keystrokes back into the DocumentBuffer (only when active)
    LaunchedEffect(state, isActive) {
        if (isActive) {
            snapshotFlow { state.text.toString() }
                .drop(1)
                .collect { newText ->
                    engine.onLineChanged(lineIndex, newText, state.selection.end)
                }
        }
    }

    // Keep toolbar formatting and search commands aligned with the visible
    // field. The engine stores document offsets; this state stores line-local
    // offsets.
    LaunchedEffect(state, isActive) {
        if (isActive) {
            snapshotFlow { state.selection }
                .collect { selection ->
                    engine.updateLineSelection(
                        lineIndex = lineIndex,
                        localStart = selection.start,
                        localEnd = selection.end
                    )
                }
        }
    }

    val lineDocEnd = lineDocStart + engine.buffer.lineLength(lineIndex)

    val outputTransformation = remember(lineIndex, engine, colorScheme, typography, lineDocStart, lineDocEnd) {
        ScribeOutputTransformation(
            engine = engine,
            colorScheme = colorScheme,
            typography = typography,
            lineDocStart = lineDocStart,
            lineDocEnd = lineDocEnd
        )
    }

    val currentLineText = state.text.toString()
    val isSceneSeparator = currentLineText.trim() == "---" || currentLineText.trim() == "***"

    val effectiveTextStyle = textStyle.copy(
        lineHeight = if (textStyle.fontSize.isSp) (textStyle.fontSize.value * 1.55f).sp else textStyle.lineHeight
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
    ) {
        if (isActive) {
            // ── Active paragraph: full BasicTextField with keyboard, cursor, IME ──
            BasicTextField(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) keyboardController?.show()
                    }
                    .onPreviewKeyEvent { keyEvent ->
                        handleLineKeyEvent(keyEvent, lineIndex, state, engine)
                    },
                textStyle = effectiveTextStyle,
                cursorBrush = cursorBrush,
                 lineLimits = TextFieldLineLimits.SingleLine,
                outputTransformation = outputTransformation,
                inputTransformation = ScribeInputTransformation,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    autoCorrectEnabled = true,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Default
                )
            )
        } else {
            // ── Inactive paragraph: plain Text(), zero IME cost ──
            val annotatedText = remember(currentLineText, lineDocStart, docRevision) {
                AnnotatedString(currentLineText)
            }
            Text(
                text = annotatedText,
                style = effectiveTextStyle,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onActivate)
            )
        }

        // Scene separator decoration (--- or ***)
        if (isSceneSeparator) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                Spacer(Modifier.width(12.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_scribe_s),
                    contentDescription = null,
                    tint = colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(12.dp))
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

private fun handleLineKeyEvent(
    event: KeyEvent,
    lineIndex: Int,
    state: TextFieldState,
    engine: ScribeEditorEngine
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false

    return when (event.key) {
        Key.Enter -> {
            val cursor = state.selection.end
            engine.onLineInserted(
                afterLineIndex = lineIndex,
                splitAt = cursor,
                currentLineText = state.text.toString()
            )
            true
        }
        Key.Backspace -> {
            if (state.selection.start == 0 && state.selection.end == 0 && lineIndex > 0) {
                engine.onLineMerge(lineIndex)
                true
            } else {
                false
            }
        }
        Key.DirectionUp -> {
            if (lineIndex > 0 && state.selection.start == 0) {
                engine.requestLineFocus(lineIndex - 1)
                true
            } else {
                false
            }
        }
        Key.DirectionDown -> {
            val totalLines = engine.buffer.lineCount()
            if (lineIndex < totalLines - 1 && state.selection.end >= state.text.length) {
                engine.requestLineFocus(lineIndex + 1)
                true
            } else {
                false
            }
        }
        else -> false
    }
}
