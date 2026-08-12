package com.primaloptima.scribe.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.primaloptima.scribe.ScribeApp
import com.primaloptima.scribe.util.AppJson
import com.primaloptima.scribe.data.WorldEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SheetsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = (application as ScribeApp).database

    val allEntries: StateFlow<List<WorldEntry>> =
        db.worldEntryDao().observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val characters: StateFlow<List<WorldEntry>> =
        db.worldEntryDao().observeByType("character")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val locations: StateFlow<List<WorldEntry>> =
        db.worldEntryDao().observeByType("location")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createEntry(type: String, name: String, onCreated: (WorldEntry) -> Unit) {
        val template = when (type) {
            "character" -> CHARACTER_FIELDS
            "location"  -> LOCATION_FIELDS
            "faction"   -> FACTION_FIELDS
            "item"      -> ITEM_FIELDS
            "lore"      -> LORE_FIELDS
            "timeline"  -> TIMELINE_FIELDS
            else        -> GENERAL_FIELDS
        }
        val defaultName = when (type) {
            "character" -> "New Character"
            "location"  -> "New Location"
            "faction"   -> "New Faction"
            "item"      -> "New Item"
            "lore"      -> "New Lore Entry"
            "timeline"  -> "New Timeline Event"
            else        -> "New Entry"
        }
        val entry = WorldEntry(
            id         = System.currentTimeMillis().toString() + Math.random().toString().takeLast(7),
            type       = type,
            name       = name.ifBlank { defaultName },
            fieldsJson = AppJson.encodeToString(template),
            createdAt  = System.currentTimeMillis(),
            updatedAt  = System.currentTimeMillis()
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
                id        = System.currentTimeMillis().toString() + Math.random().toString().takeLast(7),
                name      = "${source.name} (copy)",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            kotlinx.coroutines.withContext(Dispatchers.IO) { db.worldEntryDao().insert(copy) }
        }
    }

    companion object {

        @Serializable
        data class Field(val label: String, val value: String = "")

        val CHARACTER_FIELDS = listOf(
            Field("Role"),
            Field("Age"),
            Field("Appearance"),
            Field("Personality"),
            Field("Goal / Motivation"),
            Field("Backstory"),
            Field("Strengths"),
            Field("Weaknesses"),
            Field("Relationships")
        )

        val LOCATION_FIELDS = listOf(
            Field("Region / World"),
            Field("Atmosphere"),
            Field("Key Details"),
            Field("History"),
            Field("Who lives here"),
            Field("Significance to story")
        )

        val FACTION_FIELDS = listOf(
            Field("Leader"),
            Field("Allegiance"),
            Field("Size / Reach"),
            Field("Goal"),
            Field("Base of Operations"),
            Field("Rivals / Enemies"),
            Field("Resources"),
            Field("Secrets")
        )

        val ITEM_FIELDS = listOf(
            Field("Type"),
            Field("Appearance"),
            Field("Origin / Creator"),
            Field("Powers / Properties"),
            Field("Current Owner"),
            Field("History"),
            Field("Value / Rarity")
        )

        val LORE_FIELDS = listOf(
            Field("Era / Period"),
            Field("Key Figures"),
            Field("What Happened"),
            Field("Cause"),
            Field("Consequence"),
            Field("Who Knows About This"),
            Field("Impact on Present Story")
        )

        val TIMELINE_FIELDS = listOf(
            Field("Date / Era"),
            Field("Event"),
            Field("Location"),
            Field("Who Was Involved"),
            Field("Outcome"),
            Field("Impact on Story"),
            Field("Connected Events")
        )

        val GENERAL_FIELDS = listOf(
            Field("Description"),
            Field("Notes")
        )
    }
}
