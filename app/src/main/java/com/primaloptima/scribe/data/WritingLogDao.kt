package com.primaloptima.scribe.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the writing_log table.
 *
 * All queries are suspend — no live Flow. Data is loaded on demand when
 * a screen appears, not continuously streamed. This keeps the query cost
 * predictable and avoids unnecessary re-queries while the user is typing.
 *
 * The write path (recordDelta) uses INSERT OR REPLACE with a COALESCE
 * sub-select to accumulate the day's delta into a single row per note.
 *
 * Changed to abstract class (from interface) so that @Transaction functions
 * can have a concrete body — Room requires this for composite transactions.
 */
@Dao
abstract class WritingLogDao {

    // ── Write ─────────────────────────────────────────────────────────────────

    /**
     * Records a net word delta for a note on a given date.
     *
     * If a row for (date, note_id) already exists, the delta is added to the
     * existing words_added value. If not, a new row is created.
     * Called from EditorViewModel.saveContent() on every save where delta != 0.
     */
    @Query("""
        INSERT OR REPLACE INTO writing_log (date, note_id, book_id, folder_path, words_added)
        VALUES (:date, :noteId, :bookId, :folderPath,
            COALESCE(
                (SELECT words_added FROM writing_log
                 WHERE date = :date AND note_id = :noteId),
                0
            ) + :delta
        )
    """)
    abstract suspend fun recordDelta(
        date: String,
        noteId: String,
        bookId: String,
        folderPath: String,
        delta: Int
    )

    /**
     * Fix (Bug 3): Atomically updates notes.word_count and writing_log in one transaction.
     *
     * Without this, a process kill between the two separate DB calls in saveContent()
     * leaves notes.word_count updated but writing_log missing the delta — causing the
     * two tables to silently diverge. When delta == 0 only the notes update runs;
     * no spurious zero-delta row is written to writing_log.
     */
    @Transaction
    open suspend fun saveContentAndDelta(
        noteDao: NoteDao,
        noteId: String,
        content: String,
        wordCount: Int,
        updatedAt: Long,
        date: String,
        bookId: String,
        folderPath: String,
        delta: Int
    ) {
        noteDao.updateContentAndWordCount(noteId, content, wordCount, updatedAt)
        if (delta != 0) {
            recordDelta(date, noteId, bookId, folderPath, delta)
        }
    }

    /**
     * Updates the folder_path on all historical writing_log rows for a note.
     * Call this after moveNote() if you want writing_log to reflect the note's
     * current location rather than its historical one.
     *
     * NOTE (Bug 4): The current getWordCountPerFolder() already reads from
     * notes.folder_path (not writing_log.folder_path), so the Wordmap is immune
     * to stale folder_path values here. This query is provided for future queries
     * that may group writing_log by folder_path directly.
     */
    @Query("UPDATE writing_log SET folder_path = :newFolderPath WHERE note_id = :noteId")
    abstract suspend fun updateFolderPath(noteId: String, newFolderPath: String)

    // ── Chart queries ──────────────────────────────────────────────────────────

    /**
     * Daily totals between two ISO dates (inclusive).
     * Used for WEEK / TWO_WEEKS / MONTH chart ranges in the Statistics tab.
     * Only returns dates that have data — callers fill in zero-gaps.
     */
    @Query("""
        SELECT date, COALESCE(SUM(words_added), 0) AS total
        FROM writing_log
        WHERE date BETWEEN :startDate AND :endDate
        GROUP BY date
        ORDER BY date ASC
    """)
    abstract suspend fun getWordsByDateRange(startDate: String, endDate: String): List<DailyWordRow>

    /**
     * Monthly totals from a start date to now.
     * Groups "2026-08-10" → "2026-08" using strftime.
     * Used for the YEAR chart range.
     */
    @Query("""
        SELECT strftime('%Y-%m', date) AS month,
               COALESCE(SUM(words_added), 0) AS total
        FROM writing_log
        WHERE date >= :startDate
        GROUP BY month
        ORDER BY month ASC
    """)
    abstract suspend fun getWordsByMonth(startDate: String): List<MonthlyWordRow>

    // ── Dashboard weekly chart ─────────────────────────────────────────────────

    /**
     * Daily totals for the last 7 days (from sevenDaysAgo to today).
     * Called once when DashboardScreen appears.
     */
    @Query("""
        SELECT date, COALESCE(SUM(words_added), 0) AS total
        FROM writing_log
        WHERE date >= :sevenDaysAgo
        GROUP BY date
        ORDER BY date ASC
    """)
    abstract suspend fun getWeeklyWords(sevenDaysAgo: String): List<DailyWordRow>

    // ── Daily totals ───────────────────────────────────────────────────────────

    /** Total words written vault-wide on a specific date. Used for streak checks. */
    @Query("""
        SELECT COALESCE(SUM(words_added), 0)
        FROM writing_log
        WHERE date = :date
    """)
    abstract suspend fun getTotalWordsOnDate(date: String): Int

    /**
     * Reactive today's word count — re-emits every time writing_log changes.
     * Fix 3: replaces the suspend getTodayWords() + LaunchedEffect(Unit) pattern.
     * Room re-runs this whenever a row is inserted, so the Dashboard progress bar
     * updates live while the user is writing.
     */
    @Query("""
        SELECT COALESCE(SUM(words_added), 0)
        FROM writing_log
        WHERE date = :today
    """)
    abstract fun observeTodayWords(today: String): Flow<Int>

    /** Suspend variant kept for one-off reads outside the reactive chain. */
    @Query("""
        SELECT COALESCE(SUM(words_added), 0)
        FROM writing_log
        WHERE date = :today
    """)
    abstract suspend fun getTodayWords(today: String): Int

    // ── Streak queries ─────────────────────────────────────────────────────────

    /**
     * Reactive writing-dates stream — re-emits whenever writing_log changes.
     * Fix 3: used by HomeViewModel to derive streak StateFlows without LaunchedEffect.
     */
    @Query("""
        SELECT date, COALESCE(SUM(words_added), 0) AS total
        FROM writing_log
        WHERE words_added > 0
        GROUP BY date
        HAVING total > 0
        ORDER BY date DESC
    """)
    abstract fun observeWritingDates(): Flow<List<DailyWordRow>>

    /**
     * Reactive weekly totals — re-emits when writing_log changes.
     * Fix 3: used by HomeViewModel to derive weeklyWordData without LaunchedEffect.
     */
    @Query("""
        SELECT date, COALESCE(SUM(words_added), 0) AS total
        FROM writing_log
        WHERE date >= :sevenDaysAgo
        GROUP BY date
        ORDER BY date ASC
    """)
    abstract fun observeWeeklyWords(sevenDaysAgo: String): Flow<List<DailyWordRow>>

    /** Suspend variant kept for one-off reads. */
    @Query("""
        SELECT date, COALESCE(SUM(words_added), 0) AS total
        FROM writing_log
        WHERE words_added > 0
        GROUP BY date
        HAVING total > 0
        ORDER BY date DESC
    """)
    abstract suspend fun getAllWritingDates(): List<DailyWordRow>

    // ── Wordmap ────────────────────────────────────────────────────────────────

    /**
     * Current word count per (book_id, folder_path) pair.
     * Reads notes.word_count — not writing_log — because this answers
     * "how many words exist here now", not "how many were written".
     */
    @Query("""
        SELECT book_id, folder_path,
               COALESCE(SUM(word_count), 0) AS total
        FROM notes
        GROUP BY book_id, folder_path
    """)
    abstract suspend fun getWordCountPerFolder(): List<FolderWordRow>
}
