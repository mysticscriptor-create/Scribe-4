package com.primaloptima.scribe.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.primaloptima.scribe.ScribeApp
import com.primaloptima.scribe.util.DefaultShortcuts
import com.primaloptima.scribe.util.model.ShortcutAction

class ShortcutsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = (application as ScribeApp).prefs

    private val _shortcuts = MutableLiveData<List<ShortcutAction>>()
    val shortcuts: LiveData<List<ShortcutAction>> = _shortcuts

    init { reload() }

    fun reload() { _shortcuts.value = prefs.getShortcuts() }

    fun add(shortcut: ShortcutAction) {
        val list = (_shortcuts.value ?: emptyList()).toMutableList()
        list.add(shortcut)
        save(list)
    }

    fun update(shortcut: ShortcutAction) {
        val list = (_shortcuts.value ?: emptyList()).map {
            if (it.id == shortcut.id) shortcut else it
        }
        save(list)
    }

    fun delete(id: String) {
        val list = (_shortcuts.value ?: emptyList()).filter { it.id != id }
        save(list)
    }

    fun reorder(from: Int, to: Int) {
        val list = (_shortcuts.value ?: emptyList()).toMutableList()
        if (from < 0 || to < 0 || from >= list.size || to >= list.size) return
        val item = list.removeAt(from)
        list.add(to, item)
        save(list)
    }

    fun resetToDefaults() { save(DefaultShortcuts.all) }

    private fun save(list: List<ShortcutAction>) {
        prefs.saveShortcuts(list)
        _shortcuts.value = list
    }

    fun generateId(): String =
        System.currentTimeMillis().toString() + Math.random().toString().takeLast(6)
}
