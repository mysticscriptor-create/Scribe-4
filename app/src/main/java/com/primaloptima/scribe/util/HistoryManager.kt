package com.primaloptima.scribe.util

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.primaloptima.scribe.util.model.HistorySnapshot

/**
 * Manages version history snapshots for a single note.
 * Backed by SharedPreferences via PrefsManager — mirrors the lib/history.ts logic.
 */
class HistoryManager(private val prefs: PrefsManager) {

    private val gson = Gson()

    /** In-memory watermark to avoid disk reads on every autosave tick. */
    private val lastMeta = HashMap<String, Pair<Long, Int>>() // noteId -> (savedAt, length)

    private val MIN_INTERVAL_MS = 3 * 60 * 1000L  // 3 min
    private val MIN_DIFF_CHARS = 40
    private val MAX_SNAPSHOTS = 20

    fun getSnapshots(noteId: String): List<HistorySnapshot> {
        val json = prefs.getSnapshotsJson(noteId)
        return try {
            val type = object : TypeToken<List<HistorySnapshot>>() {}.type
            gson.fromJson<List<HistorySnapshot>>(json, type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    /**
     * Save a snapshot if the time/diff gate passes.
     * Returns true if a new snapshot was written.
     */
    fun maybeSnapshot(noteId: String, content: String): Boolean {
        val cached = lastMeta[noteId]
        if (cached != null) {
            val (savedAt, length) = cached
            val timeOk = System.currentTimeMillis() - savedAt >= MIN_INTERVAL_MS
            val diffOk = Math.abs(content.length - length) >= MIN_DIFF_CHARS
            if (!timeOk && !diffOk) return false
        }

        val snaps = getSnapshots(noteId).toMutableList()
        val last = snaps.lastOrNull()
        if (last != null) {
            val timeOk = System.currentTimeMillis() - last.savedAt >= MIN_INTERVAL_MS
            val diffOk = Math.abs(content.length - last.content.length) >= MIN_DIFF_CHARS
            if (!timeOk && !diffOk) {
                lastMeta[noteId] = Pair(last.savedAt, last.content.length)
                return false
            }
            if (last.content == content) return false
        }

        val savedAt = System.currentTimeMillis()
        snaps.add(HistorySnapshot(content = content, savedAt = savedAt))
        val trimmed = snaps.takeLast(MAX_SNAPSHOTS)
        prefs.setSnapshotsJson(noteId, gson.toJson(trimmed))
        lastMeta[noteId] = Pair(savedAt, content.length)
        return true
    }

    fun clearSnapshots(noteId: String) {
        prefs.clearSnapshots(noteId)
        lastMeta.remove(noteId)
    }
}
