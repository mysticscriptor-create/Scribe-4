package com.primaloptima.scribe.`data`

import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.performInTransactionSuspending
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
public class WritingLogDao_Impl(
  __db: RoomDatabase,
) : WritingLogDao() {
  private val __db: RoomDatabase
  init {
    this.__db = __db
  }

  public override suspend fun saveContentAndDelta(
    noteDao: NoteDao,
    noteId: String,
    content: String,
    wordCount: Int,
    updatedAt: Long,
    date: String,
    bookId: String,
    folderPath: String,
    delta: Int,
  ): Unit = performInTransactionSuspending(__db) {
    super@WritingLogDao_Impl.saveContentAndDelta(noteDao, noteId, content, wordCount, updatedAt, date, bookId, folderPath, delta)
  }

  public override suspend fun getWordsByDateRange(startDate: String, endDate: String): List<DailyWordRow> {
    val _sql: String = """
        |
        |        SELECT date, COALESCE(SUM(words_added), 0) AS total
        |        FROM writing_log
        |        WHERE date BETWEEN ? AND ?
        |        GROUP BY date
        |        ORDER BY date ASC
        |    
        """.trimMargin()
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, startDate)
        _argIndex = 2
        _stmt.bindText(_argIndex, endDate)
        val _columnIndexOfDate: Int = 0
        val _columnIndexOfTotal: Int = 1
        val _result: MutableList<DailyWordRow> = mutableListOf()
        while (_stmt.step()) {
          val _item: DailyWordRow
          val _tmpDate: String
          _tmpDate = _stmt.getText(_columnIndexOfDate)
          val _tmpTotal: Int
          _tmpTotal = _stmt.getLong(_columnIndexOfTotal).toInt()
          _item = DailyWordRow(_tmpDate,_tmpTotal)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getWordsByMonth(startDate: String): List<MonthlyWordRow> {
    val _sql: String = """
        |
        |        SELECT strftime('%Y-%m', date) AS month,
        |               COALESCE(SUM(words_added), 0) AS total
        |        FROM writing_log
        |        WHERE date >= ?
        |        GROUP BY month
        |        ORDER BY month ASC
        |    
        """.trimMargin()
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, startDate)
        val _columnIndexOfMonth: Int = 0
        val _columnIndexOfTotal: Int = 1
        val _result: MutableList<MonthlyWordRow> = mutableListOf()
        while (_stmt.step()) {
          val _item: MonthlyWordRow
          val _tmpMonth: String
          _tmpMonth = _stmt.getText(_columnIndexOfMonth)
          val _tmpTotal: Int
          _tmpTotal = _stmt.getLong(_columnIndexOfTotal).toInt()
          _item = MonthlyWordRow(_tmpMonth,_tmpTotal)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getWeeklyWords(sevenDaysAgo: String): List<DailyWordRow> {
    val _sql: String = """
        |
        |        SELECT date, COALESCE(SUM(words_added), 0) AS total
        |        FROM writing_log
        |        WHERE date >= ?
        |        GROUP BY date
        |        ORDER BY date ASC
        |    
        """.trimMargin()
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, sevenDaysAgo)
        val _columnIndexOfDate: Int = 0
        val _columnIndexOfTotal: Int = 1
        val _result: MutableList<DailyWordRow> = mutableListOf()
        while (_stmt.step()) {
          val _item: DailyWordRow
          val _tmpDate: String
          _tmpDate = _stmt.getText(_columnIndexOfDate)
          val _tmpTotal: Int
          _tmpTotal = _stmt.getLong(_columnIndexOfTotal).toInt()
          _item = DailyWordRow(_tmpDate,_tmpTotal)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getTotalWordsOnDate(date: String): Int {
    val _sql: String = """
        |
        |        SELECT COALESCE(SUM(words_added), 0)
        |        FROM writing_log
        |        WHERE date = ?
        |    
        """.trimMargin()
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, date)
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

  public override fun observeTodayWords(today: String): Flow<Int> {
    val _sql: String = """
        |
        |        SELECT COALESCE(SUM(words_added), 0)
        |        FROM writing_log
        |        WHERE date = ?
        |    
        """.trimMargin()
    return createFlow(__db, false, arrayOf("writing_log")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, today)
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

  public override suspend fun getTodayWords(today: String): Int {
    val _sql: String = """
        |
        |        SELECT COALESCE(SUM(words_added), 0)
        |        FROM writing_log
        |        WHERE date = ?
        |    
        """.trimMargin()
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, today)
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

  public override fun observeWritingDates(): Flow<List<DailyWordRow>> {
    val _sql: String = """
        |
        |        SELECT date, COALESCE(SUM(words_added), 0) AS total
        |        FROM writing_log
        |        WHERE words_added > 0
        |        GROUP BY date
        |        HAVING total > 0
        |        ORDER BY date DESC
        |    
        """.trimMargin()
    return createFlow(__db, false, arrayOf("writing_log")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfDate: Int = 0
        val _columnIndexOfTotal: Int = 1
        val _result: MutableList<DailyWordRow> = mutableListOf()
        while (_stmt.step()) {
          val _item: DailyWordRow
          val _tmpDate: String
          _tmpDate = _stmt.getText(_columnIndexOfDate)
          val _tmpTotal: Int
          _tmpTotal = _stmt.getLong(_columnIndexOfTotal).toInt()
          _item = DailyWordRow(_tmpDate,_tmpTotal)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeWeeklyWords(sevenDaysAgo: String): Flow<List<DailyWordRow>> {
    val _sql: String = """
        |
        |        SELECT date, COALESCE(SUM(words_added), 0) AS total
        |        FROM writing_log
        |        WHERE date >= ?
        |        GROUP BY date
        |        ORDER BY date ASC
        |    
        """.trimMargin()
    return createFlow(__db, false, arrayOf("writing_log")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, sevenDaysAgo)
        val _columnIndexOfDate: Int = 0
        val _columnIndexOfTotal: Int = 1
        val _result: MutableList<DailyWordRow> = mutableListOf()
        while (_stmt.step()) {
          val _item: DailyWordRow
          val _tmpDate: String
          _tmpDate = _stmt.getText(_columnIndexOfDate)
          val _tmpTotal: Int
          _tmpTotal = _stmt.getLong(_columnIndexOfTotal).toInt()
          _item = DailyWordRow(_tmpDate,_tmpTotal)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getAllWritingDates(): List<DailyWordRow> {
    val _sql: String = """
        |
        |        SELECT date, COALESCE(SUM(words_added), 0) AS total
        |        FROM writing_log
        |        WHERE words_added > 0
        |        GROUP BY date
        |        HAVING total > 0
        |        ORDER BY date DESC
        |    
        """.trimMargin()
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfDate: Int = 0
        val _columnIndexOfTotal: Int = 1
        val _result: MutableList<DailyWordRow> = mutableListOf()
        while (_stmt.step()) {
          val _item: DailyWordRow
          val _tmpDate: String
          _tmpDate = _stmt.getText(_columnIndexOfDate)
          val _tmpTotal: Int
          _tmpTotal = _stmt.getLong(_columnIndexOfTotal).toInt()
          _item = DailyWordRow(_tmpDate,_tmpTotal)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getWordCountPerFolder(): List<FolderWordRow> {
    val _sql: String = """
        |
        |        SELECT book_id, folder_path,
        |               COALESCE(SUM(word_count), 0) AS total
        |        FROM notes
        |        GROUP BY book_id, folder_path
        |    
        """.trimMargin()
    return performSuspending(__db, true, false) { _connection ->
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

  public override suspend fun recordDelta(
    date: String,
    noteId: String,
    bookId: String,
    folderPath: String,
    delta: Int,
  ) {
    val _sql: String = """
        |
        |        INSERT OR REPLACE INTO writing_log (date, note_id, book_id, folder_path, words_added)
        |        VALUES (?, ?, ?, ?,
        |            COALESCE(
        |                (SELECT words_added FROM writing_log
        |                 WHERE date = ? AND note_id = ?),
        |                0
        |            ) + ?
        |        )
        |    
        """.trimMargin()
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, date)
        _argIndex = 2
        _stmt.bindText(_argIndex, noteId)
        _argIndex = 3
        _stmt.bindText(_argIndex, bookId)
        _argIndex = 4
        _stmt.bindText(_argIndex, folderPath)
        _argIndex = 5
        _stmt.bindText(_argIndex, date)
        _argIndex = 6
        _stmt.bindText(_argIndex, noteId)
        _argIndex = 7
        _stmt.bindLong(_argIndex, delta.toLong())
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun updateFolderPath(noteId: String, newFolderPath: String) {
    val _sql: String = "UPDATE writing_log SET folder_path = ? WHERE note_id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, newFolderPath)
        _argIndex = 2
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
