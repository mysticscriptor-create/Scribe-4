package com.primaloptima.scribe.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.primaloptima.scribe.ScribeApp
import com.primaloptima.scribe.util.AppJson
import com.primaloptima.scribe.util.SAFHelper
import com.primaloptima.scribe.util.model.AppTheme
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ThemeViewModel(application: Application) : AndroidViewModel(application) {

    private val themeManager = (application as ScribeApp).themeManager
    private val dataStore = (application as ScribeApp).dataStore
    private val _themes = MutableStateFlow<List<AppTheme>>(emptyList())
    val themes: StateFlow<List<AppTheme>> = _themes.asStateFlow()

    private val _activeTheme = MutableStateFlow<AppTheme?>(null)
    val activeTheme: StateFlow<AppTheme?> = _activeTheme.asStateFlow()

    init {
        reload()
        viewModelScope.launch {
            dataStore.activeThemeIdFlow.collectLatest { themeId ->
                if (themeManager.activeTheme().id != themeId) {
                    themeManager.setActiveTheme(themeId)
                    reload()
                }
            }
        }
    }

    fun reload() {
        _themes.value = themeManager.allThemes()
        _activeTheme.value = themeManager.activeTheme()
    }

    fun setActive(id: String) {
        themeManager.setActiveTheme(id)
        viewModelScope.launch {
            dataStore.setActiveThemeId(id)
        }
        reload()
    }

    fun save(theme: AppTheme) {
        themeManager.saveCustomTheme(theme)
        // Eagerly reload so ViewModels and ThemeListActivity update immediately
        // from the in-memory cache (which saveCustomTheme already updated above).
        reload()
        viewModelScope.launch {
            // Persist to DataStore — ScribeComposeTheme observes customThemesJsonFlow,
            // so this write triggers a recomposition with the fully-updated theme
            // (including backgroundImageUri, bgMode, themeScope, etc).
            // reload() is NOT called again here: the DataStore flow emission already
            // causes ScribeComposeTheme to re-derive resolvedTheme from the new JSON.
            dataStore.setThemeBackground(
                themeId = theme.id,
                uri = theme.backgroundImageUri,
                opacity = theme.backgroundImageOpacity ?: 0.35f
            )
            dataStore.setCustomThemesJson(AppJson.encodeToString(themeManager.allCustomThemes()))
        }
    }

    fun delete(id: String) {
        themeManager.deleteCustomTheme(id)
        if (themeManager.activeTheme().id == id) {
            setActive("paper")
        }
        viewModelScope.launch {
            dataStore.setCustomThemesJson(AppJson.encodeToString(themeManager.allCustomThemes()))
            withContext(Dispatchers.IO) {
                SAFHelper.deleteThemeImageFolder(getApplication(), id)
            }
        }
        reload()
    }

    fun duplicate(id: String): AppTheme? {
        val copy = themeManager.duplicateTheme(id) ?: return null
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val hadImages = SAFHelper.copyThemeImageFolder(getApplication(), id, copy.id)
                if (hadImages) {
                    val newDir = java.io.File(getApplication<android.app.Application>().filesDir, "bg_images/${copy.id}")
                    fun remapUri(oldUri: String?): String? {
                        if (oldUri.isNullOrEmpty()) return oldUri
                        val oldFile = android.net.Uri.parse(oldUri).path?.let { java.io.File(it) } ?: return oldUri
                        val newFile = java.io.File(newDir, oldFile.name)
                        return if (newFile.exists()) android.net.Uri.fromFile(newFile).toString() else oldUri
                    }
                    val updated = copy.copy(
                        backgroundImageUri = remapUri(copy.backgroundImageUri),
                        backgroundImageOriginalUri = remapUri(copy.backgroundImageOriginalUri)
                    )
                    themeManager.saveCustomTheme(updated)
                }
            }
            dataStore.setCustomThemesJson(AppJson.encodeToString(themeManager.allCustomThemes()))
            reload()
        }
        reload()
        return copy
    }

    fun generateId(): String =
        System.currentTimeMillis().toString() + Math.random().toString().takeLast(6)
}
