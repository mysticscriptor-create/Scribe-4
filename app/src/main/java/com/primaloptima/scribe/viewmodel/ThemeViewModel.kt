package com.primaloptima.scribe.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.primaloptima.scribe.ScribeApp
import com.primaloptima.scribe.util.ThemeDataStoreRepo
import com.primaloptima.scribe.util.model.AppTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ThemeViewModel(application: Application) : AndroidViewModel(application) {

    private val themeManager = (application as ScribeApp).themeManager
    private val dataStoreRepo = ThemeDataStoreRepo(application)

    private val _themes = MutableLiveData<List<AppTheme>>()
    val themes: LiveData<List<AppTheme>> = _themes

    private val _activeTheme = MutableLiveData<AppTheme>()
    val activeTheme: LiveData<AppTheme> = _activeTheme

    init {
        reload()
        viewModelScope.launch {
            dataStoreRepo.activeThemeIdFlow.collectLatest { themeId ->
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
            dataStoreRepo.setActiveThemeId(id)
        }
        reload()
    }

    fun save(theme: AppTheme) {
        themeManager.saveCustomTheme(theme)
        viewModelScope.launch {
            dataStoreRepo.setThemeBackgroundImage(
                themeId = theme.id,
                uri = theme.backgroundImageUri,
                opacity = theme.backgroundImageOpacity ?: 0.35f
            )
            val json = (getApplication() as ScribeApp).prefs.customThemesJson
            dataStoreRepo.setCustomThemesJson(json)
        }
        reload()
    }

    fun delete(id: String) {
        themeManager.deleteCustomTheme(id)
        if (themeManager.activeTheme().id == id) {
            setActive("paper")
        }
        viewModelScope.launch {
            val json = (getApplication() as ScribeApp).prefs.customThemesJson
            dataStoreRepo.setCustomThemesJson(json)
        }
        reload()
    }

    fun duplicate(id: String): AppTheme? {
        val copy = themeManager.duplicateTheme(id) ?: return null
        reload()
        return copy
    }

    fun generateId(): String =
        System.currentTimeMillis().toString() + Math.random().toString().takeLast(6)
}
