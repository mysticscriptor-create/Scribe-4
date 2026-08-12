package com.primaloptima.scribe.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// Phase 1-B: projection for the one-time word-count backfill
data class NoteIdContent(val id: String, val content: String)

// GROUP BY result projections — one row per book
data class BookWordCount(val bookId: String, val total: Int)
data class BookNoteCount(val bookId: String, val count: Int)
data class BookFolderCount(val bookId: String, val count: Int)

@Dao
interface NoteDao {

    // ── Notes (all) ──────────────────────────────────────────────────────────

    @Query("SELECT * FROM notes ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<Note>>

    @Query("SELECT * FROM notes ORDER BY updated_at DESC")
    suspend fun getAll(): List<Note>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): Note?

    // ── Notes (scoped to book) ───────────────────────────────────────────────

    @Query("SELECT * FROM notes WHERE book_id = :bookId ORDER BY updated_at DESC")
    fun observeByBook(bookId: String): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE book_id = :bookId ORDER BY updated_at DESC")
    suspend fun getByBook(bookId: String): List<Note>

    @Query("SELECT * FROM notes WHERE book_id = :bookId AND folder_path = :folderPath ORDER BY updated_at DESC")
    suspend fun getByBookFolder(bookId: String, folderPath: String): List<Note>

    @Query("SELECT * FROM notes WHERE folder_path = :folderPath ORDER BY updated_at DESC")
    suspend fun getByFolder(folderPath: String): List<Note>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: Note)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notes: List<Note>)

    @Update
    suspend fun update(note: Note)

    @Query("UPDATE notes SET content = :content, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateContent(id: String, content: String, updatedAt: Long)

    // Phase 1-B: update content + word_count together — called from EditorViewModel.saveContent()
    @Query("UPDATE notes SET content = :content, word_count = :wordCount, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateContentAndWordCount(id: String, content: String, wordCount: Int, updatedAt: Long)

    // Phase 1-B: backfill — only touches rows that still have word_count = 0
    @Query("UPDATE notes SET word_count = :count WHERE id = :id AND word_count = 0")
    suspend fun updateWordCount(id: String, count: Int)

    // Phase 1-B: used by the startup backfill coroutine in ScribeApp
    @Query("SELECT id, content FROM notes")
    suspend fun getAllIdAndContent(): List<NoteIdContent>

    @Query("UPDATE notes SET name = :name, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateName(id: String, name: String, updatedAt: Long)

    @Query("UPDATE notes SET folder_path = :folderPath, updated_at = :updatedAt WHERE id = :id")
    suspend fun moveNote(id: String, folderPath: String, updatedAt: Long)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: String)

    @Delete
    suspend fun delete(note: Note)

    @Query("DELETE FROM notes WHERE folder_path = :folderPath")
    suspend fun deleteByFolder(folderPath: String)

    @Query("DELETE FROM notes WHERE book_id = :bookId AND folder_path = :folderPath")
    suspend fun deleteByBookFolder(bookId: String, folderPath: String)

    @Query("DELETE FROM notes WHERE book_id = :bookId")
    suspend fun deleteByBook(bookId: String)

    @Query("DELETE FROM notes WHERE external_uri IS NOT NULL")
    suspend fun deleteAllExternal()

    @Query("DELETE FROM notes")
    suspend fun deleteAll()

    @Query("SELECT * FROM notes WHERE (name LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%') ORDER BY updated_at DESC LIMIT 200")
    suspend fun search(query: String): List<Note>

    @Query("SELECT * FROM notes WHERE book_id = :bookId AND (name LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%') ORDER BY updated_at DESC LIMIT 200")
    suspend fun searchInBook(bookId: String, query: String): List<Note>

    // ── Aggregate counts (DB does the math — no Kotlin looping in UI) ────────
    //
    // These let HomeViewModel ask SQLite for a single number instead of loading
    // all notes into memory and summing in Kotlin. Only the rows for the
    // requested book/folder are touched, so editing one note doesn't re-sum
    // every other book.

    /** Total stored word count for notes in a specific folder inside a book. */
    @Query("SELECT COALESCE(SUM(word_count), 0) FROM notes WHERE book_id = :bookId AND folder_path = :folderPath")
    fun observeWordCountByFolder(bookId: String, folderPath: String): Flow<Int>

    /** Number of notes in a specific folder inside a book. */
    @Query("SELECT COUNT(*) FROM notes WHERE book_id = :bookId AND folder_path = :folderPath")
    fun observeNoteCountByFolder(bookId: String, folderPath: String): Flow<Int>

    /**
     * Vault-wide total word count — sum of word_count across all notes.
     * Used by HomeScreen sidebar panel to replace allNotes.sumOf { it.wordCount }.
     */
    @Query("SELECT COALESCE(SUM(word_count), 0) FROM notes")
    fun observeVaultWordCount(): Flow<Int>

    // ── GROUP BY aggregates (1 query for all books — replaces N per-book flows) ─

    /** Word count totals for every book in one query. */
    @Query("SELECT book_id as bookId, COALESCE(SUM(word_count), 0) as total FROM notes GROUP BY book_id")
    fun observeWordCountsByBook(): Flow<List<BookWordCount>>

    /** Note counts for every book in one query. */
    @Query("SELECT book_id as bookId, COUNT(*) as count FROM notes GROUP BY book_id")
    fun observeNoteCountsByBook(): Flow<List<BookNoteCount>>

    /** Non-root folder counts for every book in one query. */
    @Query("SELECT book_id as bookId, COUNT(*) as count FROM folders WHERE path != '/' GROUP BY book_id")
    fun observeFolderCountsByBook(): Flow<List<BookFolderCount>>

    // ── Folders ───────────────────────────────────────────────────────────────

    @Query("SELECT * FROM folders ORDER BY path ASC")
    fun observeFolders(): Flow<List<Folder>>

    @Query("SELECT * FROM folders ORDER BY path ASC")
    suspend fun getFolders(): List<Folder>

    @Query("SELECT * FROM folders WHERE book_id = :bookId ORDER BY path ASC")
    fun observeFoldersByBook(bookId: String): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE book_id = :bookId ORDER BY path ASC")
    suspend fun getFoldersByBook(bookId: String): List<Folder>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFolder(folder: Folder)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolders(folders: List<Folder>)

    @Query("DELETE FROM folders WHERE book_id = :bookId AND path = :path")
    suspend fun deleteFolder(bookId: String, path: String)

    @Query("DELETE FROM folders WHERE external_uri IS NOT NULL")
    suspend fun deleteAllExternalFolders()

    @Query("DELETE FROM folders WHERE book_id = :bookId AND path != '/'")
    suspend fun deleteNonRootFoldersByBook(bookId: String)

    @Query("DELETE FROM folders WHERE book_id = :bookId")
    suspend fun deleteFoldersByBook(bookId: String)

    @Query("DELETE FROM folders")
    suspend fun deleteAllFolders()

    // ── Fix 6: Reactive folder word totals (Wordmap) ─────────────────────────

    /**
     * Word count per (book_id, folder_path) pair — reads notes.word_count.
     * Fix 6: replaces the one-shot suspend getWordCountPerFolder() + LaunchedEffect
     * pattern in DetailedWordmapTab. Emits a fresh list whenever any note's
     * word_count changes, so the Wordmap stays current without manual refresh.
     * 
     * Exposed as HomeViewModel.folderWordTotals: StateFlow<Map<String,Int>>
     * so the composable uses collectAsStateWithLifecycle() instead of
     * LaunchedEffect + mutableStateOf.
     */
    @Query("""
        SELECT book_id, folder_path,
               COALESCE(SUM(word_count), 0) AS total
        FROM notes
        GROUP BY book_id, folder_path
    """)
    fun observeWordCountPerFolder(): Flow<List<FolderWordRow>>
}

    // NOTE: FolderWordRow is defined in StatRow.kt
