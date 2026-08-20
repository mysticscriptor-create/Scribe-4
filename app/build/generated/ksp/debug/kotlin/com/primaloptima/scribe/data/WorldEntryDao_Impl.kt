package com.primaloptima.scribe.`data`

import androidx.room.EntityDeleteOrUpdateAdapter
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
public class WorldEntryDao_Impl(
  __db: RoomDatabase,
) : WorldEntryDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfWorldEntry: EntityInsertAdapter<WorldEntry>

  private val __updateAdapterOfWorldEntry: EntityDeleteOrUpdateAdapter<WorldEntry>
  init {
    this.__db = __db
    this.__insertAdapterOfWorldEntry = object : EntityInsertAdapter<WorldEntry>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `world_entries` (`id`,`type`,`name`,`summary`,`fields_json`,`tags_json`,`image_uri`,`created_at`,`updated_at`) VALUES (?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: WorldEntry) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.type)
        statement.bindText(3, entity.name)
        statement.bindText(4, entity.summary)
        statement.bindText(5, entity.fieldsJson)
        statement.bindText(6, entity.tagsJson)
        val _tmpImageUri: String? = entity.imageUri
        if (_tmpImageUri == null) {
          statement.bindNull(7)
        } else {
          statement.bindText(7, _tmpImageUri)
        }
        statement.bindLong(8, entity.createdAt)
        statement.bindLong(9, entity.updatedAt)
      }
    }
    this.__updateAdapterOfWorldEntry = object : EntityDeleteOrUpdateAdapter<WorldEntry>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `world_entries` SET `id` = ?,`type` = ?,`name` = ?,`summary` = ?,`fields_json` = ?,`tags_json` = ?,`image_uri` = ?,`created_at` = ?,`updated_at` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: WorldEntry) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.type)
        statement.bindText(3, entity.name)
        statement.bindText(4, entity.summary)
        statement.bindText(5, entity.fieldsJson)
        statement.bindText(6, entity.tagsJson)
        val _tmpImageUri: String? = entity.imageUri
        if (_tmpImageUri == null) {
          statement.bindNull(7)
        } else {
          statement.bindText(7, _tmpImageUri)
        }
        statement.bindLong(8, entity.createdAt)
        statement.bindLong(9, entity.updatedAt)
        statement.bindText(10, entity.id)
      }
    }
  }

  public override suspend fun insert(entry: WorldEntry): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfWorldEntry.insert(_connection, entry)
  }

  public override suspend fun update(entry: WorldEntry): Unit = performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfWorldEntry.handle(_connection, entry)
  }

  public override fun observeAll(): Flow<List<WorldEntry>> {
    val _sql: String = "SELECT * FROM world_entries ORDER BY updated_at DESC"
    return createFlow(__db, false, arrayOf("world_entries")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfSummary: Int = getColumnIndexOrThrow(_stmt, "summary")
        val _columnIndexOfFieldsJson: Int = getColumnIndexOrThrow(_stmt, "fields_json")
        val _columnIndexOfTagsJson: Int = getColumnIndexOrThrow(_stmt, "tags_json")
        val _columnIndexOfImageUri: Int = getColumnIndexOrThrow(_stmt, "image_uri")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _result: MutableList<WorldEntry> = mutableListOf()
        while (_stmt.step()) {
          val _item: WorldEntry
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpType: String
          _tmpType = _stmt.getText(_columnIndexOfType)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpSummary: String
          _tmpSummary = _stmt.getText(_columnIndexOfSummary)
          val _tmpFieldsJson: String
          _tmpFieldsJson = _stmt.getText(_columnIndexOfFieldsJson)
          val _tmpTagsJson: String
          _tmpTagsJson = _stmt.getText(_columnIndexOfTagsJson)
          val _tmpImageUri: String?
          if (_stmt.isNull(_columnIndexOfImageUri)) {
            _tmpImageUri = null
          } else {
            _tmpImageUri = _stmt.getText(_columnIndexOfImageUri)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          _item = WorldEntry(_tmpId,_tmpType,_tmpName,_tmpSummary,_tmpFieldsJson,_tmpTagsJson,_tmpImageUri,_tmpCreatedAt,_tmpUpdatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getAllSync(): List<WorldEntry> {
    val _sql: String = "SELECT * FROM world_entries ORDER BY updated_at DESC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfSummary: Int = getColumnIndexOrThrow(_stmt, "summary")
        val _columnIndexOfFieldsJson: Int = getColumnIndexOrThrow(_stmt, "fields_json")
        val _columnIndexOfTagsJson: Int = getColumnIndexOrThrow(_stmt, "tags_json")
        val _columnIndexOfImageUri: Int = getColumnIndexOrThrow(_stmt, "image_uri")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _result: MutableList<WorldEntry> = mutableListOf()
        while (_stmt.step()) {
          val _item: WorldEntry
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpType: String
          _tmpType = _stmt.getText(_columnIndexOfType)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpSummary: String
          _tmpSummary = _stmt.getText(_columnIndexOfSummary)
          val _tmpFieldsJson: String
          _tmpFieldsJson = _stmt.getText(_columnIndexOfFieldsJson)
          val _tmpTagsJson: String
          _tmpTagsJson = _stmt.getText(_columnIndexOfTagsJson)
          val _tmpImageUri: String?
          if (_stmt.isNull(_columnIndexOfImageUri)) {
            _tmpImageUri = null
          } else {
            _tmpImageUri = _stmt.getText(_columnIndexOfImageUri)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          _item = WorldEntry(_tmpId,_tmpType,_tmpName,_tmpSummary,_tmpFieldsJson,_tmpTagsJson,_tmpImageUri,_tmpCreatedAt,_tmpUpdatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeByType(type: String): Flow<List<WorldEntry>> {
    val _sql: String = "SELECT * FROM world_entries WHERE type = ? ORDER BY updated_at DESC"
    return createFlow(__db, false, arrayOf("world_entries")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, type)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfSummary: Int = getColumnIndexOrThrow(_stmt, "summary")
        val _columnIndexOfFieldsJson: Int = getColumnIndexOrThrow(_stmt, "fields_json")
        val _columnIndexOfTagsJson: Int = getColumnIndexOrThrow(_stmt, "tags_json")
        val _columnIndexOfImageUri: Int = getColumnIndexOrThrow(_stmt, "image_uri")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _result: MutableList<WorldEntry> = mutableListOf()
        while (_stmt.step()) {
          val _item: WorldEntry
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpType: String
          _tmpType = _stmt.getText(_columnIndexOfType)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpSummary: String
          _tmpSummary = _stmt.getText(_columnIndexOfSummary)
          val _tmpFieldsJson: String
          _tmpFieldsJson = _stmt.getText(_columnIndexOfFieldsJson)
          val _tmpTagsJson: String
          _tmpTagsJson = _stmt.getText(_columnIndexOfTagsJson)
          val _tmpImageUri: String?
          if (_stmt.isNull(_columnIndexOfImageUri)) {
            _tmpImageUri = null
          } else {
            _tmpImageUri = _stmt.getText(_columnIndexOfImageUri)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          _item = WorldEntry(_tmpId,_tmpType,_tmpName,_tmpSummary,_tmpFieldsJson,_tmpTagsJson,_tmpImageUri,_tmpCreatedAt,_tmpUpdatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: String): WorldEntry? {
    val _sql: String = "SELECT * FROM world_entries WHERE id = ? LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfSummary: Int = getColumnIndexOrThrow(_stmt, "summary")
        val _columnIndexOfFieldsJson: Int = getColumnIndexOrThrow(_stmt, "fields_json")
        val _columnIndexOfTagsJson: Int = getColumnIndexOrThrow(_stmt, "tags_json")
        val _columnIndexOfImageUri: Int = getColumnIndexOrThrow(_stmt, "image_uri")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _result: WorldEntry?
        if (_stmt.step()) {
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpType: String
          _tmpType = _stmt.getText(_columnIndexOfType)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpSummary: String
          _tmpSummary = _stmt.getText(_columnIndexOfSummary)
          val _tmpFieldsJson: String
          _tmpFieldsJson = _stmt.getText(_columnIndexOfFieldsJson)
          val _tmpTagsJson: String
          _tmpTagsJson = _stmt.getText(_columnIndexOfTagsJson)
          val _tmpImageUri: String?
          if (_stmt.isNull(_columnIndexOfImageUri)) {
            _tmpImageUri = null
          } else {
            _tmpImageUri = _stmt.getText(_columnIndexOfImageUri)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          _result = WorldEntry(_tmpId,_tmpType,_tmpName,_tmpSummary,_tmpFieldsJson,_tmpTagsJson,_tmpImageUri,_tmpCreatedAt,_tmpUpdatedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteById(id: String) {
    val _sql: String = "DELETE FROM world_entries WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
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
