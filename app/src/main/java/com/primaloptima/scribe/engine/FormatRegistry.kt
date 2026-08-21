package com.primaloptima.scribe.engine

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

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
}

data class FormatSpan(
    val type: FormatType,
    var start: Int,   // inclusive document offset
    var end: Int      // exclusive document offset
) {
    fun toSerializedSpan(): SerializedSpan = SerializedSpan(type.name, start, end)
}

sealed class FormatEdit {
    data class Added(val span: FormatSpan) : FormatEdit()
    data class Removed(val span: FormatSpan) : FormatEdit()
    data class Modified(val before: List<FormatSpan>, val after: List<FormatSpan>) : FormatEdit()
    data class Compound(val edits: List<FormatEdit>) : FormatEdit()
}

/**
 * Manages rich prose formatting spans keyed to document character offsets.
 * Automatically adjusts span boundaries on insertions and deletions.
 */
class FormatRegistry {
    private val spans = mutableStateListOf<FormatSpan>()

    fun exportAll(): List<FormatSpan> = spans.map { it.copy() }

    fun loadAll(newSpans: List<FormatSpan>) {
        spans.clear()
        spans.addAll(newSpans.map { it.copy() })
    }

    fun addSpan(span: FormatSpan): FormatEdit {
        if (span.start >= span.end && span.type != FormatType.SCENE_SEPARATOR) {
            return FormatEdit.Compound(emptyList())
        }
        val before = exportAll()
        // If heading type, remove existing headings that overlap
        if (span.type.isHeading()) {
            spans.removeAll { it.type.isHeading() && it.start < span.end && it.end > span.start }
        }
        spans.add(span.copy())
        val after = exportAll()
        return FormatEdit.Modified(before, after)
    }

    fun removeSpan(type: FormatType, selStart: Int, selEnd: Int): FormatEdit {
        val before = exportAll()
        spans.removeAll { it.type == type && it.start >= selStart && it.end <= selEnd }
        val after = exportAll()
        return FormatEdit.Modified(before, after)
    }

    /**
     * Toggles a format across [selStart, selEnd).
     * If the range is already fully covered by the format, it removes the format.
     * Otherwise, it adds the format across the range.
     */
    fun toggleSpan(type: FormatType, selStart: Int, selEnd: Int): FormatEdit {
        val s = minOf(selStart, selEnd)
        val e = maxOf(selStart, selEnd)
        if (s == e && type != FormatType.SCENE_SEPARATOR) return FormatEdit.Compound(emptyList())
        val before = exportAll()
        val matching = spans.filter { it.type == type && it.start <= s && it.end >= e }
        if (matching.isNotEmpty()) {
            // Already fully active -> turn off
            spans.removeAll(matching.toSet())
        } else {
            // If heading, remove other headings on this line
            if (type.isHeading()) {
                spans.removeAll { it.type.isHeading() && it.start < e && it.end > s }
            }
            spans.add(FormatSpan(type, s, e))
        }
        val after = exportAll()
        return FormatEdit.Modified(before, after)
    }

    fun spansIn(start: Int, end: Int): List<FormatSpan> {
        val s = minOf(start, end)
        val e = maxOf(start, end)
        if (spans.isEmpty() || s >= e) return emptyList()
        val result = ArrayList<FormatSpan>(4)
        for (i in 0 until spans.size) {
            val span = spans[i]
            if (span.start < e && span.end > s) {
                result.add(span)
            }
        }
        return result
    }

    fun adjustForInsert(pos: Int, insertedLength: Int) {
        if (insertedLength <= 0 || spans.isEmpty()) return
        for (i in 0 until spans.size) {
            val span = spans[i]
            when {
                span.start >= pos -> spans[i] = span.copy(
                    start = span.start + insertedLength,
                    end = span.end + insertedLength
                )
                span.end > pos -> spans[i] = span.copy(end = span.end + insertedLength)
            }
        }
    }

