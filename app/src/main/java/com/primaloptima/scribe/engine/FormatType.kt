package com.primaloptima.scribe.engine

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.isSp
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp

enum class FormatType {
    BOLD,
    ITALIC,
    UNDERLINE,
    STRIKETHROUGH,
    H1,
    H2,
    H3,
    BLOCKQUOTE,
    SCENE_SEPARATOR;

    fun isHeading(): Boolean = this == H1 || this == H2 || this == H3

    fun headingLevel(): Int = when (this) {
        H1 -> 1
        H2 -> 2
        H3 -> 3
        else -> 0
    }

    fun toSpanStyle(colorScheme: ColorScheme? = null, typography: Typography? = null): SpanStyle = when (this) {
        BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
        ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
        UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
        STRIKETHROUGH -> SpanStyle(textDecoration = TextDecoration.LineThrough)
        H1 -> SpanStyle(
            fontSize = typography?.headlineLarge?.fontSize ?: 28.sp,
            fontWeight = FontWeight.Bold,
            color = colorScheme?.primary ?: Color.Unspecified
        )
        H2 -> SpanStyle(
            fontSize = typography?.headlineMedium?.fontSize ?: 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme?.primary ?: Color.Unspecified
        )
        H3 -> SpanStyle(
            fontSize = typography?.headlineSmall?.fontSize ?: 18.sp,
            fontWeight = FontWeight.Medium,
            color = colorScheme?.primary ?: Color.Unspecified
        )
        BLOCKQUOTE -> SpanStyle(
            color = colorScheme?.onSurfaceVariant ?: Color.Unspecified,
            fontStyle = FontStyle.Italic
        )
        SCENE_SEPARATOR -> SpanStyle()
    }
}

fun spanStyleToFormatType(style: SpanStyle): FormatType? {
    return when {
        style.fontWeight == FontWeight.Bold && style.fontSize.isUnspecified -> FormatType.BOLD
        style.fontStyle == FontStyle.Italic && style.color == Color.Unspecified -> FormatType.ITALIC
        style.textDecoration == TextDecoration.Underline || style.textDecoration?.contains(TextDecoration.Underline) == true -> FormatType.UNDERLINE
        style.textDecoration == TextDecoration.LineThrough || style.textDecoration?.contains(TextDecoration.LineThrough) == true -> FormatType.STRIKETHROUGH
        style.fontSize.isSp && style.fontSize.value in 26f..36f -> FormatType.H1
        style.fontSize.isSp && style.fontSize.value in 20f..25f -> FormatType.H2
        style.fontSize.isSp && style.fontSize.value in 16f..19f && style.fontWeight == FontWeight.Medium -> FormatType.H3
        style.fontStyle == FontStyle.Italic -> FormatType.BLOCKQUOTE
        else -> null
    }
}
