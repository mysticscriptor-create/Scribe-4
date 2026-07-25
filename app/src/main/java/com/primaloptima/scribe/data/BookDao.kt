package com.primaloptima.scribe.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query("SELECT * FROM books ORDER BY sort_order ASC, created_at ASC")
    fun observeAll(): Flow<List<Book>>

    @Query("SELECT * FROM books ORDER BY sort_order ASC, created_at ASC")
    suspend fun getAll(): List<Book>

    @Query("SELECT * FROM books WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): Book?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(book: Book)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(book: Book)

    @Update
    suspend fun update(book: Book)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE books SET updated_at = :updatedAt WHERE id = :id")
    suspend fun touch(id: String, updatedAt: Long)

    @Query("UPDATE books SET cover_uri = :uri, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateCover(id: String, uri: String?, updatedAt: Long)

    @Query("UPDATE books SET sort_order = :order WHERE id = :id")
    suspend fun updateSortOrder(id: String, order: Int)

    @Query("SELECT COUNT(*) FROM notes WHERE book_id = :bookId AND external_uri IS NULL")
    suspend fun localNoteCount(bookId: String): Int
}
