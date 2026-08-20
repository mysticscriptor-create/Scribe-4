package com.primaloptima.scribe.ui.editor

import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import com.primaloptima.scribe.engine.FormatType
import com.primaloptima.scribe.engine.ScribeEditorEngine
import com.primaloptima.scribe.engine.toSpanStyle

/**
 * High-performance OutputTransformation for Compose 1.12 BasicTextField.
 * Applies rich prose styles (Bold, Italic, Underline, Headings, Quotes) and
 * live search match highlights without mutating the underlying TextFieldState.
 */
class ScribeOutputTransformation(
    private val engine: ScribeEditorEngine,
    private val lineIndex: Int,
    private val colorScheme: ColorScheme,
    private val typography: Typography,
    private val activeHighlightColor: Color = Color(0xFFFFD54F),
    private val normalHighlightColor: Color = Color(0x66FFE082)
) : OutputTransformation {

    override fun TextFieldBuffer.transformOutput() {
        if (length == 0) return

        val lineStart = engine.buffer.lineStart(lineIndex)
        val lineEnd = lineStart + length

        // 1. Apply prose formatting spans for this line
        val spans = engine.formats.spansIn(lineStart, lineEnd)
        for (span in spans) {
            val localStart = (span.start - lineStart).coerceIn(0, length)
            val localEnd = (span.end - lineStart).coerceIn(0, length)
            if (localStart < localEnd) {
                val spanStyle = span.type.toSpanStyle(colorScheme, typography)
                addStyle(spanStyle, localStart, localEnd)
            }
        }

        // 2. Apply search result highlights on this line
        val searchResults = engine.searchEngine.results
        val currentMatchIndex = engine.searchEngine.currentIndex

        for (i in searchResults.indices) {
            val result = searchResults[i]
            if (result.lineIndex == lineIndex) {
                val localStart = result.lineLocalStart.coerceIn(0, length)
                val localEnd = result.lineLocalEnd.coerceIn(0, length)
                if (localStart < localEnd) {
                    val isCurrent = (i == currentMatchIndex)
                    val bgColor = if (isCurrent) activeHighlightColor else normalHighlightColor
                    addStyle(
                        SpanStyle(background = bgColor),
                        localStart,
                        localEnd
                    )
                }
            }
        }
    }
}
