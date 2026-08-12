package com.primaloptima.scribe.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * Records how many words were written per note per day.
 *
 * Primary key = (date, note_id) — one row per note per day.
 * If the same note is edited 10 times in one day, all deltas accumulate
 * into a single row via WritingLogDao.recordDelta(). Keeps the table small.
 *
 * words_added can be negative (net deletion on a given day is valid).
 */
@Entity(
    tableName = "writing_log",
    primaryKeys = ["date", "note_id"],
    indices = [
        Index(value = ["date"]),
        Index(value = ["book_id", "date"]),
        Index(value = ["book_id", "folder_path", "date"])
    ]
)
data class WritingLog(
    /** ISO date string, e.g. "2026-08-10" */
    val date: String,
    @ColumnInfo(name = "note_id")     val noteId: String,
    @ColumnInfo(name = "book_id")     val bookId: String,
    @ColumnInfo(name = "folder_path") val folderPath: String,
    /** Net word delta for this note on this date. Can be negative. */
    @ColumnInfo(name = "words_added") val wordsAdded: Int = 0
)
