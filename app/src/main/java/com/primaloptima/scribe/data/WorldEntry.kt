package com.primaloptima.scribe.data

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A character sheet or location sheet entry.
 * Fields are serialised as JSON via Converters.
 * @Immutable: all fields are val primitives/Strings. See Note.kt for full rationale.
 * Issue #2 / 2A fix.
 */
@Immutable
@Entity(tableName = "world_entries")
data class WorldEntry(
    @PrimaryKey val id: String,
    /** "character" or "location" */
    val type: String,
    val name: String,
    val summary: String = "",
    /** JSON array of [{label, value}] — stored via Converters */
    @ColumnInfo(name = "fields_json") val fieldsJson: String = "[]",
    /** JSON array of tag strings */
    @ColumnInfo(name = "tags_json") val tagsJson: String = "[]",
    @ColumnInfo(name = "image_uri") val imageUri: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
