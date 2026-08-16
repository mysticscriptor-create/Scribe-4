package com.primaloptima.scribe.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.primaloptima.scribe.util.model.ShortcutAction
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Phase 2-A: Single DataStore for the entire app.
 * Replaces both PrefsManager (SharedPreferences) and ThemeDataStoreRepo.
 * One file = one source of truth per concern. All writes are suspend, all reads are Flow.
 *
 * DataStore is safe to use from coroutines — reads/writes are transactional
 * and guaranteed not to block the main thread.
 */

// Single DataStore file — only one preferencesDataStore per name per process.
// Using applicationContext as the receiver prevents Activity-context leaks.
private val Context.scribeDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "scribe_prefs")

class ScribeDataStore(private val context: Context) {

    private val store = context.applicationContext.scribeDataStore

    // ─────────────────────────────────────────────────────────────────────────
    // Keys — all in one place. Adding a setting = one line here only.
    // Key names intentionally match the old PrefsManager constants for smooth
    // migration (DataStore will not find old SharedPreferences values — users
    // start fresh for DataStore-backed keys, which is acceptable since
    // preferences like theme choice are easily re-applied).
    // ─────────────────────────────────────────────────────────────────────────

    companion object Keys {
        // Identity
        val ACTIVE_NOTE_ID     = stringPreferencesKey("active_note_id")
        val VAULT_NAME         = stringPreferencesKey("vault_name")
        val EXTERNAL_ROOT_JSON = stringPreferencesKey("external_root_json")

        // Theme
        val ACTIVE_THEME_ID    = stringPreferencesKey("active_theme_id")
        val CUSTOM_THEMES_JSON = stringPreferencesKey("custom_themes_json")
        val LEGACY_BLUR_ENABLED= booleanPreferencesKey("legacy_blur_enabled")

        // Per-theme background images (dynamic keys — one per theme)
        fun bgUriKey(themeId: String)     = stringPreferencesKey("bg_uri_$themeId")
        fun bgOpacityKey(themeId: String) = floatPreferencesKey("bg_opacity_$themeId")

        // Layout & UI
        val GRID_COLUMNS       = intPreferencesKey("grid_columns")
        val HOME_START_PAGE    = stringPreferencesKey("home_start_page")
        val VIEW_MODE          = stringPreferencesKey("view_mode")
        val ONGOING_PROJECT_ID = stringPreferencesKey("ongoing_project_id")

        // Editor
        val SHOW_WORD_COUNT    = booleanPreferencesKey("show_word_count")
        val TYPEWRITER_MODE    = booleanPreferencesKey("typewriter_mode")
        val LINE_SPACING       = stringPreferencesKey("line_spacing")
        val EDITOR_FONT_SIZE   = intPreferencesKey("editor_font_size")

        // Writing stats
        val DAILY_GOAL         = intPreferencesKey("daily_goal")

        // Shortcuts & Pinned
        val SHORTCUTS_JSON     = stringPreferencesKey("shortcuts_json")
        val PINNED_JSON        = stringPreferencesKey("pinned_json")

        // Companion panel — pinned notes slots (persisted as JSON list of note IDs)
        val PINNED_TOP_JSON    = stringPreferencesKey("pinned_top_json")
        val PINNED_BOTTOM_JSON = stringPreferencesKey("pinned_bottom_json")
        // Companion panel — UI prefs
        val COMPANION_TAB_BAR_BOTTOM   = booleanPreferencesKey("companion_tab_bar_bottom")
        val COMPANION_SPLIT_HORIZONTAL = booleanPreferencesKey("companion_split_horizontal") // true = side-by-side, false = up/down

        // Per-book goals (dynamic)
        fun bookGoalKey(bookId: String) = stringPreferencesKey("book_goal_$bookId")

        // History settings
        val AUTO_HISTORY_ENABLED       = booleanPreferencesKey("auto_history_enabled")
        val MANUAL_CHECKPOINTS_ENABLED = booleanPreferencesKey("manual_checkpoints_enabled")
        val AUTO_HISTORY_SLOTS         = intPreferencesKey("auto_history_slots")
        val MANUAL_CHECKPOINT_SLOTS    = intPreferencesKey("manual_checkpoint_slots")
        val AUTO_HISTORY_MIN_WORDS     = intPreferencesKey("auto_history_min_words")

        // One-time backfill flag — set after runWordCountBackfill() completes
        // so it never re-runs on subsequent launches.
        val WORD_COUNT_BACKFILL_DONE   = booleanPreferencesKey("word_count_backfill_done")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Flows — reactive UI reads. Collect these in ViewModels via stateIn().
    // ─────────────────────────────────────────────────────────────────────────

    val activeNoteIdFlow: Flow<String?>      = store.data.map { it[ACTIVE_NOTE_ID] }
    val vaultNameFlow: Flow<String>          = store.data.map { it[VAULT_NAME] ?: "My Vault" }
    val externalRootJsonFlow: Flow<String?>  = store.data.map { it[EXTERNAL_ROOT_JSON] }

    val activeThemeIdFlow: Flow<String>      = store.data.map { it[ACTIVE_THEME_ID] ?: "paper" }
    val customThemesJsonFlow: Flow<String>   = store.data.map { it[CUSTOM_THEMES_JSON] ?: "[]" }
    val legacyBlurEnabledFlow: Flow<Boolean> = store.data.map { it[LEGACY_BLUR_ENABLED] ?: false }

    val gridColumnsFlow: Flow<Int>           = store.data.map { it[GRID_COLUMNS] ?: 2 }
    val homeStartPageFlow: Flow<String>      = store.data.map { it[HOME_START_PAGE] ?: "books" }
    val viewModeFlow: Flow<String>           = store.data.map { it[VIEW_MODE] ?: "tree" }
    val ongoingProjectIdFlow: Flow<String?>  = store.data.map { it[ONGOING_PROJECT_ID] }

    val showWordCountFlow: Flow<Boolean>     = store.data.map { it[SHOW_WORD_COUNT] ?: true }
    val typewriterModeFlow: Flow<Boolean>    = store.data.map { it[TYPEWRITER_MODE] ?: false }
    val lineSpacingFlow: Flow<String>        = store.data.map { it[LINE_SPACING] ?: "comfortable" }
    val editorFontSizeFlow: Flow<Int>        = store.data.map { it[EDITOR_FONT_SIZE] ?: 16 }

    val dailyGoalFlow: Flow<Int>             = store.data.map { it[DAILY_GOAL] ?: 500 }
    val shortcutsJsonFlow: Flow<String?>     = store.data.map { it[SHORTCUTS_JSON] }
    val pinnedJsonFlow: Flow<String?>        = store.data.map { it[PINNED_JSON] }

    // Companion panel persistent state
    val pinnedTopJsonFlow: Flow<String?>     = store.data.map { it[PINNED_TOP_JSON] }
    val pinnedBottomJsonFlow: Flow<String?>  = store.data.map { it[PINNED_BOTTOM_JSON] }
    val companionTabBarBottomFlow: Flow<Boolean>   = store.data.map { it[COMPANION_TAB_BAR_BOTTOM] ?: false }
    val companionSplitHorizontalFlow: Flow<Boolean> = store.data.map { it[COMPANION_SPLIT_HORIZONTAL] ?: false }

    val autoHistoryEnabledFlow: Flow<Boolean>       = store.data.map { it[AUTO_HISTORY_ENABLED] ?: true }
    val manualCheckpointsEnabledFlow: Flow<Boolean> = store.data.map { it[MANUAL_CHECKPOINTS_ENABLED] ?: true }
    val autoHistorySlotsFlow: Flow<Int>             = store.data.map { it[AUTO_HISTORY_SLOTS] ?: 10 }
    val manualCheckpointSlotsFlow: Flow<Int>        = store.data.map { it[MANUAL_CHECKPOINT_SLOTS] ?: 10 }
    val autoHistoryMinWordsFlow: Flow<Int>          = store.data.map { it[AUTO_HISTORY_MIN_WORDS] ?: 10 }

    fun bgUriFlow(themeId: String): Flow<String?>   = store.data.map { it[bgUriKey(themeId)] }
    fun bgOpacityFlow(themeId: String): Flow<Float> = store.data.map { it[bgOpacityKey(themeId)] ?: 0.35f }
    /** Reactive Flow variant of [getBookGoal] — use in ViewModels that need live updates. */
    fun bookGoalFlow(bookId: String): Flow<BookGoal> = store.data.map { prefs ->
        val json = prefs[bookGoalKey(bookId)] ?: return@map BookGoal()
        try { AppJson.decodeFromString<BookGoal>(json) }
        catch (_: Exception) { BookGoal() }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Synchronous reads (suspend) — for boot-time and non-Flow callers only.
    // Always prefer the Flow variants in UI/ViewModel code.
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun getActiveThemeId(): String =
        store.data.first()[ACTIVE_THEME_ID] ?: "paper"

    suspend fun getCustomThemesJson(): String =
        store.data.first()[CUSTOM_THEMES_JSON] ?: "[]"

    suspend fun getAutoHistoryEnabled(): Boolean =
        store.data.first()[AUTO_HISTORY_ENABLED] ?: true

    suspend fun getAutoHistoryMinWords(): Int =
        store.data.first()[AUTO_HISTORY_MIN_WORDS] ?: 10

    suspend fun getAutoHistorySlots(): Int =
        store.data.first()[AUTO_HISTORY_SLOTS] ?: 10

    suspend fun getManualCheckpointsEnabled(): Boolean =
        store.data.first()[MANUAL_CHECKPOINTS_ENABLED] ?: true

    suspend fun getManualCheckpointSlots(): Int =
        store.data.first()[MANUAL_CHECKPOINT_SLOTS] ?: 10

    suspend fun getExternalRootJson(): String? =
        store.data.first()[EXTERNAL_ROOT_JSON]

    suspend fun getShortcuts(): List<ShortcutAction> {
        val json = store.data.first()[SHORTCUTS_JSON] ?: return DefaultShortcuts.all
        return try {
            AppJson.decodeFromString<List<ShortcutAction>>(json)
        } catch (_: Exception) { DefaultShortcuts.all }
    }

    suspend fun getBookGoal(bookId: String): BookGoal {
        val json = store.data.first()[bookGoalKey(bookId)] ?: return BookGoal()
        return try { AppJson.decodeFromString<BookGoal>(json) }
        catch (_: Exception) { BookGoal() }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Writes — all suspend, all execute off the main thread via DataStore internals.
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun setActiveNoteId(id: String?) = store.edit { prefs ->
        if (id == null) prefs.remove(ACTIVE_NOTE_ID) else prefs[ACTIVE_NOTE_ID] = id
    }

    suspend fun setVaultName(name: String) = store.edit { it[VAULT_NAME] = name }

    suspend fun setExternalRootJson(json: String?) = store.edit { prefs ->
        if (json == null) prefs.remove(EXTERNAL_ROOT_JSON) else prefs[EXTERNAL_ROOT_JSON] = json
    }

    suspend fun setActiveThemeId(id: String) = store.edit { it[ACTIVE_THEME_ID] = id }

    suspend fun setCustomThemesJson(json: String) = store.edit { it[CUSTOM_THEMES_JSON] = json }

    suspend fun setLegacyBlurEnabled(v: Boolean) = store.edit { it[LEGACY_BLUR_ENABLED] = v }

    suspend fun setGridColumns(cols: Int) = store.edit { it[GRID_COLUMNS] = cols }

    suspend fun setHomeStartPage(page: String) = store.edit { it[HOME_START_PAGE] = page }

    suspend fun setViewMode(mode: String) = store.edit { it[VIEW_MODE] = mode }

    suspend fun setOngoingProjectId(id: String?) = store.edit { prefs ->
        if (id == null) prefs.remove(ONGOING_PROJECT_ID) else prefs[ONGOING_PROJECT_ID] = id
    }

    suspend fun setShowWordCount(v: Boolean) = store.edit { it[SHOW_WORD_COUNT] = v }

    suspend fun setTypewriterMode(v: Boolean) = store.edit { it[TYPEWRITER_MODE] = v }

    suspend fun setLineSpacing(v: String) = store.edit { it[LINE_SPACING] = v }

    suspend fun setEditorFontSize(size: Int) = store.edit { it[EDITOR_FONT_SIZE] = size.coerceIn(12, 28) }

    suspend fun setDailyGoal(goal: Int) = store.edit { it[DAILY_GOAL] = maxOf(50, goal) }

    // ── Word count backfill flag ───────────────────────────────────────────────

    /** Returns true if the one-time word-count backfill has already completed. */
    suspend fun isWordCountBackfillDone(): Boolean =
        store.data.first()[WORD_COUNT_BACKFILL_DONE] ?: false

    /** Marks the word-count backfill as done so it never re-runs on subsequent launches. */
    suspend fun markWordCountBackfillDone() =
        store.edit { it[WORD_COUNT_BACKFILL_DONE] = true }

    suspend fun setShortcutsJson(json: String?) = store.edit { prefs ->
        if (json == null) prefs.remove(SHORTCUTS_JSON) else prefs[SHORTCUTS_JSON] = json
    }

    suspend fun setPinnedJson(json: String) = store.edit { it[PINNED_JSON] = json }

    // Companion panel write functions
    suspend fun setPinnedTopJson(json: String)    = store.edit { it[PINNED_TOP_JSON]    = json }
    suspend fun setPinnedBottomJson(json: String) = store.edit { it[PINNED_BOTTOM_JSON] = json }
    suspend fun setCompanionTabBarBottom(v: Boolean) = store.edit { it[COMPANION_TAB_BAR_BOTTOM]   = v }
    suspend fun setCompanionSplitHorizontal(v: Boolean) = store.edit { it[COMPANION_SPLIT_HORIZONTAL] = v }

    suspend fun saveBookGoal(bookId: String, goal: BookGoal) = store.edit {
        it[bookGoalKey(bookId)] = AppJson.encodeToString(goal)
    }

    suspend fun setAutoHistoryEnabled(v: Boolean) = store.edit { it[AUTO_HISTORY_ENABLED] = v }

    suspend fun setManualCheckpointsEnabled(v: Boolean) = store.edit { it[MANUAL_CHECKPOINTS_ENABLED] = v }

    suspend fun setAutoHistorySlots(v: Int) = store.edit { it[AUTO_HISTORY_SLOTS] = v.coerceIn(1, 30) }

    suspend fun setManualCheckpointSlots(v: Int) = store.edit { it[MANUAL_CHECKPOINT_SLOTS] = v.coerceIn(1, 30) }

    suspend fun setAutoHistoryMinWords(v: Int) = store.edit { it[AUTO_HISTORY_MIN_WORDS] = v.coerceIn(1, 200) }

    suspend fun setThemeBackground(themeId: String, uri: String?, opacity: Float) = store.edit { prefs ->
        if (uri != null) prefs[bgUriKey(themeId)] = uri else prefs.remove(bgUriKey(themeId))
        prefs[bgOpacityKey(themeId)] = opacity
    }
}

// ── BookGoal (moved here from PrefsManager inner class) ──────────────────────
// Kept as a top-level data class so both ScribeDataStore and callers can use it.
@Serializable
data class BookGoal(
    val dailyWords: Int = 500,
    val totalTarget: Int = 120_000,
    val chapterTarget: Int = 0  // 0 = not set
)
