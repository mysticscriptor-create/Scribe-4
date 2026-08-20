package com.primaloptima.scribe.`data`

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class AppDatabase_Impl : AppDatabase() {
  private val _noteDao: Lazy<NoteDao> = lazy {
    NoteDao_Impl(this)
  }

  private val _worldEntryDao: Lazy<WorldEntryDao> = lazy {
    WorldEntryDao_Impl(this)
  }

  private val _bookDao: Lazy<BookDao> = lazy {
    BookDao_Impl(this)
  }

  private val _noteVersionDao: Lazy<NoteVersionDao> = lazy {
    NoteVersionDao_Impl(this)
  }

  private val _writingLogDao: Lazy<WritingLogDao> = lazy {
    WritingLogDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(8, "5957714add10d15b253ccb881884af11", "f059735e65d2731db60485970a1324b6") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `notes` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `book_id` TEXT NOT NULL, `folder_path` TEXT NOT NULL, `ext` TEXT NOT NULL, `content` TEXT NOT NULL, `word_count` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `external_uri` TEXT, `loaded` INTEGER NOT NULL, `formats_json` TEXT, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_book_id` ON `notes` (`book_id`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_book_id_folder_path` ON `notes` (`book_id`, `folder_path`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `folders` (`book_id` TEXT NOT NULL, `path` TEXT NOT NULL, `external_uri` TEXT, PRIMARY KEY(`book_id`, `path`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `world_entries` (`id` TEXT NOT NULL, `type` TEXT NOT NULL, `name` TEXT NOT NULL, `summary` TEXT NOT NULL, `fields_json` TEXT NOT NULL, `tags_json` TEXT NOT NULL, `image_uri` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `books` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `cover_uri` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `sort_order` INTEGER NOT NULL, `summary` TEXT NOT NULL, `tags` TEXT NOT NULL, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `note_versions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `note_id` TEXT NOT NULL, `content` TEXT NOT NULL, `word_count` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `type` TEXT NOT NULL)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_note_versions_note_id` ON `note_versions` (`note_id`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `writing_log` (`date` TEXT NOT NULL, `note_id` TEXT NOT NULL, `book_id` TEXT NOT NULL, `folder_path` TEXT NOT NULL, `words_added` INTEGER NOT NULL, PRIMARY KEY(`date`, `note_id`))")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_writing_log_date` ON `writing_log` (`date`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_writing_log_book_id_date` ON `writing_log` (`book_id`, `date`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_writing_log_book_id_folder_path_date` ON `writing_log` (`book_id`, `folder_path`, `date`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '5957714add10d15b253ccb881884af11')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `notes`")
        connection.execSQL("DROP TABLE IF EXISTS `folders`")
        connection.execSQL("DROP TABLE IF EXISTS `world_entries`")
        connection.execSQL("DROP TABLE IF EXISTS `books`")
        connection.execSQL("DROP TABLE IF EXISTS `note_versions`")
        connection.execSQL("DROP TABLE IF EXISTS `writing_log`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection): RoomOpenDelegate.ValidationResult {
        val _columnsNotes: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsNotes.put("id", TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNotes.put("name", TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNotes.put("book_id", TableInfo.Column("book_id", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNotes.put("folder_path", TableInfo.Column("folder_path", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNotes.put("ext", TableInfo.Column("ext", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNotes.put("content", TableInfo.Column("content", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNotes.put("word_count", TableInfo.Column("word_count", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNotes.put("created_at", TableInfo.Column("created_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNotes.put("updated_at", TableInfo.Column("updated_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNotes.put("external_uri", TableInfo.Column("external_uri", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNotes.put("loaded", TableInfo.Column("loaded", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNotes.put("formats_json", TableInfo.Column("formats_json", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysNotes: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesNotes: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesNotes.add(TableInfo.Index("index_notes_book_id", false, listOf("book_id"), listOf("ASC")))
        _indicesNotes.add(TableInfo.Index("index_notes_book_id_folder_path", false, listOf("book_id", "folder_path"), listOf("ASC", "ASC")))
        val _infoNotes: TableInfo = TableInfo("notes", _columnsNotes, _foreignKeysNotes, _indicesNotes)
        val _existingNotes: TableInfo = read(connection, "notes")
        if (!_infoNotes.equals(_existingNotes)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |notes(com.primaloptima.scribe.data.Note).
              | Expected:
              |""".trimMargin() + _infoNotes + """
              |
              | Found:
              |""".trimMargin() + _existingNotes)
        }
        val _columnsFolders: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsFolders.put("book_id", TableInfo.Column("book_id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsFolders.put("path", TableInfo.Column("path", "TEXT", true, 2, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsFolders.put("external_uri", TableInfo.Column("external_uri", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysFolders: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesFolders: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoFolders: TableInfo = TableInfo("folders", _columnsFolders, _foreignKeysFolders, _indicesFolders)
        val _existingFolders: TableInfo = read(connection, "folders")
        if (!_infoFolders.equals(_existingFolders)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |folders(com.primaloptima.scribe.data.Folder).
              | Expected:
              |""".trimMargin() + _infoFolders + """
              |
              | Found:
              |""".trimMargin() + _existingFolders)
        }
        val _columnsWorldEntries: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsWorldEntries.put("id", TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorldEntries.put("type", TableInfo.Column("type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorldEntries.put("name", TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorldEntries.put("summary", TableInfo.Column("summary", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorldEntries.put("fields_json", TableInfo.Column("fields_json", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorldEntries.put("tags_json", TableInfo.Column("tags_json", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorldEntries.put("image_uri", TableInfo.Column("image_uri", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorldEntries.put("created_at", TableInfo.Column("created_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorldEntries.put("updated_at", TableInfo.Column("updated_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysWorldEntries: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesWorldEntries: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoWorldEntries: TableInfo = TableInfo("world_entries", _columnsWorldEntries, _foreignKeysWorldEntries, _indicesWorldEntries)
        val _existingWorldEntries: TableInfo = read(connection, "world_entries")
        if (!_infoWorldEntries.equals(_existingWorldEntries)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |world_entries(com.primaloptima.scribe.data.WorldEntry).
              | Expected:
              |""".trimMargin() + _infoWorldEntries + """
              |
              | Found:
              |""".trimMargin() + _existingWorldEntries)
        }
        val _columnsBooks: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsBooks.put("id", TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("title", TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("cover_uri", TableInfo.Column("cover_uri", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("created_at", TableInfo.Column("created_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("updated_at", TableInfo.Column("updated_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("sort_order", TableInfo.Column("sort_order", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("summary", TableInfo.Column("summary", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("tags", TableInfo.Column("tags", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysBooks: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesBooks: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoBooks: TableInfo = TableInfo("books", _columnsBooks, _foreignKeysBooks, _indicesBooks)
        val _existingBooks: TableInfo = read(connection, "books")
        if (!_infoBooks.equals(_existingBooks)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |books(com.primaloptima.scribe.data.Book).
              | Expected:
              |""".trimMargin() + _infoBooks + """
              |
              | Found:
              |""".trimMargin() + _existingBooks)
        }
        val _columnsNoteVersions: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsNoteVersions.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNoteVersions.put("note_id", TableInfo.Column("note_id", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNoteVersions.put("content", TableInfo.Column("content", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNoteVersions.put("word_count", TableInfo.Column("word_count", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNoteVersions.put("timestamp", TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNoteVersions.put("type", TableInfo.Column("type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysNoteVersions: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesNoteVersions: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesNoteVersions.add(TableInfo.Index("index_note_versions_note_id", false, listOf("note_id"), listOf("ASC")))
        val _infoNoteVersions: TableInfo = TableInfo("note_versions", _columnsNoteVersions, _foreignKeysNoteVersions, _indicesNoteVersions)
        val _existingNoteVersions: TableInfo = read(connection, "note_versions")
        if (!_infoNoteVersions.equals(_existingNoteVersions)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |note_versions(com.primaloptima.scribe.data.NoteVersion).
              | Expected:
              |""".trimMargin() + _infoNoteVersions + """
              |
              | Found:
              |""".trimMargin() + _existingNoteVersions)
        }
        val _columnsWritingLog: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsWritingLog.put("date", TableInfo.Column("date", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWritingLog.put("note_id", TableInfo.Column("note_id", "TEXT", true, 2, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWritingLog.put("book_id", TableInfo.Column("book_id", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWritingLog.put("folder_path", TableInfo.Column("folder_path", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWritingLog.put("words_added", TableInfo.Column("words_added", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysWritingLog: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesWritingLog: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesWritingLog.add(TableInfo.Index("index_writing_log_date", false, listOf("date"), listOf("ASC")))
        _indicesWritingLog.add(TableInfo.Index("index_writing_log_book_id_date", false, listOf("book_id", "date"), listOf("ASC", "ASC")))
        _indicesWritingLog.add(TableInfo.Index("index_writing_log_book_id_folder_path_date", false, listOf("book_id", "folder_path", "date"), listOf("ASC", "ASC", "ASC")))
        val _infoWritingLog: TableInfo = TableInfo("writing_log", _columnsWritingLog, _foreignKeysWritingLog, _indicesWritingLog)
        val _existingWritingLog: TableInfo = read(connection, "writing_log")
        if (!_infoWritingLog.equals(_existingWritingLog)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |writing_log(com.primaloptima.scribe.data.WritingLog).
              | Expected:
              |""".trimMargin() + _infoWritingLog + """
              |
              | Found:
              |""".trimMargin() + _existingWritingLog)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "notes", "folders", "world_entries", "books", "note_versions", "writing_log")
  }

  public override fun clearAllTables() {
    super.performClear(false, "notes", "folders", "world_entries", "books", "note_versions", "writing_log")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(NoteDao::class, NoteDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(WorldEntryDao::class, WorldEntryDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(BookDao::class, BookDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(NoteVersionDao::class, NoteVersionDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(WritingLogDao::class, WritingLogDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>): List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun noteDao(): NoteDao = _noteDao.value

  public override fun worldEntryDao(): WorldEntryDao = _worldEntryDao.value

  public override fun bookDao(): BookDao = _bookDao.value

  public override fun noteVersionDao(): NoteVersionDao = _noteVersionDao.value

  public override fun writingLogDao(): WritingLogDao = _writingLogDao.value
}
