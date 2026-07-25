package com.primaloptima.scribe.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.FileProvider
import com.primaloptima.scribe.data.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ExportHelper {

    /**
     * Export a note to the requested format and open the system share sheet.
     * Must be called from the main thread for PDF (uses WebView).
     */
    fun shareNote(context: Context, note: Note, format: String) {
        when (format) {
            "txt"  -> shareText(context, note, "txt", "text/plain")
            "md"   -> shareText(context, note, "md", "text/markdown")
            "html" -> shareText(context, note, "html", "text/html", fullHtml(note))
            "epub" -> shareBytes(context, note, "epub", "application/epub+zip", buildEpub(note))
            "docx" -> shareBytes(context, note, "docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                buildDocx(note))
            "pdf"  -> printToPdf(context, note)
        }
    }

    // ── Text formats ──────────────────────────────────────────────────────────

    private fun shareText(context: Context, note: Note, ext: String, mime: String,
                          content: String = note.content) {
        val file = writeCacheFile(context, sanitize(note.name) + ".$ext", content.toByteArray())
        openShareSheet(context, file, mime)
    }

    private fun shareBytes(context: Context, note: Note, ext: String, mime: String, bytes: ByteArray) {
        val file = writeCacheFile(context, sanitize(note.name) + ".$ext", bytes)
        openShareSheet(context, file, mime)
    }

    // ── PDF via print framework ────────────────────────────────────────────────

    private fun printToPdf(context: Context, note: Note) {
        val webView = WebView(context)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val jobName = sanitize(note.name)
                val printAdapter = view.createPrintDocumentAdapter(jobName)
                pm.print(
                    jobName,
                    printAdapter,
                    PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .build()
                )
            }
        }
        webView.loadDataWithBaseURL(null, fullHtml(note), "text/html", "UTF-8", null)
    }

    // ── HTML ──────────────────────────────────────────────────────────────────

    private fun fullHtml(note: Note): String {
        val body = MarkdownUtil.toHtmlBody(note.content)
        return MarkdownUtil.fullHtmlDocument(note.name, body)
    }

    // ── EPUB ──────────────────────────────────────────────────────────────────

    private fun buildEpub(note: Note): ByteArray {
        val body = MarkdownUtil.toHtmlBody(note.content)
        val title = MarkdownUtil.escapeHtml(note.name)
        val uuid = "scribe-${note.id}-${System.currentTimeMillis()}"

        val entries = mapOf(
            "mimetype" to "application/epub+zip".toByteArray(),
            "META-INF/container.xml" to """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>""".toByteArray(),
            "OEBPS/chapter1.xhtml" to """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml">
<head><title>$title</title></head>
<body>
<h1>$title</h1>
$body
</body>
</html>""".toByteArray(),
            "OEBPS/content.opf" to """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" unique-identifier="BookId" version="2.0">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:title>$title</dc:title>
    <dc:language>en</dc:language>
    <dc:identifier id="BookId">$uuid</dc:identifier>
    <dc:creator>Scribe</dc:creator>
  </metadata>
  <manifest>
    <item id="chapter1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
  </manifest>
  <spine toc="ncx">
    <itemref idref="chapter1"/>
  </spine>
</package>""".toByteArray(),
            "OEBPS/toc.ncx" to """<?xml version="1.0" encoding="UTF-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
  <head><meta name="dtb:uid" content="$uuid"/></head>
  <docTitle><text>$title</text></docTitle>
  <navMap>
    <navPoint id="chapter1" playOrder="1">
      <navLabel><text>$title</text></navLabel>
      <content src="chapter1.xhtml"/>
    </navPoint>
  </navMap>
</ncx>""".toByteArray()
        )

        return buildZip(entries, storeEntries = setOf("mimetype"))
    }

    // ── DOCX ──────────────────────────────────────────────────────────────────

    private fun buildDocx(note: Note): ByteArray {
        val plain = MarkdownUtil.toPlainText(note.content)
        val titleXml = MarkdownUtil.xmlEscape(note.name)
        val paras = plain.lines().joinToString("") { line ->
            if (line.isBlank()) "<w:p/>"
            else "<w:p><w:r><w:t xml:space=\"preserve\">${MarkdownUtil.xmlEscape(line)}</w:t></w:r></w:p>"
        }
        val entries = mapOf(
            "[Content_Types].xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>""".toByteArray(),
            "_rels/.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>""".toByteArray(),
            "word/_rels/document.xml.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"/>""".toByteArray(),
            "word/document.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
    <w:p><w:pPr><w:pStyle w:val="Title"/></w:pPr><w:r><w:t xml:space="preserve">$titleXml</w:t></w:r></w:p>
    $paras
    <w:sectPr/>
  </w:body>
</w:document>""".toByteArray()
        )
        return buildZip(entries)
    }

    // ── ZIP helper ────────────────────────────────────────────────────────────

    private fun buildZip(entries: Map<String, ByteArray>, storeEntries: Set<String> = emptySet()): ByteArray {
        val baos = java.io.ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            for ((name, bytes) in entries) {
                val entry = ZipEntry(name)
                if (name in storeEntries) entry.method = ZipEntry.STORED
                zos.putNextEntry(entry)
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    // ── File / share helpers ──────────────────────────────────────────────────

    private fun writeCacheFile(context: Context, name: String, bytes: ByteArray): File {
        val dir = File(context.cacheDir, "exports").also { it.mkdirs() }
        return File(dir, name).also { it.writeBytes(bytes) }
    }

    private fun openShareSheet(context: Context, file: File, mime: String) {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, file.name))
    }

    private fun sanitize(name: String): String =
        (name.ifBlank { "Untitled" }).replace(Regex("[\\\\/:*?\"<>|]"), "-").trim()
}
