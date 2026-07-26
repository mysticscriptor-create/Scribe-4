package com.primaloptima.scribe.util

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.primaloptima.scribe.util.model.SafCover
import com.primaloptima.scribe.util.model.SafFile
import com.primaloptima.scribe.util.model.SafFolder
import com.primaloptima.scribe.util.model.SafScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.File

private val TEXT_EXTENSIONS = setOf("md", "mdown", "markdown", "txt")
private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif")
private const val BATCH_SIZE = 24

object SAFHelper {

    // ── Permission ────────────────────────────────────────────────────────────

    fun takePersistablePermission(context: Context, uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: Exception) {
            // Permission grant not persistable for this URI
        }
    }

    fun releasePersistablePermission(context: Context, uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try { context.contentResolver.releasePersistableUriPermission(uri, flags) } catch (_: Exception) {}
    }

    /**
     * Copies a user-picked image URI into the app's private storage so it survives
     * force-stop and process death without requiring any ongoing URI permission.
     *
     * Background: ActivityResultContracts.GetContent() grants only a *temporary*
     * URI permission tied to the activity session.  takePersistableUriPermission()
     * works for most MediaStore URIs but silently fails for Google Photos export
     * URIs, cloud-backed providers, and some OEM gallery apps.  Copying to
     * app-private storage (`filesDir/bg_images/`) is the only universal fix.
     *
     * Removes stale bg_images files beyond a rolling window of [keepCount] files
     * so storage doesn't grow unboundedly.
     *
     * Returns the `file://` Uri of the copied file, or null on failure (caller
     * should fall back to takePersistablePermission + original uri).
     */
    suspend fun copyBgImageToInternalStorage(
        context: Context,
        sourceUri: Uri,
        keepCount: Int = 6
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, "bg_images").also { it.mkdirs() }

            // Determine extension from MIME type
            val mimeType = context.contentResolver.getType(sourceUri) ?: "image/png"
            val ext = when {
                mimeType.contains("jpeg") || mimeType.contains("jpg") -> "jpg"
                mimeType.contains("webp") -> "webp"
                else -> "png"
            }

            val dest = File(dir, "theme_bg_${System.currentTimeMillis()}.$ext")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }

            // Prune old copies beyond keepCount, sorted oldest-first
            dir.listFiles()
                ?.filter { it.name.startsWith("theme_bg_") }
                ?.sortedBy { it.lastModified() }
                ?.dropLast(keepCount)
                ?.forEach { it.delete() }

            Uri.fromFile(dest)
        } catch (_: Exception) {
            // Fallback: attempt to take persistable permission on original URI
            try {
                context.contentResolver.takePersistableUriPermission(
                    sourceUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { /* best-effort */ }
            null
        }
    }

    // ── Read / Write ──────────────────────────────────────────────────────────

    suspend fun readFile(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader().readText()
        } ?: ""
    }

    suspend fun writeFile(context: Context, uri: Uri, content: String): Unit = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
            stream.bufferedWriter().also { it.write(content); it.flush() }
        } ?: throw IllegalStateException("Could not open output stream for $uri")
    }

    suspend fun createFile(context: Context, parentUri: Uri, name: String, ext: String): Uri =
        withContext(Dispatchers.IO) {
            val parent = DocumentFile.fromTreeUri(context, parentUri)
                ?: throw IllegalArgumentException("Invalid parent URI: $parentUri")
            val mime = if (ext == "md") "text/markdown" else "text/plain"
            val doc = parent.createFile(mime, "$name.$ext")
                ?: throw IllegalStateException("Failed to create file $name.$ext")
            doc.uri
        }

    suspend fun createFolder(context: Context, parentUri: Uri, name: String): Uri =
        withContext(Dispatchers.IO) {
            val parent = DocumentFile.fromTreeUri(context, parentUri)
                ?: throw IllegalArgumentException("Invalid parent URI: $parentUri")
            val dir = parent.createDirectory(name)
                ?: throw IllegalStateException("Failed to create directory $name")
            dir.uri
        }

    suspend fun deleteDocument(context: Context, uri: Uri): Unit = withContext(Dispatchers.IO) {
        DocumentsContract.deleteDocument(context.contentResolver, uri)
    }

    // ── Directory scan ────────────────────────────────────────────────────────

    /**
     * Recursively scan a SAF tree, returning all text files, subfolder entries,
     * and the first image in each folder (for cover thumbnails).
     *
     * Mirrors the safStorage.ts scanFolderTree logic with batched concurrency.
     */
    suspend fun scanFolderTree(context: Context, treeUri: Uri): SafScanResult =
        withContext(Dispatchers.IO) {
            val files = mutableListOf<SafFile>()
            val folders = mutableListOf<SafFolder>()
            val covers = mutableListOf<SafCover>()

            scanDirectory(context, treeUri, treeUri, "/", files, folders, covers)

            SafScanResult(files = files, folders = folders, covers = covers)
        }

    private suspend fun scanDirectory(
        context: Context,
        treeUri: Uri,
        dirUri: Uri,
        relativePath: String,
        files: MutableList<SafFile>,
        folders: MutableList<SafFolder>,
        covers: MutableList<SafCover>
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getDocumentId(dirUri)
        )
        val resolver: ContentResolver = context.contentResolver
        val cursor = resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null, null, null
        ) ?: return

        data class Entry(val docId: String, val name: String, val mimeType: String)
        val entries = mutableListOf<Entry>()
        cursor.use {
            while (it.moveToNext()) {
                entries.add(
                    Entry(
                        docId    = it.getString(0),
                        name     = it.getString(1) ?: "",
                        mimeType = it.getString(2) ?: ""
                    )
                )
            }
        }

        var coverFound = false

        // Process in batches for concurrency
        entries.chunked(BATCH_SIZE).forEach { batch ->
            val jobs = batch.map { entry ->
                kotlinx.coroutines.coroutineScope {
                    async {
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, entry.docId)
                        when {
                            entry.mimeType == DocumentsContract.Document.MIME_TYPE_DIR -> {
                                val childPath = if (relativePath == "/") "/${entry.name}"
                                               else "$relativePath/${entry.name}"
                                synchronized(folders) {
                                    folders.add(SafFolder(uri = docUri.toString(), relativePath = childPath))
                                }
                                scanDirectory(context, treeUri, docUri, childPath, files, folders, covers)
                            }
                            else -> {
                                val ext = entry.name.substringAfterLast('.', "").lowercase()
                                if (ext in TEXT_EXTENSIONS) {
                                    val noteName = entry.name.substringBeforeLast('.')
                                    synchronized(files) {
                                        files.add(
                                            SafFile(
                                                uri = docUri.toString(),
                                                name = noteName,
                                                ext = ext,
                                                folderPath = relativePath
                                            )
                                        )
                                    }
                                } else if (ext in IMAGE_EXTENSIONS) {
                                    synchronized(covers) {
                                        if (!coverFound) {
                                            coverFound = true
                                            covers.add(SafCover(
                                                uri = docUri.toString(),
                                                folderPath = relativePath,
                                                ext = ext
                                            ))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            jobs.awaitAll()
        }
    }
}
