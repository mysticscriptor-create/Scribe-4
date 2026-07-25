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

    @Query("SELECT * FROM note_versions WHERE note_id = :noteId ORDER BY timestamp DESC LIMIT 30")
    suspend fun getVersions(noteId: String): List<NoteVersion>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(version: NoteVersion)

    @Query("DELETE FROM note_versions WHERE note_id = :noteId AND id NOT IN (SELECT id FROM note_versions WHERE note_id = :noteId ORDER BY timestamp DESC LIMIT 30)")
    suspend fun trimOldVersions(noteId: String)

    @Query("DELETE FROM note_versions WHERE note_id = :noteId")
    suspend fun deleteByNoteId(noteId: String)
}
