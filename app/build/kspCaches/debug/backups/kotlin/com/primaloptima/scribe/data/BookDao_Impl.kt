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
public class BookDao_Impl(
  __db: RoomDatabase,
) : BookDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfBook: EntityInsertAdapter<Book>

  private val __insertAdapterOfBook_1: EntityInsertAdapter<Book>

  private val __updateAdapterOfBook: EntityDeleteOrUpdateAdapter<Book>
  init {
    this.__db = __db
    this.__insertAdapterOfBook = object : EntityInsertAdapter<Book>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `books` (`id`,`title`,`cover_uri`,`created_at`,`updated_at`,`sort_order`,`summary`,`tags`) VALUES (?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: Book) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.title)
        val _tmpCoverUri: String? = entity.coverUri
        if (_tmpCoverUri == null) {
          statement.bindNull(3)
        } else {
          statement.bindText(3, _tmpCoverUri)
        }
        statement.bindLong(4, entity.createdAt)
        statement.bindLong(5, entity.updatedAt)
        statement.bindLong(6, entity.sortOrder.toLong())
        statement.bindText(7, entity.summary)
        statement.bindText(8, entity.tags)
      }
    }
    this.__insertAdapterOfBook_1 = object : EntityInsertAdapter<Book>() {
      protected override fun createQuery(): String = "INSERT OR IGNORE INTO `books` (`id`,`title`,`cover_uri`,`created_at`,`updated_at`,`sort_order`,`summary`,`tags`) VALUES (?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: Book) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.title)
        val _tmpCoverUri: String? = entity.coverUri
        if (_tmpCoverUri == null) {
          statement.bindNull(3)
        } else {
          statement.bindText(3, _tmpCoverUri)
        }
        statement.bindLong(4, entity.createdAt)
        statement.bindLong(5, entity.updatedAt)
        statement.bindLong(6, entity.sortOrder.toLong())
        statement.bindText(7, entity.summary)
        statement.bindText(8, entity.tags)
      }
    }
    this.__updateAdapterOfBook = object : EntityDeleteOrUpdateAdapter<Book>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `books` SET `id` = ?,`title` = ?,`cover_uri` = ?,`created_at` = ?,`updated_at` = ?,`sort_order` = ?,`summary` = ?,`tags` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: Book) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.title)
        val _tmpCoverUri: String? = entity.coverUri
        if (_tmpCoverUri == null) {
          statement.bindNull(3)
        } else {
          statement.bindText(3, _tmpCoverUri)
        }
        statement.bindLong(4, entity.createdAt)
        statement.bindLong(5, entity.updatedAt)
        statement.bindLong(6, entity.sortOrder.toLong())
        statement.bindText(7, entity.summary)
        statement.bindText(8, entity.tags)
        statement.bindText(9, entity.id)
      }
    }
  }

  public override suspend fun insert(book: Book): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfBook.insert(_connection, book)
  }

  public override suspend fun insertIfAbsent(book: Book): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfBook_1.insert(_connection, book)
  }

  public override suspend fun update(book: Book): Unit = performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfBook.handle(_connection, book)
  }

  public override fun observeAll(): Flow<List<Book>> {
    val _sql: String = "SELECT * FROM books ORDER BY sort_order ASC, created_at ASC"
    return createFlow(__db, false, arrayOf("books")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfCoverUri: Int = getColumnIndexOrThrow(_stmt, "cover_uri")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfSortOrder: Int = getColumnIndexOrThrow(_stmt, "sort_order")
        val _columnIndexOfSummary: Int = getColumnIndexOrThrow(_stmt, "summary")
        val _columnIndexOfTags: Int = getColumnIndexOrThrow(_stmt, "tags")
        val _result: MutableList<Book> = mutableListOf()
        while (_stmt.step()) {
          val _item: Book
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpCoverUri: String?
          if (_stmt.isNull(_columnIndexOfCoverUri)) {
            _tmpCoverUri = null
          } else {
            _tmpCoverUri = _stmt.getText(_columnIndexOfCoverUri)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmpSortOrder: Int
          _tmpSortOrder = _stmt.getLong(_columnIndexOfSortOrder).toInt()
          val _tmpSummary: String
          _tmpSummary = _stmt.getText(_columnIndexOfSummary)
          val _tmpTags: String
          _tmpTags = _stmt.getText(_columnIndexOfTags)
          _item = Book(_tmpId,_tmpTitle,_tmpCoverUri,_tmpCreatedAt,_tmpUpdatedAt,_tmpSortOrder,_tmpSummary,_tmpTags)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getAll(): List<Book> {
    val _sql: String = "SELECT * FROM books ORDER BY sort_order ASC, created_at ASC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfCoverUri: Int = getColumnIndexOrThrow(_stmt, "cover_uri")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfSortOrder: Int = getColumnIndexOrThrow(_stmt, "sort_order")
        val _columnIndexOfSummary: Int = getColumnIndexOrThrow(_stmt, "summary")
        val _columnIndexOfTags: Int = getColumnIndexOrThrow(_stmt, "tags")
        val _result: MutableList<Book> = mutableListOf()
        while (_stmt.step()) {
          val _item: Book
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpCoverUri: String?
          if (_stmt.isNull(_columnIndexOfCoverUri)) {
            _tmpCoverUri = null
          } else {
            _tmpCoverUri = _stmt.getText(_columnIndexOfCoverUri)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmpSortOrder: Int
          _tmpSortOrder = _stmt.getLong(_columnIndexOfSortOrder).toInt()
          val _tmpSummary: String
          _tmpSummary = _stmt.getText(_columnIndexOfSummary)
          val _tmpTags: String
          _tmpTags = _stmt.getText(_columnIndexOfTags)
          _item = Book(_tmpId,_tmpTitle,_tmpCoverUri,_tmpCreatedAt,_tmpUpdatedAt,_tmpSortOrder,_tmpSummary,_tmpTags)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: String): Book? {
    val _sql: String = "SELECT * FROM books WHERE id = ? LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfCoverUri: Int = getColumnIndexOrThrow(_stmt, "cover_uri")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfSortOrder: Int = getColumnIndexOrThrow(_stmt, "sort_order")
        val _columnIndexOfSummary: Int = getColumnIndexOrThrow(_stmt, "summary")
        val _columnIndexOfTags: Int = getColumnIndexOrThrow(_stmt, "tags")
        val _result: Book?
        if (_stmt.step()) {
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpCoverUri: String?
          if (_stmt.isNull(_columnIndexOfCoverUri)) {
            _tmpCoverUri = null
          } else {
            _tmpCoverUri = _stmt.getText(_columnIndexOfCoverUri)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmpSortOrder: Int
          _tmpSortOrder = _stmt.getLong(_columnIndexOfSortOrder).toInt()
          val _tmpSummary: String
          _tmpSummary = _stmt.getText(_columnIndexOfSummary)
          val _tmpTags: String
          _tmpTags = _stmt.getText(_columnIndexOfTags)
          _result = Book(_tmpId,_tmpTitle,_tmpCoverUri,_tmpCreatedAt,_tmpUpdatedAt,_tmpSortOrder,_tmpSummary,_tmpTags)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun localNoteCount(bookId: String): Int {
    val _sql: String = "SELECT COUNT(*) FROM notes WHERE book_id = ? AND external_uri IS NULL"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, bookId)
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

  public override suspend fun deleteById(id: String) {
    val _sql: String = "DELETE FROM books WHERE id = ?"
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

  public override suspend fun touch(id: String, updatedAt: Long) {
    val _sql: String = "UPDATE books SET updated_at = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, updatedAt)
        _argIndex = 2
        _stmt.bindText(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun updateCover(
    id: String,
    uri: String?,
    updatedAt: Long,
  ) {
    val _sql: String = "UPDATE books SET cover_uri = ?, updated_at = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        if (uri == null) {
          _stmt.bindNull(_argIndex)
        } else {
          _stmt.bindText(_argIndex, uri)
        }
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

  public override suspend fun updateSortOrder(id: String, order: Int) {
    val _sql: String = "UPDATE books SET sort_order = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, order.toLong())
        _argIndex = 2
        _stmt.bindText(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun updateSummary(
    id: String,
    summary: String,
    updatedAt: Long,
  ) {
    val _sql: String = "UPDATE books SET summary = ?, updated_at = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, summary)
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

  public override suspend fun updateTags(
    id: String,
    tags: String,
    updatedAt: Long,
  ) {
    val _sql: String = "UPDATE books SET tags = ?, updated_at = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, tags)
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

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
