package com.primaloptima.scribe.`data`

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import javax.`annotation`.processing.Generated
import kotlin.Boolean
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
public class NoteDao_Impl(
  __db: RoomDatabase,
) : NoteDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfNote: EntityInsertAdapter<Note>

  private val __insertAdapterOfFolder: EntityInsertAdapter<Folder>

  private val __insertAdapterOfFolder_1: EntityInsertAdapter<Folder>

  private val __deleteAdapterOfNote: EntityDeleteOrUpdateAdapter<Note>

  private val __updateAdapterOfNote: EntityDeleteOrUpdateAdapter<Note>
  init {
    this.__db = __db
    this.__insertAdapterOfNote = object : EntityInsertAdapter<Note>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `notes` (`id`,`name`,`book_id`,`folder_path`,`ext`,`content`,`word_count`,`created_at`,`updated_at`,`external_uri`,`loaded`,`formats_json`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: Note) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.name)
        statement.bindText(3, entity.bookId)
        statement.bindText(4, entity.folderPath)
        statement.bindText(5, entity.ext)
        statement.bindText(6, entity.content)
        statement.bindLong(7, entity.wordCount.toLong())
        statement.bindLong(8, entity.createdAt)
        statement.bindLong(9, entity.updatedAt)
        val _tmpExternalUri: String? = entity.externalUri
        if (_tmpExternalUri == null) {
          statement.bindNull(10)
        } else {
          statement.bindText(10, _tmpExternalUri)
        }
        val _tmp: Int = if (entity.loaded) 1 else 0
        statement.bindLong(11, _tmp.toLong())
        val _tmpFormatsJson: String? = entity.formatsJson
        if (_tmpFormatsJson == null) {
          statement.bindNull(12)
        } else {
          statement.bindText(12, _tmpFormatsJson)
        }
      }
    }
    this.__insertAdapterOfFolder = object : EntityInsertAdapter<Folder>() {
      protected override fun createQuery(): String = "INSERT OR IGNORE INTO `folders` (`book_id`,`path`,`external_uri`) VALUES (?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: Folder) {
        statement.bindText(1, entity.bookId)
        statement.bindText(2, entity.path)
        val _tmpExternalUri: String? = entity.externalUri
        if (_tmpExternalUri == null) {
          statement.bindNull(3)
        } else {
          statement.bindText(3, _tmpExternalUri)
        }
      }
    }
    this.__insertAdapterOfFolder_1 = object : EntityInsertAdapter<Folder>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `folders` (`book_id`,`path`,`external_uri`) VALUES (?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: Folder) {
        statement.bindText(1, entity.bookId)
        statement.bindText(2, entity.path)
        val _tmpExternalUri: String? = entity.externalUri
        if (_tmpExternalUri == null) {
          statement.bindNull(3)
        } else {
          statement.bindText(3, _tmpExternalUri)
        }
      }
    }
    this.__deleteAdapterOfNote = object : EntityDeleteOrUpdateAdapter<Note>() {
      protected override fun createQuery(): String = "DELETE FROM `notes` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: Note) {
        statement.bindText(1, entity.id)
      }
    }
    this.__updateAdapterOfNote = object : EntityDeleteOrUpdateAdapter<Note>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `notes` SET `id` = ?,`name` = ?,`book_id` = ?,`folder_path` = ?,`ext` = ?,`content` = ?,`word_count` = ?,`created_at` = ?,`updated_at` = ?,`external_uri` = ?,`loaded` = ?,`formats_json` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: Note) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.name)
        statement.bindText(3, entity.bookId)
        statement.bindText(4, entity.folderPath)
        statement.bindText(5, entity.ext)
        statement.bindText(6, entity.content)
        statement.bindLong(7, entity.wordCount.toLong())
        statement.bindLong(8, entity.createdAt)
        statement.bindLong(9, entity.updatedAt)
        val _tmpExternalUri: String? = entity.externalUri
        if (_tmpExternalUri == null) {
          statement.bindNull(10)
        } else {
          statement.bindText(10, _tmpExternalUri)
        }
        val _tmp: Int = if (entity.loaded) 1 else 0
        statement.bindLong(11, _tmp.toLong())
        val _tmpFormatsJson: String? = entity.formatsJson
        if (_tmpFormatsJson == null) {
          statement.bindNull(12)
        } else {
          statement.bindText(12, _tmpFormatsJson)
        }
        statement.bindText(13, entity.id)
      }
    }
  }

  public override suspend fun insert(note: Note): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfNote.insert(_connection, note)
  }

  public override suspend fun insertAll(notes: List<Note>): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfNote.insert(_connection, notes)
  }

  public override suspend fun insertFolder(folder: Folder): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfFolder.insert(_connection, folder)
  }

  public override suspend fun insertFolders(folders: List<Folder>): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfFolder_1.insert(_connection, folders)
  }

  public override suspend fun delete(note: Note): Unit = performSuspending(__db, false, true) { _connection ->
    __deleteAdapterOfNote.handle(_connection, note)
  }

  public override suspend fun update(note: Note): Unit = performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfNote.handle(_connection, note)
  }

  public override fun observeAll(): Flow<List<Note>> {
    val _sql: String = "SELECT * FROM notes ORDER BY updated_at DESC"
    return createFlow(__db, false, arrayOf("notes")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfBookId: Int = getColumnIndexOrThrow(_stmt, "book_id")
        val _columnIndexOfFolderPath: Int = getColumnIndexOrThrow(_stmt, "folder_path")
        val _columnIndexOfExt: Int = getColumnIndexOrThrow(_stmt, "ext")
        val _columnIndexOfContent: Int = getColumnIndexOrThrow(_stmt, "content")
        val _columnIndexOfWordCount: Int = getColumnIndexOrThrow(_stmt, "word_count")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfExternalUri: Int = getColumnIndexOrThrow(_stmt, "external_uri")
        val _columnIndexOfLoaded: Int = getColumnIndexOrThrow(_stmt, "loaded")
        val _columnIndexOfFormatsJson: Int = getColumnIndexOrThrow(_stmt, "formats_json")
        val _result: MutableList<Note> = mutableListOf()
        while (_stmt.step()) {
          val _item: Note
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpBookId: String
          _tmpBookId = _stmt.getText(_columnIndexOfBookId)
          val _tmpFolderPath: String
          _tmpFolderPath = _stmt.getText(_columnIndexOfFolderPath)
          val _tmpExt: String
          _tmpExt = _stmt.getText(_columnIndexOfExt)
          val _tmpContent: String
          _tmpContent = _stmt.getText(_columnIndexOfContent)
          val _tmpWordCount: Int
          _tmpWordCount = _stmt.getLong(_columnIndexOfWordCount).toInt()
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmpExternalUri: String?
          if (_stmt.isNull(_columnIndexOfExternalUri)) {
            _tmpExternalUri = null
          } else {
            _tmpExternalUri = _stmt.getText(_columnIndexOfExternalUri)
          }
          val _tmpLoaded: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfLoaded).toInt()
          _tmpLoaded = _tmp != 0
          val _tmpFormatsJson: String?
          if (_stmt.isNull(_columnIndexOfFormatsJson)) {
            _tmpFormatsJson = null
          } else {
            _tmpFormatsJson = _stmt.getText(_columnIndexOfFormatsJson)
          }
          _item = Note(_tmpId,_tmpName,_tmpBookId,_tmpFolderPath,_tmpExt,_tmpContent,_tmpWordCount,_tmpCreatedAt,_tmpUpdatedAt,_tmpExternalUri,_tmpLoaded,_tmpFormatsJson)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getAll(): List<Note> {
    val _sql: String = "SELECT * FROM notes ORDER BY updated_at DESC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfBookId: Int = getColumnIndexOrThrow(_stmt, "book_id")
        val _columnIndexOfFolderPath: Int = getColumnIndexOrThrow(_stmt, "folder_path")
        val _columnIndexOfExt: Int = getColumnIndexOrThrow(_stmt, "ext")
        val _columnIndexOfContent: Int = getColumnIndexOrThrow(_stmt, "content")
        val _columnIndexOfWordCount: Int = getColumnIndexOrThrow(_stmt, "word_count")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfExternalUri: Int = getColumnIndexOrThrow(_stmt, "external_uri")
        val _columnIndexOfLoaded: Int = getColumnIndexOrThrow(_stmt, "loaded")
        val _columnIndexOfFormatsJson: Int = getColumnIndexOrThrow(_stmt, "formats_json")
        val _result: MutableList<Note> = mutableListOf()
        while (_stmt.step()) {
          val _item: Note
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpBookId: String
          _tmpBookId = _stmt.getText(_columnIndexOfBookId)
          val _tmpFolderPath: String
          _tmpFolderPath = _stmt.getText(_columnIndexOfFolderPath)
          val _tmpExt: String
          _tmpExt = _stmt.getText(_columnIndexOfExt)
          val _tmpContent: String
          _tmpContent = _stmt.getText(_columnIndexOfContent)
          val _tmpWordCount: Int
          _tmpWordCount = _stmt.getLong(_columnIndexOfWordCount).toInt()
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmpExternalUri: String?
          if (_stmt.isNull(_columnIndexOfExternalUri)) {
            _tmpExternalUri = null
          } else {
            _tmpExternalUri = _stmt.getText(_columnIndexOfExternalUri)
          }
          val _tmpLoaded: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfLoaded).toInt()
          _tmpLoaded = _tmp != 0
          val _tmpFormatsJson: String?
          if (_stmt.isNull(_columnIndexOfFormatsJson)) {
            _tmpFormatsJson = null
          } else {
            _tmpFormatsJson = _stmt.getText(_columnIndexOfFormatsJson)
          }
          _item = Note(_tmpId,_tmpName,_tmpBookId,_tmpFolderPath,_tmpExt,_tmpContent,_tmpWordCount,_tmpCreatedAt,_tmpUpdatedAt,_tmpExternalUri,_tmpLoaded,_tmpFormatsJson)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: String): Note? {
    val _sql: String = "SELECT * FROM notes WHERE id = ? LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfBookId: Int = getColumnIndexOrThrow(_stmt, "book_id")
        val _columnIndexOfFolderPath: Int = getColumnIndexOrThrow(_stmt, "folder_path")
        val _columnIndexOfExt: Int = getColumnIndexOrThrow(_stmt, "ext")
        val _columnIndexOfContent: Int = getColumnIndexOrThrow(_stmt, "content")
        val _columnIndexOfWordCount: Int = getColumnIndexOrThrow(_stmt, "word_count")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfExternalUri: Int = getColumnIndexOrThrow(_stmt, "external_uri")
        val _columnIndexOfLoaded: Int = getColumnIndexOrThrow(_stmt, "loaded")
        val _columnIndexOfFormatsJson: Int = getColumnIndexOrThrow(_stmt, "formats_json")
        val _result: Note?
        if (_stmt.step()) {
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpBookId: String
          _tmpBookId = _stmt.getText(_columnIndexOfBookId)
          val _tmpFolderPath: String
          _tmpFolderPath = _stmt.getText(_columnIndexOfFolderPath)
          val _tmpExt: String
          _tmpExt = _stmt.getText(_columnIndexOfExt)
          val _tmpContent: String
          _tmpContent = _stmt.getText(_columnIndexOfContent)
          val _tmpWordCount: Int
          _tmpWordCount = _stmt.getLong(_columnIndexOfWordCount).toInt()
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmpExternalUri: String?
          if (_stmt.isNull(_columnIndexOfExternalUri)) {
            _tmpExternalUri = null
          } else {
            _tmpExternalUri = _stmt.getText(_columnIndexOfExternalUri)
          }
          val _tmpLoaded: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfLoaded).toInt()
          _tmpLoaded = _tmp != 0
          val _tmpFormatsJson: String?
          if (_stmt.isNull(_columnIndexOfFormatsJson)) {
            _tmpFormatsJson = null
          } else {
            _tmpFormatsJson = _stmt.getText(_columnIndexOfFormatsJson)
          }
          _result = Note(_tmpId,_tmpName,_tmpBookId,_tmpFolderPath,_tmpExt,_tmpContent,_tmpWordCount,_tmpCreatedAt,_tmpUpdatedAt,_tmpExternalUri,_tmpLoaded,_tmpFormatsJson)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeByBook(bookId: String): Flow<List<Note>> {
    val _sql: String = "SELECT * FROM notes WHERE book_id = ? ORDER BY updated_at DESC"
    return createFlow(__db, false, arrayOf("notes")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, bookId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfBookId: Int = getColumnIndexOrThrow(_stmt, "book_id")
        val _columnIndexOfFolderPath: Int = getColumnIndexOrThrow(_stmt, "folder_path")
        val _columnIndexOfExt: Int = getColumnIndexOrThrow(_stmt, "ext")
        val _columnIndexOfContent: Int = getColumnIndexOrThrow(_stmt, "content")
        val _columnIndexOfWordCount: Int = getColumnIndexOrThrow(_stmt, "word_count")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfExternalUri: Int = getColumnIndexOrThrow(_stmt, "external_uri")
        val _columnIndexOfLoaded: Int = getColumnIndexOrThrow(_stmt, "loaded")
        val _columnIndexOfFormatsJson: Int = getColumnIndexOrThrow(_stmt, "formats_json")
        val _result: MutableList<Note> = mutableListOf()
        while (_stmt.step()) {
          val _item: Note
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpBookId: String
          _tmpBookId = _stmt.getText(_columnIndexOfBookId)
          val _tmpFolderPath: String
          _tmpFolderPath = _stmt.getText(_columnIndexOfFolderPath)
          val _tmpExt: String
          _tmpExt = _stmt.getText(_columnIndexOfExt)
          val _tmpContent: String
          _tmpContent = _stmt.getText(_columnIndexOfContent)
          val _tmpWordCount: Int
          _tmpWordCount = _stmt.getLong(_columnIndexOfWordCount).toInt()
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmpExternalUri: String?
          if (_stmt.isNull(_columnIndexOfExternalUri)) {
            _tmpExternalUri = null
          } else {
            _tmpExternalUri = _stmt.getText(_columnIndexOfExternalUri)
          }
          val _tmpLoaded: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfLoaded).toInt()
          _tmpLoaded = _tmp != 0
          val _tmpFormatsJson: String?
          if (_stmt.isNull(_columnIndexOfFormatsJson)) {
            _tmpFormatsJson = null
          } else {
            _tmpFormatsJson = _stmt.getText(_columnIndexOfFormatsJson)
          }
          _item = Note(_tmpId,_tmpName,_tmpBookId,_tmpFolderPath,_tmpExt,_tmpContent,_tmpWordCount,_tmpCreatedAt,_tmpUpdatedAt,_tmpExternalUri,_tmpLoaded,_tmpFormatsJson)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getByBook(bookId: String): List<Note> {
    val _sql: String = "SELECT * FROM notes WHERE book_id = ? ORDER BY updated_at DESC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, bookId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfBookId: Int = getColumnIndexOrThrow(_stmt, "book_id")
        val _columnIndexOfFolderPath: Int = getColumnIndexOrThrow(_stmt, "folder_path")
        val _columnIndexOfExt: Int = getColumnIndexOrThrow(_stmt, "ext")
        val _columnIndexOfContent: Int = getColumnIndexOrThrow(_stmt, "content")
        val _columnIndexOfWordCount: Int = getColumnIndexOrThrow(_stmt, "word_count")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfExternalUri: Int = getColumnIndexOrThrow(_stmt, "external_uri")
        val _columnIndexOfLoaded: Int = getColumnIndexOrThrow(_stmt, "loaded")
        val _columnIndexOfFormatsJson: Int = getColumnIndexOrThrow(_stmt, "formats_json")
        val _result: MutableList<Note> = mutableListOf()
        while (_stmt.step()) {
          val _item: Note
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpBookId: String
          _tmpBookId = _stmt.getText(_columnIndexOfBookId)
          val _tmpFolderPath: String
          _tmpFolderPath = _stmt.getText(_columnIndexOfFolderPath)
          val _tmpExt: String
          _tmpExt = _stmt.getText(_columnIndexOfExt)
          val _tmpContent: String
          _tmpContent = _stmt.getText(_columnIndexOfContent)
          val _tmpWordCount: Int
          _tmpWordCount = _stmt.getLong(_columnIndexOfWordCount).toInt()
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmpExternalUri: String?
          if (_stmt.isNull(_columnIndexOfExternalUri)) {
            _tmpExternalUri = null
          } else {
            _tmpExternalUri = _stmt.getText(_columnIndexOfExternalUri)
          }
          val _tmpLoaded: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfLoaded).toInt()
          _tmpLoaded = _tmp != 0
          val _tmpFormatsJson: String?
          if (_stmt.isNull(_columnIndexOfFormatsJson)) {
            _tmpFormatsJson = null
          } else {
            _tmpFormatsJson = _stmt.getText(_columnIndexOfFormatsJson)
          }
          _item = Note(_tmpId,_tmpName,_tmpBookId,_tmpFolderPath,_tmpExt,_tmpContent,_tmpWordCount,_tmpCreatedAt,_tmpUpdatedAt,_tmpExternalUri,_tmpLoaded,_tmpFormatsJson)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getByBookFolder(bookId: String, folderPath: String): List<Note> {
    val _sql: String = "SELECT * FROM notes WHERE book_id = ? AND folder_path = ? ORDER BY updated_at DESC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, bookId)
        _argIndex = 2
        _stmt.bindText(_argIndex, folderPath)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfBookId: Int = getColumnIndexOrThrow(_stmt, "book_id")
        val _columnIndexOfFolderPath: Int = getColumnIndexOrThrow(_stmt, "folder_path")
        val _columnIndexOfExt: Int = getColumnIndexOrThrow(_stmt, "ext")
        val _columnIndexOfContent: Int = getColumnIndexOrThrow(_stmt, "content")
        val _columnIndexOfWordCount: Int = getColumnIndexOrThrow(_stmt, "word_count")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfExternalUri: Int = getColumnIndexOrThrow(_stmt, "external_uri")
        val _columnIndexOfLoaded: Int = getColumnIndexOrThrow(_stmt, "loaded")
        val _columnIndexOfFormatsJson: Int = getColumnIndexOrThrow(_stmt, "formats_json")
        val _result: MutableList<Note> = mutableListOf()
        while (_stmt.step()) {
          val _item: Note
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpBookId: String
          _tmpBookId = _stmt.getText(_columnIndexOfBookId)
          val _tmpFolderPath: String
          _tmpFolderPath = _stmt.getText(_columnIndexOfFolderPath)
          val _tmpExt: String
          _tmpExt = _stmt.getText(_columnIndexOfExt)
          val _tmpContent: String
          _tmpContent = _stmt.getText(_columnIndexOfContent)
          val _tmpWordCount: Int
          _tmpWordCount = _stmt.getLong(_columnIndexOfWordCount).toInt()
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmpExternalUri: String?
          if (_stmt.isNull(_columnIndexOfExternalUri)) {
            _tmpExternalUri = null
          } else {
            _tmpExternalUri = _stmt.getText(_columnIndexOfExternalUri)
          }
          val _tmpLoaded: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfLoaded).toInt()
          _tmpLoaded = _tmp != 0
          val _tmpFormatsJson: String?
          if (_stmt.isNull(_columnIndexOfFormatsJson)) {
            _tmpFormatsJson = null
          } else {
            _tmpFormatsJson = _stmt.getText(_columnIndexOfFormatsJson)
          }
          _item = Note(_tmpId,_tmpName,_tmpBookId,_tmpFolderPath,_tmpExt,_tmpContent,_tmpWordCount,_tmpCreatedAt,_tmpUpdatedAt,_tmpExternalUri,_tmpLoaded,_tmpFormatsJson)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getByFolder(folderPath: String): List<Note> {
    val _sql: String = "SELECT * FROM notes WHERE folder_path = ? ORDER BY updated_at DESC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, folderPath)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfBookId: Int = getColumnIndexOrThrow(_stmt, "book_id")
        val _columnIndexOfFolderPath: Int = getColumnIndexOrThrow(_stmt, "folder_path")
        val _columnIndexOfExt: Int = getColumnIndexOrThrow(_stmt, "ext")
        val _columnIndexOfContent: Int = getColumnIndexOrThrow(_stmt, "content")
        val _columnIndexOfWordCount: Int = getColumnIndexOrThrow(_stmt, "word_count")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfExternalUri: Int = getColumnIndexOrThrow(_stmt, "external_uri")
        val _columnIndexOfLoaded: Int = getColumnIndexOrThrow(_stmt, "loaded")
        val _columnIndexOfFormatsJson: Int = getColumnIndexOrThrow(_stmt, "formats_json")
        val _result: MutableList<Note> = mutableListOf()
        while (_stmt.step()) {
          val _item: Note
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpBookId: String
          _tmpBookId = _stmt.getText(_columnIndexOfBookId)
          val _tmpFolderPath: String
          _tmpFolderPath = _stmt.getText(_columnIndexOfFolderPath)
          val _tmpExt: String
          _tmpExt = _stmt.getText(_columnIndexOfExt)
          val _tmpContent: String
          _tmpContent = _stmt.getText(_columnIndexOfContent)
          val _tmpWordCount: Int
          _tmpWordCount = _stmt.getLong(_columnIndexOfWordCount).toInt()
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmpExternalUri: String?
          if (_stmt.isNull(_columnIndexOfExternalUri)) {
            _tmpExternalUri = null
          } else {
            _tmpExternalUri = _stmt.getText(_columnIndexOfExternalUri)
          }
          val _tmpLoaded: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfLoaded).toInt()
          _tmpLoaded = _tmp != 0
          val _tmpFormatsJson: String?
          if (_stmt.isNull(_columnIndexOfFormatsJson)) {
            _tmpFormatsJson = null
          } else {
            _tmpFormatsJson = _stmt.getText(_columnIndexOfFormatsJson)
          }
          _item = Note(_tmpId,_tmpName,_tmpBookId,_tmpFolderPath,_tmpExt,_tmpContent,_tmpWordCount,_tmpCreatedAt,_tmpUpdatedAt,_tmpExternalUri,_tmpLoaded,_tmpFormatsJson)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getAllIdAndContent(): List<NoteIdContent> {
    val _sql: String = "SELECT id, content FROM notes"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = 0
        val _columnIndexOfContent: Int = 1
        val _result: MutableList<NoteIdContent> = mutableListOf()
        while (_stmt.step()) {
          val _item: NoteIdContent
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpContent: String
          _tmpContent = _stmt.getText(_columnIndexOfContent)
          _item = NoteIdContent(_tmpId,_tmpContent)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun search(query: String): List<Note> {
    val _sql: String = "SELECT * FROM notes WHERE (name LIKE '%' || ? || '%' OR content LIKE '%' || ? || '%') ORDER BY updated_at DESC LIMIT 200"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, query)
        _argIndex = 2
        _stmt.bindText(_argIndex, query)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfBookId: Int = getColumnIndexOrThrow(_stmt, "book_id")
        val _columnIndexOfFolderPath: Int = getColumnIndexOrThrow(_stmt, "folder_path")
        val _columnIndexOfExt: Int = getColumnIndexOrThrow(_stmt, "ext")
        val _columnIndexOfContent: Int = getColumnIndexOrThrow(_stmt, "content")
        val _columnIndexOfWordCount: Int = getColumnIndexOrThrow(_stmt, "word_count")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfExternalUri: Int = getColumnIndexOrThrow(_stmt, "external_uri")
        val _columnIndexOfLoaded: Int = getColumnIndexOrThrow(_stmt, "loaded")
        val _columnIndexOfFormatsJson: Int = getColumnIndexOrThrow(_stmt, "formats_json")
        val _result: MutableList<Note> = mutableListOf()
        while (_stmt.step()) {
          val _item: Note
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpBookId: String
          _tmpBookId = _stmt.getText(_columnIndexOfBookId)
          val _tmpFolderPath: String
          _tmpFolderPath = _stmt.getText(_columnIndexOfFolderPath)
          val _tmpExt: String
          _tmpExt = _stmt.getText(_columnIndexOfExt)
          val _tmpContent: String
          _tmpContent = _stmt.getText(_columnIndexOfContent)
          val _tmpWordCount: Int
          _tmpWordCount = _stmt.getLong(_columnIndexOfWordCount).toInt()
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmpExternalUri: String?
          if (_stmt.isNull(_columnIndexOfExternalUri)) {
            _tmpExternalUri = null
          } else {
            _tmpExternalUri = _stmt.getText(_columnIndexOfExternalUri)
          }
          val _tmpLoaded: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfLoaded).toInt()
          _tmpLoaded = _tmp != 0
          val _tmpFormatsJson: String?
          if (_stmt.isNull(_columnIndexOfFormatsJson)) {
            _tmpFormatsJson = null
          } else {
            _tmpFormatsJson = _stmt.getText(_columnIndexOfFormatsJson)
          }
          _item = Note(_tmpId,_tmpName,_tmpBookId,_tmpFolderPath,_tmpExt,_tmpContent,_tmpWordCount,_tmpCreatedAt,_tmpUpdatedAt,_tmpExternalUri,_tmpLoaded,_tmpFormatsJson)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun searchInBook(bookId: String, query: String): List<Note> {
    val _sql: String = "SELECT * FROM notes WHERE book_id = ? AND (name LIKE '%' || ? || '%' OR content LIKE '%' || ? || '%') ORDER BY updated_at DESC LIMIT 200"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, bookId)
        _argIndex = 2
        _stmt.bindText(_argIndex, query)
        _argIndex = 3
        _stmt.bindText(_argIndex, query)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfBookId: Int = getColumnIndexOrThrow(_stmt, "book_id")
        val _columnIndexOfFolderPath: Int = getColumnIndexOrThrow(_stmt, "folder_path")
        val _columnIndexOfExt: Int = getColumnIndexOrThrow(_stmt, "ext")
        val _columnIndexOfContent: Int = getColumnIndexOrThrow(_stmt, "content")
        val _columnIndexOfWordCount: Int = getColumnIndexOrThrow(_stmt, "word_count")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfExternalUri: Int = getColumnIndexOrThrow(_stmt, "external_uri")
        val _columnIndexOfLoaded: Int = getColumnIndexOrThrow(_stmt, "loaded")
        val _columnIndexOfFormatsJson: Int = getColumnIndexOrThrow(_stmt, "formats_json")
        val _result: MutableList<Note> = mutableListOf()
        while (_stmt.step()) {
          val _item: Note
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpBookId: String
          _tmpBookId = _stmt.getText(_columnIndexOfBookId)
          val _tmpFolderPath: String
          _tmpFolderPath = _stmt.getText(_columnIndexOfFolderPath)
          val _tmpExt: String
          _tmpExt = _stmt.getText(_columnIndexOfExt)
          val _tmpContent: String
          _tmpContent = _stmt.getText(_columnIndexOfContent)
          val _tmpWordCount: Int
          _tmpWordCount = _stmt.getLong(_columnIndexOfWordCount).toInt()
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmpExternalUri: String?
          if (_stmt.isNull(_columnIndexOfExternalUri)) {
            _tmpExternalUri = null
          } else {
            _tmpExternalUri = _stmt.getText(_columnIndexOfExternalUri)
          }
          val _tmpLoaded: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfLoaded).toInt()
          _tmpLoaded = _tmp != 0
          val _tmpFormatsJson: String?
          if (_stmt.isNull(_columnIndexOfFormatsJson)) {
            _tmpFormatsJson = null
          } else {
            _tmpFormatsJson = _stmt.getText(_columnIndexOfFormatsJson)
          }
          _item = Note(_tmpId,_tmpName,_tmpBookId,_tmpFolderPath,_tmpExt,_tmpContent,_tmpWordCount,_tmpCreatedAt,_tmpUpdatedAt,_tmpExternalUri,_tmpLoaded,_tmpFormatsJson)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeWordCountByFolder(bookId: String, folderPath: String): Flow<Int> {
    val _sql: String = "SELECT COALESCE(SUM(word_count), 0) FROM notes WHERE book_id = ? AND folder_path = ?"
    return createFlow(__db, false, arrayOf("notes")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, bookId)
        _argIndex = 2
        _stmt.bindText(_argIndex, folderPath)
        val _result: Int
        if (_stmt.step()) {
          val _tmp: Int
          _tmp = _stmt.getLong(0).toInt()
          _result = _tmp
        } else {
          _result = 0
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeNoteCountByFolder(bookId: String, folderPath: String): Flow<Int> {
    val _sql: String = "SELECT COUNT(*) FROM notes WHERE book_id = ? AND folder_path = ?"
    return createFlow(__db, false, arrayOf("notes")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, bookId)
        _argIndex = 2
        _stmt.bindText(_argIndex, folderPath)
        val _result: Int
        if (_stmt.step()) {
          val _tmp: Int
          _tmp = _stmt.getLong(0).toInt()
          _result = _tmp
        } else {
          _result = 0
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeVaultWordCount(): Flow<Int> {
    val _sql: String = "SELECT COALESCE(SUM(word_count), 0) FROM notes"
    return createFlow(__db, false, arrayOf("notes")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _result: Int
        if (_stmt.step()) {
          val _tmp: Int
          _tmp = _stmt.getLong(0).toInt()
          _result = _tmp
        } else {
          _result = 0
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeWordCountsByBook(): Flow<List<BookWordCount>> {
    val _sql: String = "SELECT book_id as bookId, COALESCE(SUM(word_count), 0) as total FROM notes GROUP BY book_id"
    return createFlow(__db, false, arrayOf("notes")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfBookId: Int = 0
        val _columnIndexOfTotal: Int = 1
        val _result: MutableList<BookWordCount> = mutableListOf()
        while (_stmt.step()) {
          val _item: BookWordCount
          val _tmpBookId: String
          _tmpBookId = _stmt.getText(_columnIndexOfBookId)
          val _tmpTotal: Int
          _tmpTotal = _stmt.getLong(_columnIndexOfTotal).toInt()
          _item = BookWordCount(_tmpBookId,_tmpTotal)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeNoteCountsByBook(): Flow<List<BookNoteCount>> {
    val _sql: String = "SELECT book_id as bookId, COUNT(*) as count FROM notes GROUP BY book_id"
    return createFlow(__db, false, arrayOf("notes")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfBookId: Int = 0
        val _columnIndexOfCount: Int = 1
        val _result: MutableList<BookNoteCount> = mutableListOf()
        while (_stmt.step()) {
          val _item: BookNoteCount
          val _tmpBookId: String
          _tmpBookId = _stmt.getText(_columnIndexOfBookId)
          val _tmpCount: Int
          _tmpCount = _stmt.getLong(_columnIndexOfCount).toInt()
          _item = BookNoteCount(_tmpBookId,_tmpCount)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeFolderCountsByBook(): Flow<List<BookFolderCount>> {
    val _sql: String = "SELECT book_id as bookId, COUNT(*) as count FROM folders WHERE path != '/' GROUP BY book_id"
    return createFlow(__db, false, arrayOf("folders")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfBookId: Int = 0
        val _columnIndexOfCount: Int = 1
        val _result: MutableList<BookFolderCount> = mutableListOf()
        while (_stmt.step()) {
          val _item: BookFolderCount
          val _tmpBookId: String
          _tmpBookId = _stmt.getText(_columnIndexOfBookId)
          val _tmpCount: Int
          _tmpCount = _stmt.getLong(_columnIndexOfCount).toInt()
          _item = BookFolderCount(_tmpBookId,_tmpCount)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeFolders(): Flow<List<Folder>> {
    val _sql: String = "SELECT * FROM folders ORDER BY path ASC"
    return createFlow(__db, false, arrayOf("folders")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfBookId: Int = getColumnIndexOrThrow(_stmt, "book_id")
        val _columnIndexOfPath: Int = getColumnIndexOrThrow(_stmt, "path")
        val _columnIndexOfExternalUri: Int = getColumnIndexOrThrow(_stmt, "external_uri")
        val _result: MutableList<Folder> = mutableListOf()
        while (_stmt.step()) {
          val _item: Folder
          val _tmpBookId: String
          _tmpBookId = _stmt.getText(_columnIndexOfBookId)
          val _tmpPath: String
          _tmpPath = _stmt.getText(_columnIndexOfPath)
          val _tmpExternalUri: String?
          if (_stmt.isNull(_columnIndexOfExternalUri)) {
            _tmpExternalUri = null
          } else {
            _tmpExternalUri = _stmt.getText(_columnIndexOfExternalUri)
          }
          _item = Folder(_tmpBookId,_tmpPath,_tmpExternalUri)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getFolders(): List<Folder> {
    val _sql: String = "SELECT * FROM folders ORDER BY path ASC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfBookId: Int = getColumnIndexOrThrow(_stmt, "book_id")
        val _columnIndexOfPath: Int = getColumnIndexOrThrow(_stmt, "path")
        val _columnIndexOfExternalUri: Int = getColumnIndexOrThrow(_stmt, "external_uri")
        val _result: MutableList<Folder> = mutableListOf()
        while (_stmt.step()) {
          val _item: Folder
          val _tmpBookId: String
          _tmpBookId = _stmt.getText(_columnIndexOfBookId)
          val _tmpPath: String
          _tmpPath = _stmt.getText(_columnIndexOfPath)
          val _tmpExternalUri: String?
          if (_stmt.isNull(_columnIndexOfExternalUri)) {
            _tmpExternalUri = null
          } else {
            _tmpExternalUri = _stmt.getText(_columnIndexOfExternalUri)
          }
          _item = Folder(_tmpBookId,_tmpPath,_tmpExternalUri)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeFoldersByBook(bookId: String): Flow<List<Folder>> {
    val _sql: String = "SELECT * FROM folders WHERE book_id = ? ORDER BY path ASC"
    return createFlow(__db, false, arrayOf("folders")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, bookId)
        val _columnIndexOfBookId: Int = getColumnIndexOrThrow(_stmt, "book_id")
        val _columnIndexOfPath: Int = getColumnIndexOrThrow(_stmt, "path")
        val _columnIndexOfExternalUri: Int = getColumnIndexOrThrow(_stmt, "external_uri")
        val _result: MutableList<Folder> = mutableListOf()
        while (_stmt.step()) {
          val _item: Folder
          val _tmpBookId: String
          _tmpBookId = _stmt.getText(_columnIndexOfBookId)
          val _tmpPath: String
          _tmpPath = _stmt.getText(_columnIndexOfPath)
          val _tmpExternalUri: String?
          if (_stmt.isNull(_columnIndexOfExternalUri)) {
            _tmpExternalUri = null
          } else {
            _tmpExternalUri = _stmt.getText(_columnIndexOfExternalUri)
          }
          _item = Folder(_tmpBookId,_tmpPath,_tmpExternalUri)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getFoldersByBook(bookId: String): List<Folder> {
    val _sql: String = "SELECT * FROM folders WHERE book_id = ? ORDER BY path ASC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, bookId)
        val _columnIndexOfBookId: Int = getColumnIndexOrThrow(_stmt, "book_id")
        val _columnIndexOfPath: Int = getColumnIndexOrThrow(_stmt, "path")
        val _columnIndexOfExternalUri: Int = getColumnIndexOrThrow(_stmt, "external_uri")
        val _result: MutableList<Folder> = mutableListOf()
        while (_stmt.step()) {
          val _item: Folder
          val _tmpBookId: String
          _tmpBookId = _stmt.getText(_columnIndexOfBookId)
          val _tmpPath: String
          _tmpPath = _stmt.getText(_columnIndexOfPath)
          val _tmpExternalUri: String?
          if (_stmt.isNull(_columnIndexOfExternalUri)) {
            _tmpExternalUri = null
          } else {
            _tmpExternalUri = _stmt.getText(_columnIndexOfExternalUri)
          }
          _item = Folder(_tmpBookId,_tmpPath,_tmpExternalUri)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeWordCountPerFolder(): Flow<List<FolderWordRow>> {
    val _sql: String = """
        |
        |        SELECT book_id, folder_path,
        |               COALESCE(SUM(word_count), 0) AS total
        |        FROM notes
        |        GROUP BY book_id, folder_path
        |    
        """.trimMargin()
    return createFlow(__db, false, arrayOf("notes")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfBookId: Int = 0
        val _columnIndexOfFolderPath: Int = 1
        val _columnIndexOfTotal: Int = 2
        val _result: MutableList<FolderWordRow> = mutableListOf()
        while (_stmt.step()) {
          val _item: FolderWordRow
          val _tmpBookId: String
          _tmpBookId = _stmt.getText(_columnIndexOfBookId)
          val _tmpFolderPath: String
          _tmpFolderPath = _stmt.getText(_columnIndexOfFolderPath)
          val _tmpTotal: Int
          _tmpTotal = _stmt.getLong(_columnIndexOfTotal).toInt()
          _item = FolderWordRow(_tmpBookId,_tmpFolderPath,_tmpTotal)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun updateContent(
    id: String,
    content: String,
    updatedAt: Long,
  ) {
    val _sql: String = "UPDATE notes SET content = ?, updated_at = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, content)
        _argIndex = 2
        _stmt.bindLong(_argIndex, updatedAt)
        _argIndex = 3
        _stmt.bindText(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun updateContentAndWordCount(
    id: String,
    content: String,
    wordCount: Int,
    updatedAt: Long,
  ) {
    val _sql: String = "UPDATE notes SET content = ?, word_count = ?, updated_at = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, content)
        _argIndex = 2
        _stmt.bindLong(_argIndex, wordCount.toLong())
        _argIndex = 3
        _stmt.bindLong(_argIndex, updatedAt)
        _argIndex = 4
        _stmt.bindText(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun updateWordCount(id: String, count: Int) {
    val _sql: String = "UPDATE notes SET word_count = ? WHERE id = ? AND word_count = 0"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, count.toLong())
        _argIndex = 2
        _stmt.bindText(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun updateName(
    id: String,
    name: String,
    updatedAt: Long,
  ) {
    val _sql: String = "UPDATE notes SET name = ?, updated_at = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, name)
        _argIndex = 2
        _stmt.bindLong(_argIndex, updatedAt)
        _argIndex = 3
        _stmt.bindText(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun moveNote(
    id: String,
    folderPath: String,
    updatedAt: Long,
  ) {
    val _sql: String = "UPDATE notes SET folder_path = ?, updated_at = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, folderPath)
        _argIndex = 2
        _stmt.bindLong(_argIndex, updatedAt)
        _argIndex = 3
        _stmt.bindText(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteById(id: String) {
    val _sql: String = "DELETE FROM notes WHERE id = ?"
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

  public override suspend fun deleteByFolder(folderPath: String) {
    val _sql: String = "DELETE FROM notes WHERE folder_path = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, folderPath)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteByBookFolder(bookId: String, folderPath: String) {
    val _sql: String = "DELETE FROM notes WHERE book_id = ? AND folder_path = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, bookId)
        _argIndex = 2
        _stmt.bindText(_argIndex, folderPath)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteByBook(bookId: String) {
    val _sql: String = "DELETE FROM notes WHERE book_id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, bookId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteAllExternal() {
    val _sql: String = "DELETE FROM notes WHERE external_uri IS NOT NULL"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteAll() {
    val _sql: String = "DELETE FROM notes"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteFolder(bookId: String, path: String) {
    val _sql: String = "DELETE FROM folders WHERE book_id = ? AND path = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, bookId)
        _argIndex = 2
        _stmt.bindText(_argIndex, path)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteAllExternalFolders() {
    val _sql: String = "DELETE FROM folders WHERE external_uri IS NOT NULL"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteNonRootFoldersByBook(bookId: String) {
    val _sql: String = "DELETE FROM folders WHERE book_id = ? AND path != '/'"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, bookId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteFoldersByBook(bookId: String) {
    val _sql: String = "DELETE FROM folders WHERE book_id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, bookId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteAllFolders() {
    val _sql: String = "DELETE FROM folders"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
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
