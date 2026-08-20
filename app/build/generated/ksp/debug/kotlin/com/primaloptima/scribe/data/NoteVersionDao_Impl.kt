package com.primaloptima.scribe.`data`

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import javax.`annotation`.processing.Generated
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class NoteVersionDao_Impl(
  __db: RoomDatabase,
) : NoteVersionDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfNoteVersion: EntityInsertAdapter<NoteVersion>
  init {
    this.__db = __db
    this.__insertAdapterOfNoteVersion = object : EntityInsertAdapter<NoteVersion>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `note_versions` (`id`,`note_id`,`content`,`word_count`,`timestamp`,`type`) VALUES (nullif(?, 0),?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: NoteVersion) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.noteId)
        statement.bindText(3, entity.content)
        statement.bindLong(4, entity.wordCount.toLong())
        statement.bindLong(5, entity.timestamp)
        statement.bindText(6, entity.type)
      }
    }
  }

  public override suspend fun insert(version: NoteVersion): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfNoteVersion.insert(_connection, version)
  }

  public override fun observeVersions(noteId: String): Flow<List<NoteVersion>> {
    val _sql: String = "SELECT * FROM note_versions WHERE note_id = ? ORDER BY timestamp DESC"
    return createFlow(__db, false, arrayOf("note_versions")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, noteId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfNoteId: Int = getColumnIndexOrThrow(_stmt, "note_id")
        val _columnIndexOfContent: Int = getColumnIndexOrThrow(_stmt, "content")
        val _columnIndexOfWordCount: Int = getColumnIndexOrThrow(_stmt, "word_count")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _result: MutableList<NoteVersion> = mutableListOf()
        while (_stmt.step()) {
          val _item: NoteVersion
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpNoteId: String
          _tmpNoteId = _stmt.getText(_columnIndexOfNoteId)
          val _tmpContent: String
          _tmpContent = _stmt.getText(_columnIndexOfContent)
          val _tmpWordCount: Int
          _tmpWordCount = _stmt.getLong(_columnIndexOfWordCount).toInt()
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
          val _tmpType: String
          _tmpType = _stmt.getText(_columnIndexOfType)
          _item = NoteVersion(_tmpId,_tmpNoteId,_tmpContent,_tmpWordCount,_tmpTimestamp,_tmpType)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun trimByType(
    noteId: String,
    type: String,
    keep: Int,
  ) {
    val _sql: String = """
        |
        |        DELETE FROM note_versions
        |        WHERE note_id = ?
        |          AND type = ?
        |          AND id NOT IN (
        |              SELECT id FROM note_versions
        |              WHERE note_id = ? AND type = ?
        |              ORDER BY timestamp DESC
        |              LIMIT ?
        |          )
        |    
        """.trimMargin()
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, noteId)
        _argIndex = 2
        _stmt.bindText(_argIndex, type)
        _argIndex = 3
        _stmt.bindText(_argIndex, noteId)
        _argIndex = 4
        _stmt.bindText(_argIndex, type)
        _argIndex = 5
        _stmt.bindLong(_argIndex, keep.toLong())
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteByNoteId(noteId: String) {
    val _sql: String = "DELETE FROM note_versions WHERE note_id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, noteId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
