package com.primaloptima.scribe.ui.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalLayoutDirection
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
 * Custom BringIntoViewSpec for Scribe editor:
 * - If the focused line/cursor is already visible anywhere within the viewport, do NOT scroll (distance = 0).
 *   This ensures tapping on text to place the cursor leaves the line exactly where it is.
 * - If the cursor is obscured below the visible viewport (e.g. by keyboard), scroll minimally just enough
 *   to reveal it above the keyboard edge without jumping to the top of the screen.
 * - If the cursor is above the visible viewport, scroll down just enough to reveal it.
 */
@OptIn(ExperimentalFoundationApi::class)
private val EditorBringIntoViewSpec = object : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float
    ): Float {
        val trailingEdge = offset + size
        val leadingEdge = offset

        // 1. If child is larger than container, align to top
        if (leadingEdge < 0f && trailingEdge > containerSize) {
            return leadingEdge
        }

        // 2. If child is already fully visible within the container bounds, do not scroll!
        if (leadingEdge >= 0f && trailingEdge <= containerSize) {
            return 0f
        }

        // 3. If child is above the top edge, scroll down just enough to reveal it
        if (leadingEdge < 0f) {
            return leadingEdge
        }

        // 4. If child is below the bottom edge, scroll up just enough to reveal it
        if (trailingEdge > containerSize) {
            return trailingEdge - containerSize
        }

        return 0f
    }
}

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
    val layoutDirection = LocalLayoutDirection.current

    val outputTransformation = remember(engine, colorScheme, typography) {
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
            keyboardController?.show()
        }
    }

    CompositionLocalProvider(LocalBringIntoViewSpec provides EditorBringIntoViewSpec) {
        Box(
            modifier = modifier.fillMaxSize()
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
                            .padding(
                                start = contentPadding.calculateStartPadding(layoutDirection),
                                end = contentPadding.calculateEndPadding(layoutDirection),
                                top = contentPadding.calculateTopPadding(),
                                bottom = contentPadding.calculateBottomPadding() + 200.dp
                            )
                    ) {
                        innerTextField()
                    }
                }
            )
        }
    }
}
