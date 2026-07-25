package com.primaloptima.scribe.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorldEntryDao {

    @Query("SELECT * FROM world_entries ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<WorldEntry>>

    @Query("SELECT * FROM world_entries ORDER BY updated_at DESC")
    suspend fun getAllSync(): List<WorldEntry>

    @Query("SELECT * FROM world_entries WHERE type = :type ORDER BY updated_at DESC")
    fun observeByType(type: String): Flow<List<WorldEntry>>

    @Query("SELECT * FROM world_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): WorldEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: WorldEntry)

    @Update
    suspend fun update(entry: WorldEntry)

    @Query("DELETE FROM world_entries WHERE id = :id")
    suspend fun deleteById(id: String)
}
