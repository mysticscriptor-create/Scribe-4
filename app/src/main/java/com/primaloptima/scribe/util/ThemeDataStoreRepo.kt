package com.primaloptima.scribe.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_preferences")

class ThemeDataStoreRepo(private val context: Context) {

    companion object {
        val KEY_ACTIVE_THEME = stringPreferencesKey("active_theme_id")
        val KEY_CUSTOM_THEMES_JSON = stringPreferencesKey("custom_themes_json")
        val KEY_GRID_COLUMNS = intPreferencesKey("grid_columns")
        val KEY_DAILY_GOAL = intPreferencesKey("daily_goal")

        fun bgUriKey(themeId: String) = stringPreferencesKey("bg_uri_$themeId")
        fun bgOpacityKey(themeId: String) = floatPreferencesKey("bg_opacity_$themeId")
    }

    val activeThemeIdFlow: Flow<String> = context.themeDataStore.data.map { prefs ->
        prefs[KEY_ACTIVE_THEME] ?: "paper"
    }

    val customThemesJsonFlow: Flow<String> = context.themeDataStore.data.map { prefs ->
        prefs[KEY_CUSTOM_THEMES_JSON] ?: "[]"
    }

    val gridColumnsFlow: Flow<Int> = context.themeDataStore.data.map { prefs ->
        prefs[KEY_GRID_COLUMNS] ?: 2
    }

    val dailyGoalFlow: Flow<Int> = context.themeDataStore.data.map { prefs ->
        prefs[KEY_DAILY_GOAL] ?: 500
    }

    suspend fun setActiveThemeId(themeId: String) {
        context.themeDataStore.edit { prefs ->
            prefs[KEY_ACTIVE_THEME] = themeId
        }
    }

    suspend fun setCustomThemesJson(json: String) {
        context.themeDataStore.edit { prefs ->
            prefs[KEY_CUSTOM_THEMES_JSON] = json
        }
    }

    suspend fun setGridColumns(cols: Int) {
        context.themeDataStore.edit { prefs ->
            prefs[KEY_GRID_COLUMNS] = cols
        }
    }

    suspend fun setDailyGoal(goal: Int) {
        context.themeDataStore.edit { prefs ->
            prefs[KEY_DAILY_GOAL] = maxOf(50, goal)
        }
    }

    fun getBgUriFlow(themeId: String): Flow<String?> = context.themeDataStore.data.map { prefs ->
        prefs[bgUriKey(themeId)]
    }

    fun getBgOpacityFlow(themeId: String): Flow<Float> = context.themeDataStore.data.map { prefs ->
        prefs[bgOpacityKey(themeId)] ?: 0.35f
    }

    suspend fun setThemeBackgroundImage(themeId: String, uri: String?, opacity: Float) {
        context.themeDataStore.edit { prefs ->
            if (uri != null) {
                prefs[bgUriKey(themeId)] = uri
            } else {
                prefs.remove(bgUriKey(themeId))
            }
            prefs[bgOpacityKey(themeId)] = opacity
        }
    }
}
