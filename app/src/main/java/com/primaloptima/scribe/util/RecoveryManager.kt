package com.primaloptima.scribe.util

import android.content.Context
import java.io.File

/**
 * Phase 3-A: Crash-recovery buffer backed by flat files in filesDir/recovery/.
 *
 * Replaces the old prefs.setRecovery() / getRecovery() / clearRecovery() which
 * stored the full note text inside a SharedPreferences XML file. For a 5,000-word
 * chapter (~30KB), that meant rewriting a 30KB XML file on every autosave tick.
 *
 * With flat files:
 *  - One file per note (e.g. "<noteId>.recovery")
 *  - File I/O is ~10x faster than SharedPreferences XML serialisation at this size
 *  - Writes happen on Dispatchers.IO (see EditorViewModel Phase 3-B), never blocking
 *    the main thread
 *  - Files survive process death and are cleared only when the user dismisses recovery
 *
 * The directory is created lazily on first access.
 */
object RecoveryManager {

    private const val DIR_NAME = "recovery"
    private const val FILE_EXT = ".recovery"

    private fun recoveryDir(context: Context): File =
        File(context.applicationContext.filesDir, DIR_NAME).also { it.mkdirs() }

    private fun recoveryFile(context: Context, noteId: String): File =
        File(recoveryDir(context), "$noteId$FILE_EXT")

    /**
     * Returns the recovery content for [noteId], or null if no recovery file exists.
     * Safe to call on any thread; intended for Dispatchers.IO.
     */
    fun get(context: Context, noteId: String): String? {
        val f = recoveryFile(context, noteId)
        return if (f.exists()) {
            try { f.readText() } catch (_: Exception) { null }
        } else null
    }

    /**
     * Writes [content] to the recovery file for [noteId].
     * Creates the recovery directory if it does not exist.
     * Safe to call on any thread; intended for Dispatchers.IO.
     */
    fun set(context: Context, noteId: String, content: String) {
        try { recoveryFile(context, noteId).writeText(content) }
        catch (_: Exception) { /* best-effort — a failed write is silent */ }
    }

    /**
     * Deletes the recovery file for [noteId].
     * Called when the user saves or explicitly dismisses the recovery prompt.
     */
    fun clear(context: Context, noteId: String) {
        try { recoveryFile(context, noteId).delete() }
        catch (_: Exception) {}
    }

    /**
     * Returns true if a recovery file exists for [noteId] and its content
     * differs from [savedContent]. Used by EditorViewModel.checkRecovery().
     */
    fun hasRecovery(context: Context, noteId: String, savedContent: String): Boolean {
        val saved = get(context, noteId) ?: return false
        return saved != savedContent
    }
}
