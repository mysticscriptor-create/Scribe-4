package com.primaloptima.scribe.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.primaloptima.scribe.ScribeApp
import com.primaloptima.scribe.data.WorldEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SheetsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = (application as ScribeApp).database

    val allEntries: LiveData<List<WorldEntry>> =
        db.worldEntryDao().observeAll().asLiveData()

    val characters: LiveData<List<WorldEntry>> =
        db.worldEntryDao().observeByType("character").asLiveData()

    val locations: LiveData<List<WorldEntry>> =
        db.worldEntryDao().observeByType("location").asLiveData()

    fun createEntry(type: String, name: String, onCreated: (WorldEntry) -> Unit) {
        val template = if (type == "character") CHARACTER_FIELDS else LOCATION_FIELDS
        val entry = WorldEntry(
            id = System.currentTimeMillis().toString() + Math.random().toString().takeLast(7),
            type = type,
            name = name.ifBlank { if (type == "character") "New Character" else "New Location" },
            fieldsJson = com.google.gson.Gson().toJson(template),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        viewModelScope.launch {
            kotlinx.coroutines.withContext(Dispatchers.IO) { db.worldEntryDao().insert(entry) }
            onCreated(entry)
        }
    }

    fun updateEntry(entry: WorldEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            db.worldEntryDao().update(entry.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun deleteEntry(id: String) {
        viewModelScope.launch(Dispatchers.IO) { db.worldEntryDao().deleteById(id) }
    }

    fun duplicateEntry(id: String) {
        viewModelScope.launch {
            val source = kotlinx.coroutines.withContext(Dispatchers.IO) {
                db.worldEntryDao().getById(id)
            } ?: return@launch
            val copy = source.copy(
                id = System.currentTimeMillis().toString() + Math.random().toString().takeLast(7),
                name = "${source.name} (copy)",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            kotlinx.coroutines.withContext(Dispatchers.IO) { db.worldEntryDao().insert(copy) }
        }
    }

    companion object {
        data class Field(val label: String, val value: String = "")
        val CHARACTER_FIELDS = listOf(
            Field("Role"), Field("Age"), Field("Appearance"),
            Field("Personality"), Field("Goal"), Field("Backstory")
        )
        val LOCATION_FIELDS = listOf(
            Field("Region"), Field("Atmosphere"), Field("Key details"), Field("History")
        )
    }
}
