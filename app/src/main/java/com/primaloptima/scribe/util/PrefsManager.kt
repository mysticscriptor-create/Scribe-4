package com.primaloptima.scribe.util

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.primaloptima.scribe.util.model.PinnedItem
import com.primaloptima.scribe.util.model.ShortcutAction
import com.primaloptima.scribe.util.model.StreakData

/**
 * Single access point for all SharedPreferences keys used by Scribe.
 * Matches the storage keys used by the React Native / Expo source.
 */
class PrefsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("scribe_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    // ── Active note ───────────────────────────────────────────────────────────

    var activeNoteId: String?
        get() = prefs.getString(KEY_ACTIVE_NOTE, null)
        set(v) = prefs.edit().putString(KEY_ACTIVE_NOTE, v).apply()

    // ── Vault ─────────────────────────────────────────────────────────────────

    var vaultName: String
        get() = prefs.getString(KEY_VAULT_NAME, "My Vault") ?: "My Vault"
        set(v) = prefs.edit().putString(KEY_VAULT_NAME, v).apply()

    // ── External root ─────────────────────────────────────────────────────────

    /** JSON: {uri, name} or null */
    var externalRootJson: String?
        get() = prefs.getString(KEY_EXTERNAL_ROOT, null)
        set(v) = if (v == null) prefs.edit().remove(KEY_EXTERNAL_ROOT).apply()
                 else prefs.edit().putString(KEY_EXTERNAL_ROOT, v).apply()

    // ── Themes ────────────────────────────────────────────────────────────────

    /** JSON array of custom Theme objects */
    var customThemesJson: String
        get() = prefs.getString(KEY_THEMES, "[]") ?: "[]"
        set(v) = prefs.edit().putString(KEY_THEMES, v).apply()

    var activeThemeId: String
        get() = prefs.getString(KEY_ACTIVE_THEME, "paper") ?: "paper"
        set(v) = prefs.edit().putString(KEY_ACTIVE_THEME, v).apply()

    // ── Shortcuts ─────────────────────────────────────────────────────────────

    /** JSON array of ShortcutAction objects; null = use defaults */
    var shortcutsJson: String?
        get() = prefs.getString(KEY_SHORTCUTS, null)
        set(v) = if (v == null) prefs.edit().remove(KEY_SHORTCUTS).apply()
                 else prefs.edit().putString(KEY_SHORTCUTS, v).apply()

    fun getShortcuts(): List<ShortcutAction> {
        val json = shortcutsJson ?: return DefaultShortcuts.all
        return try {
            val type = object : TypeToken<List<ShortcutAction>>() {}.type
            gson.fromJson(json, type) ?: DefaultShortcuts.all
        } catch (_: Exception) { DefaultShortcuts.all }
    }

    fun saveShortcuts(list: List<ShortcutAction>) {
        shortcutsJson = gson.toJson(list)
    }

    // ── UI panels ─────────────────────────────────────────────────────────────

    /** JSON array of PinnedItem objects */
    var pinnedJson: String
        get() = prefs.getString(KEY_PINNED, "[]") ?: "[]"
        set(v) = prefs.edit().putString(KEY_PINNED, v).apply()

    fun getPinned(): List<PinnedItem> {
        return try {
            val type = object : TypeToken<List<PinnedItem>>() {}.type
            gson.fromJson(pinnedJson, type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    fun savePinned(list: List<PinnedItem>) {
        pinnedJson = gson.toJson(list)
    }

    var viewMode: String
        get() = prefs.getString(KEY_VIEW_MODE, "tree") ?: "tree"
        set(v) = prefs.edit().putString(KEY_VIEW_MODE, v).apply()

    var showWordCount: Boolean
        get() = prefs.getBoolean(KEY_SHOW_WORD_COUNT, true)
        set(v) = prefs.edit().putBoolean(KEY_SHOW_WORD_COUNT, v).apply()

    var typewriterMode: Boolean
        get() = prefs.getBoolean(KEY_TYPEWRITER, false)
        set(v) = prefs.edit().putBoolean(KEY_TYPEWRITER, v).apply()

    var lineSpacing: String
        get() = prefs.getString(KEY_LINE_SPACING, "comfortable") ?: "comfortable"
        set(v) = prefs.edit().putString(KEY_LINE_SPACING, v).apply()

    var editorFontSize: Int
        get() = prefs.getInt(KEY_EDITOR_FONT_SIZE, 16)
        set(v) = prefs.edit().putInt(KEY_EDITOR_FONT_SIZE, v.coerceIn(14, 22)).apply()

    // ── Writing stats ─────────────────────────────────────────────────────────

    var dailyGoal: Int
        get() = prefs.getInt(KEY_DAILY_GOAL, 500)
        set(v) = prefs.edit().putInt(KEY_DAILY_GOAL, maxOf(50, v)).apply()

    fun getTodayWords(dateStr: String): Int =
        prefs.getInt("$KEY_DAILY_WORDS_PREFIX$dateStr", 0)

    fun setTodayWords(dateStr: String, count: Int) =
        prefs.edit().putInt("$KEY_DAILY_WORDS_PREFIX$dateStr", maxOf(0, count)).apply()

    fun getStreak(): StreakData {
        val json = prefs.getString(KEY_STREAK, null) ?: return StreakData()
        return try { gson.fromJson(json, StreakData::class.java) ?: StreakData() }
        catch (_: Exception) { StreakData() }
    }

    fun saveStreak(data: StreakData) {
        prefs.edit().putString(KEY_STREAK, gson.toJson(data)).apply()
    }

    // ── History snapshots ─────────────────────────────────────────────────────

    fun getSnapshotsJson(noteId: String): String =
        prefs.getString("$KEY_HISTORY_PREFIX$noteId", "[]") ?: "[]"

    fun setSnapshotsJson(noteId: String, json: String) =
        prefs.edit().putString("$KEY_HISTORY_PREFIX$noteId", json).apply()

    fun clearSnapshots(noteId: String) =
        prefs.edit().remove("$KEY_HISTORY_PREFIX$noteId").apply()

    // ── Recovery buffer ───────────────────────────────────────────────────────

    fun getRecovery(noteId: String): String? =
        prefs.getString("$KEY_RECOVERY_PREFIX$noteId", null)

    fun setRecovery(noteId: String, content: String) =
        prefs.edit().putString("$KEY_RECOVERY_PREFIX$noteId", content).apply()

    fun clearRecovery(noteId: String) =
        prefs.edit().remove("$KEY_RECOVERY_PREFIX$noteId").apply()

    // ── Constants ─────────────────────────────────────────────────────────────

    companion object {
        private const val KEY_ACTIVE_NOTE        = "scribe.activeNote.v1"
        private const val KEY_VAULT_NAME         = "scribe.vaultName.v1"
        private const val KEY_EXTERNAL_ROOT      = "scribe.externalRoot.v1"
        private const val KEY_THEMES             = "scribe.themes.v1"
        private const val KEY_ACTIVE_THEME       = "scribe.activeTheme.v1"
        private const val KEY_SHORTCUTS          = "scribe.shortcuts.v1"
        private const val KEY_PINNED             = "scribe.pinned.v1"
        private const val KEY_VIEW_MODE          = "scribe.viewMode.v1"
        private const val KEY_SHOW_WORD_COUNT    = "scribe.showWordCount.v1"
        private const val KEY_TYPEWRITER         = "scribe.typewriterMode.v1"
        private const val KEY_LINE_SPACING       = "scribe.lineSpacing.v1"
        private const val KEY_EDITOR_FONT_SIZE   = "scribe.editorFontSize.v1"
        private const val KEY_DAILY_GOAL         = "scribe.dailyGoal.v1"
        private const val KEY_DAILY_WORDS_PREFIX = "scribe.dailyWords."
        private const val KEY_STREAK             = "scribe.streak.v1"
        private const val KEY_HISTORY_PREFIX     = "scribe.history."
        private const val KEY_RECOVERY_PREFIX    = "scribe.recovery."
    }
}
