package com.primaloptima.scribe.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteVersionDao {

    @Query("SELECT * FROM note_versions WHERE note_id = :noteId ORDER BY timestamp DESC")
    fun observeVersions(noteId: String): Flow<List<NoteVersion>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(version: NoteVersion)

    /**
     * Trim versions of a specific type down to [keep] most recent.
     * Called separately for "auto" and "manual" so each type has its own cap.
     */
    @Query("""
        DELETE FROM note_versions
        WHERE note_id = :noteId
          AND type = :type
          AND id NOT IN (
              SELECT id FROM note_versions
              WHERE note_id = :noteId AND type = :type
              ORDER BY timestamp DESC
              LIMIT :keep
          )
    """)
    suspend fun trimByType(noteId: String, type: String, keep: Int)

    @Query("DELETE FROM note_versions WHERE note_id = :noteId")
    suspend fun deleteByNoteId(noteId: String)
}
