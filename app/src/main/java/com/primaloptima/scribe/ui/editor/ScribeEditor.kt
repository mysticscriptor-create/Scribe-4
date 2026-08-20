package com.primaloptima.scribe.ui.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primaloptima.scribe.engine.ScribeEditorEngine
import kotlinx.coroutines.flow.collectLatest

/**
 * Unified High-Performance Scribe Prose Editor.
 *
 * Implements a single, persistent InputConnection canvas directly on Compose BasicTextField:
 * - Single unbroken IME lifecycle: soft keyboard never stutters, closes, or flickers on tap or line change.
 * - Continuous full-document multi-line text selection: handles drag seamlessly across arbitrary lines & paragraphs.
 * - Pixel-accurate tap-to-cursor placement: native text layout resolving touch offsets directly to document characters.
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
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    val docRevision = engine.documentRevision.value

    val outputTransformation = remember(engine, colorScheme, typography, docRevision) {
        ScribeOutputTransformation(
            engine = engine,
            colorScheme = colorScheme,
            typography = typography
        )
    }

    val effectiveTextStyle = textStyle.copy(
        lineHeight = if (textStyle.fontSize.isSp) (textStyle.fontSize.value * 1.55f).sp else textStyle.lineHeight
    )

    // Handle programmatic focus requests (from search matches, outline jump, undo/redo)
    LaunchedEffect(engine) {
        engine.focusRequests.collectLatest {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                focusRequester.requestFocus()
                keyboardController?.show()
            }
            .padding(contentPadding)
    ) {
        BasicTextField(
            state = engine.state,
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester),
            textStyle = effectiveTextStyle,
            cursorBrush = cursorBrush,
            scrollState = scrollState,
            lineLimits = TextFieldLineLimits.Default,
            outputTransformation = outputTransformation,
            inputTransformation = ScribeInputTransformation,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                autoCorrectEnabled = true,
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Default
            ),
            decorator = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 400.dp)
                        .padding(bottom = 200.dp)
                ) {
                    innerTextField()
                }
            }
        )
    }
}
