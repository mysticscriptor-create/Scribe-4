package com.primaloptima.scribe.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.primaloptima.scribe.ScribeApp
import com.primaloptima.scribe.data.Book
import com.primaloptima.scribe.data.Folder
import com.primaloptima.scribe.data.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class BookViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ScribeApp
    private val db = app.database

    // Set by BookActivity after creation
    var bookId: String = Note.DEFAULT_BOOK_ID
        private set

    private val _book = MutableLiveData<Book?>()
    val book: LiveData<Book?> = _book

    private val _notes = MutableLiveData<List<Note>>(emptyList())
    val notes: LiveData<List<Note>> = _notes

    private val _folders = MutableLiveData<List<Folder>>(emptyList())
    val folders: LiveData<List<Folder>> = _folders

    private val _worldEntries = MutableLiveData<List<com.primaloptima.scribe.data.WorldEntry>>(emptyList())
    val worldEntries: LiveData<List<com.primaloptima.scribe.data.WorldEntry>> = _worldEntries

    // ── View mode ─────────────────────────────────────────────────────────────

    enum class ViewMode { LIST, TREE }

    private val _viewMode = MutableLiveData(ViewMode.LIST)
    val viewMode: LiveData<ViewMode> = _viewMode

    fun toggleViewMode() {
        _viewMode.value = if (_viewMode.value == ViewMode.LIST) ViewMode.TREE else ViewMode.LIST
    }

    // ── Sort ──────────────────────────────────────────────────────────────────

    enum class SortMode { DATE_UPDATED, DATE_CREATED, TITLE_AZ }

    private val _sortMode = MutableLiveData(SortMode.DATE_UPDATED)
    val sortMode: LiveData<SortMode> = _sortMode

    fun setSortMode(mode: SortMode) { _sortMode.value = mode; reload() }

    // ── Init ──────────────────────────────────────────────────────────────────

    fun init(bookId: String) {
        this.bookId = bookId
        reload()
    }

    fun reload() {
        viewModelScope.launch(Dispatchers.IO) {
            val book = db.bookDao().getById(bookId)
            val notes = db.noteDao().getByBook(bookId)
            val folders = db.noteDao().getFoldersByBook(bookId)
            val world = try { db.worldEntryDao().getAllSync() } catch (_: Exception) { emptyList() }
            val sortedNotes = when (_sortMode.value) {
                SortMode.DATE_CREATED -> notes.sortedByDescending { it.createdAt }
                SortMode.TITLE_AZ -> notes.sortedBy { it.name.lowercase() }
                else -> notes.sortedByDescending { it.updatedAt }
            }
            withContext(Dispatchers.Main) {
                _book.value = book
                _notes.value = sortedNotes
                _folders.value = folders.sortedBy { it.path }
                _worldEntries.value = world
            }
        }
    }

    // ── Tree helpers ──────────────────────────────────────────────────────────

    fun notesInFolder(folderPath: String): List<Note> =
        (_notes.value ?: emptyList()).filter { it.folderPath == folderPath }

    fun childFolders(parentPath: String): List<Folder> {
        val prefix = if (parentPath == "/") "/" else "$parentPath/"
        return (_folders.value ?: emptyList()).filter { folder ->
            folder.path != parentPath
                && folder.path.startsWith(prefix)
                && !folder.path.removePrefix(prefix).contains("/")
        }.sortedBy { it.path }
    }

    // ── Tree item model ───────────────────────────────────────────────────────

    sealed class TreeItem {
        data class FolderItem(val folder: Folder, val depth: Int, var expanded: Boolean = true) : TreeItem()
        data class NoteItem(val note: Note, val depth: Int) : TreeItem()
    }

    /** Build a flat ordered list for tree view from the root. */
    fun buildTree(): List<TreeItem> {
        val result = mutableListOf<TreeItem>()
        fun addFolder(path: String, depth: Int) {
            if (path != "/") {
                val folder = (_folders.value ?: emptyList()).find { it.path == path } ?: return
                result.add(TreeItem.FolderItem(folder, depth))
            }
            notesInFolder(path).forEach { result.add(TreeItem.NoteItem(it, depth)) }
            childFolders(path).forEach { addFolder(it.path, depth + 1) }
        }
        addFolder("/", 0)
        return result
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    fun createNote(name: String, folderPath: String = "/", onCreated: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            db.noteDao().insert(
                Note(id = id, name = name, bookId = bookId, folderPath = folderPath,
                     createdAt = now, updatedAt = now)
            )
            db.bookDao().touch(bookId, now)
            withContext(Dispatchers.Main) {
                reload()
                onCreated(id)
            }
        }
    }

    fun createFolder(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.noteDao().insertFolder(Folder(bookId = bookId, path = path))
            withContext(Dispatchers.Main) { reload() }
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.noteDao().deleteById(noteId)
            withContext(Dispatchers.Main) { reload() }
        }
    }

    fun deleteFolder(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.noteDao().deleteByBookFolder(bookId, path)
            db.noteDao().deleteFolder(bookId, path)
            withContext(Dispatchers.Main) { reload() }
        }
    }

    fun renameNote(noteId: String, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.noteDao().updateName(noteId, newName, System.currentTimeMillis())
            withContext(Dispatchers.Main) { reload() }
        }
    }

    fun duplicateNote(noteId: String, onCreated: (String) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val original = db.noteDao().getById(noteId) ?: return@launch
            val newId = UUID.randomUUID().toString()
            val now   = System.currentTimeMillis()
            db.noteDao().insert(
                original.copy(
                    id           = newId,
                    name         = "${original.name} (copy)",
                    externalUri  = null,   // copy stays in the DB vault only
                    createdAt    = now,
                    updatedAt    = now
                )
            )
            db.bookDao().touch(bookId, now)
            withContext(Dispatchers.Main) {
                reload()
                onCreated(newId)
            }
        }
    }
}
