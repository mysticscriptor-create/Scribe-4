package com.primaloptima.scribe.data

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// @Immutable: all fields are val primitives/Strings — no mutable nested objects.
// The Compose compiler already infers stability for same-module all-val data classes,
// but the explicit annotation documents intent and acts as a guard if a var field
// is ever added (the compiler would warn rather than silently degrade performance).
// Issue #2 / 2A fix. See stability_config.conf for the complementary List<T> fix.
/** A text note stored in the vault or backed by a SAF URI. */
@Immutable
@Entity(
    tableName = "notes",
    indices = [
        Index(value = ["book_id"]),
        Index(value = ["book_id", "folder_path"])
    ]
)
data class Note(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "book_id") val bookId: String = DEFAULT_BOOK_ID,
    @ColumnInfo(name = "folder_path") val folderPath: String = "/",
    /** "md" or "txt" */
    val ext: String = "md",
    val content: String = "",
    // Phase 1-A: stored word count — eliminates all UI-layer Regex word counting
    @ColumnInfo(name = "word_count") val wordCount: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    /** Non-null when this note is backed by a SAF document URI. */
    @ColumnInfo(name = "external_uri") val externalUri: String? = null,
    /** True once the SAF file content has been read from disk. */
    val loaded: Boolean = true,
    @ColumnInfo(name = "formats_json") val formatsJson: String? = null
) {
    companion object {
        const val DEFAULT_BOOK_ID = "default"
    }
}

/** A logical folder entry in the vault, scoped to a book. */
@Immutable
@Entity(tableName = "folders", primaryKeys = ["book_id", "path"])
data class Folder(
    @ColumnInfo(name = "book_id") val bookId: String = Note.DEFAULT_BOOK_ID,
    val path: String,
    @ColumnInfo(name = "external_uri") val externalUri: String? = null
)
