package com.primaloptima.scribe.util

import android.content.Context
import android.widget.TextView
import com.primaloptima.scribe.util.model.OutlineEntry
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin

/**
 * Lightweight Markdown utilities — word counting, reading time, outline extraction,
 * Markwon rich rendering, and a simple HTML renderer used by export modes.
 */
object MarkdownUtil {

    /**
     * Render Markdown directly onto a TextView using Markwon engine with tables,
     * strikethroughs, task lists, and HTML support.
     */
    fun renderWithMarkwon(context: Context, markdown: String, textView: TextView) {
        val markwon = Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(HtmlPlugin.create())
            .build()
        markwon.setMarkdown(textView, markdown)
    }

    // ── Word / char counting ─────────────────────────────────────────────────

    fun countWords(text: String): Int {
        if (text.isBlank()) return 0
        return text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
    }

    fun countChars(text: String): Int = text.length

    fun readingTimeMinutes(text: String): Int {
        val words = countWords(text)
        return maxOf(1, (words / 238.0).toInt())
    }

    // ── Outline ───────────────────────────────────────────────────────────────

    /**
     * Extract H1–H4 headings from Markdown text and return them as outline entries.
     * Each entry carries the 1-based line index so the editor can jump to it.
     */
    fun extractOutline(text: String): List<OutlineEntry> {
        val entries = mutableListOf<OutlineEntry>()
        val lines = text.lines()
        lines.forEachIndexed { idx, line ->
            val trimmed = line.trimStart()
            val level = when {
                trimmed.startsWith("#### ") -> 4
                trimmed.startsWith("### ")  -> 3
                trimmed.startsWith("## ")   -> 2
                trimmed.startsWith("# ")    -> 1
                else -> 0
            }
            if (level > 0) {
                val headerText = trimmed.drop(level + 1).trim()
                // Collect next 1-3 non-empty lines for snippet preview
                val previewLines = mutableListOf<String>()
                var k = idx + 1
                while (k < lines.size && previewLines.size < 2) {
                    val nextLine = lines[k].trim()
                    if (nextLine.isNotBlank() && !nextLine.startsWith("#")) {
                        previewLines.add(nextLine)
                    } else if (nextLine.startsWith("#")) {
                        break
                    }
                    k++
                }
                val previewText = if (previewLines.isNotEmpty()) previewLines.joinToString(" ") else null
                entries.add(OutlineEntry(level = level, text = headerText, lineIndex = idx, preview = previewText))
            }
        }
        return entries
    }

    // ── Simple HTML renderer ──────────────────────────────────────────────────

