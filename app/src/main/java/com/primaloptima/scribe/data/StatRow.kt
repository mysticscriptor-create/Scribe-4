package com.primaloptima.scribe.data

import androidx.room.ColumnInfo

/**
 * Projection classes used by WritingLogDao queries.
 * Room maps query results to these — one class per distinct query shape.
 * They are plain data holders with no Room annotations beyond @ColumnInfo
 * where the column alias differs from the field name.
 */

/** One row from a daily-aggregation query: a date and the total words written that day. */
data class DailyWordRow(
    val date: String,
    val total: Int
)

/** One row from a monthly-aggregation query: "YYYY-MM" and the total for that month. */
data class MonthlyWordRow(
    val month: String,
    val total: Int
)

/** One row from the folder word-count query: book + folder + current word total. */
data class FolderWordRow(
    @ColumnInfo(name = "book_id")     val bookId: String,
    @ColumnInfo(name = "folder_path") val folderPath: String,
    val total: Int
)
