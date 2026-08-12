package com.primaloptima.scribe.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.primaloptima.scribe.ScribeApp
import com.primaloptima.scribe.util.AppJson
import com.primaloptima.scribe.util.DefaultShortcuts
import com.primaloptima.scribe.util.model.ShortcutAction
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ShortcutsViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore = (application as ScribeApp).dataStore

    private val _shortcuts = MutableStateFlow<List<ShortcutAction>>(emptyList())
    val shortcuts: StateFlow<List<ShortcutAction>> = _shortcuts.asStateFlow()

    init {
        viewModelScope.launch {
            _shortcuts.value = dataStore.getShortcuts()
        }
    }

    fun add(shortcut: ShortcutAction) {
        val list = _shortcuts.value.toMutableList()
        list.add(shortcut)
        save(list)
    }

    fun update(shortcut: ShortcutAction) {
        val list = _shortcuts.value.map {
            if (it.id == shortcut.id) shortcut else it
        }
        save(list)
    }

    fun delete(id: String) {
        val list = _shortcuts.value.filter { it.id != id }
        save(list)
    }

    fun reorder(from: Int, to: Int) {
        val list = _shortcuts.value.toMutableList()
        if (from < 0 || to < 0 || from >= list.size || to >= list.size) return
        val item = list.removeAt(from)
        list.add(to, item)
        save(list)
    }

    fun resetToDefaults() { save(DefaultShortcuts.all) }

    private fun save(list: List<ShortcutAction>) {
        _shortcuts.value = list
        viewModelScope.launch {
            dataStore.setShortcutsJson(AppJson.encodeToString(list))
        }
    }

    fun generateId(): String =
        System.currentTimeMillis().toString() + Math.random().toString().takeLast(6)
}
