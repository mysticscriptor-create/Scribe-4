package com.primaloptima.scribe.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Phase 2-A: bumped to version 6 (added indexes on book_id and book_id+folder_path)
// Stats upgrade: bumped to version 7 (added writing_log table)
@Database(
    entities = [Note::class, Folder::class, WorldEntry::class, Book::class, NoteVersion::class, WritingLog::class],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao
    abstract fun worldEntryDao(): WorldEntryDao
    abstract fun bookDao(): BookDao
    abstract fun noteVersionDao(): NoteVersionDao
    abstract fun writingLogDao(): WritingLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * v1 → v2:
         *  - Create `books` table; insert "My Notes" default book for existing data.
         *  - Add `book_id` column to `notes` (DEFAULT 'default').
         *  - Recreate `folders` with composite PK (book_id, path) and copy existing rows.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val now = System.currentTimeMillis()

                // 1. Books table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS books (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        cover_uri TEXT,
                        created_at INTEGER NOT NULL DEFAULT 0,
                        updated_at INTEGER NOT NULL DEFAULT 0,
                        sort_order INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                // 2. Default book for pre-existing notes
                database.execSQL("""
                    INSERT OR IGNORE INTO books (id, title, cover_uri, created_at, updated_at, sort_order)
                    VALUES ('default', 'My Notes', NULL, $now, $now, 0)
                """.trimIndent())

                // 3. Add book_id to notes
                database.execSQL(
                    "ALTER TABLE notes ADD COLUMN book_id TEXT NOT NULL DEFAULT 'default'"
                )

                // 4. Recreate folders with composite PK (book_id, path)
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS folders_new (
                        book_id TEXT NOT NULL,
                        path TEXT NOT NULL,
                        external_uri TEXT,
                        PRIMARY KEY (book_id, path)
                    )
                """.trimIndent())
                database.execSQL("""
                    INSERT OR IGNORE INTO folders_new (book_id, path, external_uri)
                    SELECT 'default', path, external_uri FROM folders
                """.trimIndent())
                database.execSQL("DROP TABLE folders")
                database.execSQL("ALTER TABLE folders_new RENAME TO folders")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS note_versions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        note_id TEXT NOT NULL,
                        content TEXT NOT NULL,
                        word_count INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_note_versions_note_id ON note_versions (note_id)")
            }
        }

        /** v3 → v4: add `type` column ("auto" | "manual"). Existing rows become "auto". */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE note_versions ADD COLUMN type TEXT NOT NULL DEFAULT 'auto'"
                )
            }
        }

        /**
         * v4 → v5: add `word_count` column to notes (Phase 1-A).
         * Defaults to 0; real values are backfilled on first launch by ScribeApp.runWordCountBackfill().
         * SQLite does not support word counting natively — the Kotlin-side backfill handles it.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE notes ADD COLUMN word_count INTEGER NOT NULL DEFAULT 0"
                )
                // Real word counts are backfilled in ScribeApp.onCreate() via a coroutine.
                // Existing notes show 0 briefly (~1 second for 100 notes) until backfill completes.
            }
        }

        /**
         * v5 → v6: add indexes on notes(book_id) and notes(book_id, folder_path).
         * Purely additive — no data touched, no columns changed.
         * Speeds up all SUM/COUNT queries used by HomeViewModel aggregate flows.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE INDEX IF NOT EXISTS index_notes_book_id ON notes (book_id)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_notes_book_id_folder_path ON notes (book_id, folder_path)")
            }
        }

        /**
         * v6 → v7: add writing_log table with three indexes.
         * Purely additive — no existing data touched.
         * After this migration, EditorViewModel will start populating the table
         * on every save (Phase 2).
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS writing_log (
                        date TEXT NOT NULL,
                        note_id TEXT NOT NULL,
                        book_id TEXT NOT NULL,
                        folder_path TEXT NOT NULL,
                        words_added INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY (date, note_id)
                    )
                """.trimIndent())
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_writing_log_date ON writing_log (date)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_writing_log_book_id_date ON writing_log (book_id, date)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_writing_log_folder_date ON writing_log (book_id, folder_path, date)"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val db = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "scribe.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            val now = System.currentTimeMillis()
                            // Insert default book
                            db.execSQL("""
                                INSERT OR IGNORE INTO books (id, title, cover_uri, created_at, updated_at, sort_order)
                                VALUES ('default', 'My Notes', NULL, $now, $now, 0)
                            """.trimIndent())
                            // Insert root folder for default book
                            db.execSQL(
                                "INSERT OR IGNORE INTO folders (book_id, path) VALUES ('default', '/')"
                            )
                        }
                    })
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = db
                db
            }
        }
    }
}
