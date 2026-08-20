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
 * OutputTransformation scoped to a single paragraph/line.
 *
 * [lineDocStart] and [lineDocEnd] are the global document character positions
 * of this paragraph (not including the trailing newline). Only spans and search
 * results that overlap this range are applied, translated to local coordinates.
 */
class ScribeOutputTransformation(
    private val engine: ScribeEditorEngine,
    private val colorScheme: ColorScheme,
    private val typography: Typography,
    private val lineDocStart: Int = 0,
    private val lineDocEnd: Int = Int.MAX_VALUE,
    private val activeHighlightColor: Color = Color(0xFFFFD54F),
    private val normalHighlightColor: Color = Color(0x66FFE082)
) : OutputTransformation {

    override fun TextFieldBuffer.transformOutput() {
        if (length == 0) return

        // 1. Apply prose formatting spans (only those overlapping this line)
        val spans = engine.formats.spansIn(lineDocStart, lineDocEnd)
        for (span in spans) {
            // Skip spans that don't overlap this paragraph
            if (span.end <= lineDocStart || span.start >= lineDocEnd) continue

            // Translate to local (paragraph-relative) coordinates
            val localStart = (span.start - lineDocStart).coerceIn(0, length)
            val localEnd = (span.end - lineDocStart).coerceIn(0, length)
            if (localStart < localEnd) {
                val spanStyle = span.type.toSpanStyle(colorScheme, typography)
                addStyle(spanStyle, localStart, localEnd)
            }
        }

        // 2. Apply search result highlights (only those overlapping this line)
        val searchResults = engine.searchEngine.results
        val currentMatchIndex = engine.searchEngine.currentIndex

        for (i in searchResults.indices) {
            val result = searchResults[i]
            val resultEnd = result.docOffset + result.matchLength

            // Skip results that don't overlap this paragraph
            if (resultEnd <= lineDocStart || result.docOffset >= lineDocEnd) continue

            val localStart = (result.docOffset - lineDocStart).coerceIn(0, length)
            val localEnd = (resultEnd - lineDocStart).coerceIn(0, length)
            if (localStart < localEnd) {
                val isCurrent = (i == currentMatchIndex)
                val bgColor = if (isCurrent) activeHighlightColor else normalHighlightColor
                addStyle(SpanStyle(background = bgColor), localStart, localEnd)
            }
        }
    }
}
