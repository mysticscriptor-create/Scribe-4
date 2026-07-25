package com.primaloptima.scribe.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File

object CoverUtils {

    /**
     * Copies a selected image Uri into app-internal storage so it persists safely across
     * sessions without throwing SecurityException due to transient content permissions.
     */
    fun saveCoverImage(context: Context, bookId: String, uri: Uri): String {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
            // Content provider does not support persistable permissions
        }

        return try {
            val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
            val destFile = File(coversDir, "cover_${bookId}_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            // Delete older cover files for this book
            coversDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("cover_${bookId}_") && file != destFile) {
                    file.delete()
                }
            }
            Uri.fromFile(destFile).toString()
        } catch (e: Exception) {
            e.printStackTrace()
            uri.toString()
        }
    }
}