    fun adjustForDelete(start: Int, deletedLength: Int) {
        if (deletedLength <= 0) return
        val end = start + deletedLength
        var i = 0
        while (i < spans.size) {
            val span = spans[i]
            when {
                span.start >= end -> {
                    spans[i] = span.copy(
                        start = span.start - deletedLength,
                        end = span.end - deletedLength
                    )
                    i++
                }
                span.end <= start -> i++
                span.start >= start && span.end <= end -> {
                    spans.removeAt(i)
                }
                span.start < start && span.end > end -> {
                    spans[i] = span.copy(end = span.end - deletedLength)
                    i++
                }
                span.start < start && span.end <= end -> {
                    val updated = span.copy(end = start)
                    if (updated.start >= updated.end && updated.type != FormatType.SCENE_SEPARATOR) {
                        spans.removeAt(i)
                    } else {
                        spans[i] = updated
                        i++
                    }
                }
                else -> {
                    val updated = span.copy(start = start, end = span.end - deletedLength)
                    if (updated.start >= updated.end && updated.type != FormatType.SCENE_SEPARATOR) {
                        spans.removeAt(i)
                    } else {
                        spans[i] = updated
                        i++
                    }
                }
            }
        }
    }

    fun applyFormatEdit(edit: FormatEdit) {
        when (edit) {
            is FormatEdit.Added -> spans.add(edit.span.copy())
            is FormatEdit.Removed -> spans.remove(edit.span)
            is FormatEdit.Modified -> {
                spans.clear()
                spans.addAll(edit.after.map { it.copy() })
            }
            is FormatEdit.Compound -> {
                for (subEdit in edit.edits) {
                    applyFormatEdit(subEdit)
                }
            }
        }
    }

    fun invertFormatEdit(edit: FormatEdit) {
        when (edit) {
            is FormatEdit.Added -> spans.remove(edit.span)
            is FormatEdit.Removed -> spans.add(edit.span.copy())
            is FormatEdit.Modified -> {
                spans.clear()
                spans.addAll(edit.before.map { it.copy() })
            }
            is FormatEdit.Compound -> {
                for (subEdit in edit.edits.reversed()) {
                    invertFormatEdit(subEdit)
                }
            }
        }
    }

    fun toAnnotatedString(
        lineStart: Int,
        lineEnd: Int,
        text: String,
        colorScheme: ColorScheme,
        typography: Typography
    ): AnnotatedString {
        return buildAnnotatedString {
            append(text)
            spansIn(lineStart, lineEnd).forEach { span ->
                val localStart = (span.start - lineStart).coerceIn(0, text.length)
                val localEnd = (span.end - lineStart).coerceIn(0, text.length)
                if (localStart < localEnd) {
                    addStyle(span.type.toSpanStyle(colorScheme, typography), localStart, localEnd)
                }
            }
        }
    }
}

fun FormatType.toSpanStyle(colorScheme: ColorScheme, typography: Typography): SpanStyle = when (this) {
    FormatType.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
    FormatType.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
    FormatType.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
    FormatType.STRIKETHROUGH -> SpanStyle(textDecoration = TextDecoration.LineThrough)
    FormatType.H1 -> SpanStyle(
        fontSize = typography.headlineLarge.fontSize,
        fontWeight = FontWeight.Bold,
        color = colorScheme.primary
    )
    FormatType.H2 -> SpanStyle(
        fontSize = typography.headlineMedium.fontSize,
        fontWeight = FontWeight.SemiBold,
        color = colorScheme.primary
    )
    FormatType.H3 -> SpanStyle(
        fontSize = typography.headlineSmall.fontSize,
        fontWeight = FontWeight.Medium,
        color = colorScheme.primary
    )
    FormatType.BLOCKQUOTE -> SpanStyle(
        color = colorScheme.onSurfaceVariant,
        fontStyle = FontStyle.Italic
    )
    FormatType.SCENE_SEPARATOR -> SpanStyle()
}
