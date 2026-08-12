package com.primaloptima.scribe.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "note_versions",
    indices = [Index(value = ["note_id"])]
)
data class NoteVersion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "note_id") val noteId: String,
    val content: String,
    @ColumnInfo(name = "word_count") val wordCount: Int,
    val timestamp: Long = System.currentTimeMillis(),
    /** "auto" = triggered on session end, "manual" = writer tapped the checkpoint button */
    val type: String = TYPE_AUTO
) {
    companion object {
        const val TYPE_AUTO   = "auto"
        const val TYPE_MANUAL = "manual"
    }
}