    fun toHtmlBody(markdown: String): String {
        val sb = StringBuilder()
        val lines = markdown.lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trimStart()

            when {
                // Headings
                trimmed.startsWith("#### ") -> {
                    sb.append("<h4>${inlineHtml(trimmed.drop(5))}</h4>\n"); i++
                }
                trimmed.startsWith("### ") -> {
                    sb.append("<h3>${inlineHtml(trimmed.drop(4))}</h3>\n"); i++
                }
                trimmed.startsWith("## ") -> {
                    sb.append("<h2>${inlineHtml(trimmed.drop(3))}</h2>\n"); i++
                }
                trimmed.startsWith("# ") -> {
                    sb.append("<h1>${inlineHtml(trimmed.drop(2))}</h1>\n"); i++
                }
                // Blockquote
                trimmed.startsWith("> ") -> {
                    sb.append("<blockquote>${inlineHtml(trimmed.drop(2))}</blockquote>\n"); i++
                }
                // Fenced code block
                trimmed.startsWith("```") -> {
                    val lang = trimmed.drop(3).trim()
                    sb.append("<pre><code${if (lang.isNotEmpty()) " class=\"language-$lang\"" else ""}>")
                    i++
                    while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                        sb.append(escapeHtml(lines[i])).append("\n")
                        i++
                    }
                    sb.append("</code></pre>\n")
                    i++ // skip closing ```
                }
                // HR
                trimmed == "---" || trimmed == "***" || trimmed == "___" -> {
                    sb.append("<hr/>\n"); i++
                }
                // Unordered list
                (trimmed.startsWith("- ") || trimmed.startsWith("* ")) -> {
                    sb.append("<ul>\n")
                    while (i < lines.size) {
                        val l = lines[i].trimStart()
                        if (!l.startsWith("- ") && !l.startsWith("* ")) break
                        val content = if (l.startsWith("- ")) l.drop(2) else l.drop(2)
                        sb.append("  <li>${inlineHtml(content)}</li>\n")
                        i++
                    }
                    sb.append("</ul>\n")
                }
                // Ordered list
                trimmed.matches(Regex("\\d+\\. .*")) -> {
                    sb.append("<ol>\n")
                    while (i < lines.size) {
                        val l = lines[i].trimStart()
                        if (!l.matches(Regex("\\d+\\. .*"))) break
                        val content = l.replace(Regex("^\\d+\\. "), "")
                        sb.append("  <li>${inlineHtml(content)}</li>\n")
                        i++
                    }
                    sb.append("</ol>\n")
                }
                // Blank line → paragraph break
                trimmed.isEmpty() -> { sb.append(""); i++ }
                // Paragraph
                else -> {
                    sb.append("<p>${inlineHtml(trimmed)}</p>\n"); i++
                }
            }
        }
        return sb.toString()
    }

    fun fullHtmlDocument(title: String, bodyHtml: String): String = """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8"/>
<title>${escapeHtml(title)}</title>
<style>
  body { font-family: Georgia, serif; max-width: 680px; margin: 40px auto; padding: 0 20px; line-height: 1.6; color: #222; }
  h1,h2,h3,h4 { font-family: -apple-system, Helvetica, Arial, sans-serif; }
  blockquote { border-left: 3px solid #ccc; margin-left: 0; padding-left: 16px; color: #555; }
  code, pre { font-family: 'Courier New', monospace; background: #f4f4f4; padding: 2px 4px; border-radius: 3px; }
  pre { padding: 12px; overflow-x: auto; }
  pre code { padding: 0; background: none; }
  hr { border: none; border-top: 1px solid #ddd; margin: 24px 0; }
</style>
</head>
<body>
<h1>${escapeHtml(title)}</h1>
$bodyHtml
</body>
</html>""".trimIndent()

    // ── Inline formatting ─────────────────────────────────────────────────────

    private fun inlineHtml(text: String): String {
        var s = escapeHtml(text)
        // Bold + italic: ***text***
        s = s.replace(Regex("\\*\\*\\*(.+?)\\*\\*\\*"), "<strong><em>$1</em></strong>")
        // Bold: **text**
        s = s.replace(Regex("\\*\\*(.+?)\\*\\*"), "<strong>$1</strong>")
        // Italic: *text* or _text_
        s = s.replace(Regex("\\*(.+?)\\*"), "<em>$1</em>")
        s = s.replace(Regex("_(.+?)_"), "<em>$1</em>")
        // Inline code: `text`
        s = s.replace(Regex("`(.+?)`"), "<code>$1</code>")
        // Links: [text](url)
        s = s.replace(Regex("\\[(.+?)\\]\\((.+?)\\)"), "<a href=\"$2\">$1</a>")
        return s
    }

    fun escapeHtml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    fun xmlEscape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    /**
     * Strip all Markdown syntax and return plain text.
     * Used for export to .txt / .docx.
     */
    fun toPlainText(markdown: String): String {
        var s = markdown
        s = s.replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
        s = s.replace(Regex("\\*\\*\\*(.+?)\\*\\*\\*"), "$1")
        s = s.replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
        s = s.replace(Regex("\\*(.+?)\\*"), "$1")
        s = s.replace(Regex("_(.+?)_"), "$1")
        s = s.replace(Regex("`(.+?)`"), "$1")
        s = s.replace(Regex("```[^`]*```", RegexOption.DOT_MATCHES_ALL), "")
        s = s.replace(Regex("\\[(.+?)\\]\\(.+?\\)"), "$1")
        s = s.replace(Regex("^>\\s?", RegexOption.MULTILINE), "")
        s = s.replace(Regex("^[-*]\\s", RegexOption.MULTILINE), "")
        s = s.replace(Regex("^\\d+\\.\\s", RegexOption.MULTILINE), "")
        s = s.replace(Regex("---+"), "")
        return s.trim()
    }
}
