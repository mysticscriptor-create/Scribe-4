package com.primaloptima.scribe.data

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

// @Immutable: all fields are val primitives/Strings. See Note.kt for full rationale.
// Issue #2 / 2A fix.
/** Top-level container: a named book/project that holds notes and folders. */
@Immutable
@Entity(tableName = "books")
data class Book(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "cover_uri") val coverUri: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    /** Short blurb the author writes about this book. Shown on BookScreen header. */
    @ColumnInfo(name = "summary") val summary: String = "",
    /** Comma-separated genre/mood tags, e.g. "Dark Fantasy,Adventure,Romance". */
    @ColumnInfo(name = "tags") val tags: String = ""
)
