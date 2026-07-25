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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ScribeApp
    private val db = app.database

    val books: LiveData<List<Book>> = db.bookDao().observeAll().asLiveData()
    val allNotes: LiveData<List<Note>> = db.noteDao().observeAll().asLiveData()
    val allFolders: LiveData<List<Folder>> = db.noteDao().observeFolders().asLiveData()

    private val _searchResults = MutableLiveData<List<Book>>(emptyList())
    val searchResults: LiveData<List<Book>> = _searchResults

    // ── Quick Note Creation ──────────────────────────────────────────────────

    fun createQuickNote(onCreated: (Note) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = db.noteDao().getByFolder("/Quick Notes")
            val quickNotes = if (existing.isEmpty()) {
                db.noteDao().getByBookFolder(Note.DEFAULT_BOOK_ID, "/Quick Notes")
            } else existing

            val nextNumber = quickNotes.size + 1
            val title = "Note $nextNumber"
            val id = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()

            val note = Note(
                id = id,
                bookId = Note.DEFAULT_BOOK_ID,
                name = title,
                content = "",
                folderPath = "/Quick Notes",
                createdAt = now,
                updatedAt = now
            )
            db.noteDao().insertFolder(Folder(bookId = Note.DEFAULT_BOOK_ID, path = "/Quick Notes"))
            db.noteDao().insert(note)
            withContext(Dispatchers.Main) { onCreated(note) }
        }
    }

    // ── Sort mode ──────────────────────────────────────────────────────────────

    enum class SortMode { DATE_UPDATED, DATE_CREATED, TITLE_AZ, MANUAL }

    private val _sortMode = MutableLiveData(SortMode.DATE_UPDATED)
    val sortMode: LiveData<SortMode> = _sortMode

    fun setSortMode(mode: SortMode) { _sortMode.value = mode }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    fun createBook(title: String, onCreated: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val book = Book(id = id, title = title, createdAt = now, updatedAt = now)
            db.bookDao().insert(book)
            // Create root folder for new book
            db.noteDao().insertFolder(Folder(bookId = id, path = "/"))
            withContext(Dispatchers.Main) { onCreated(id) }
        }
    }

    fun deleteBook(bookId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.noteDao().deleteByBook(bookId)
            db.noteDao().deleteFoldersByBook(bookId)
            db.bookDao().deleteById(bookId)
        }
    }

    fun renameBook(bookId: String, newTitle: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val book = db.bookDao().getById(bookId) ?: return@launch
            db.bookDao().update(book.copy(title = newTitle, updatedAt = System.currentTimeMillis()))
        }
    }

    fun updateCover(bookId: String, uri: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            db.bookDao().updateCover(bookId, uri, System.currentTimeMillis())
        }
    }

    fun moveBookManual(bookId: String, newOrder: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            db.bookDao().updateSortOrder(bookId, newOrder)
        }
    }

    /** Sort the visible list client-side (books LiveData still sorted by DB default). */
    fun sortedBooks(books: List<Book>): List<Book> = when (_sortMode.value) {
        SortMode.DATE_CREATED -> books.sortedByDescending { it.createdAt }
        SortMode.TITLE_AZ -> books.sortedBy { it.title.lowercase() }
        SortMode.MANUAL -> books.sortedBy { it.sortOrder }
        else -> books.sortedByDescending { it.updatedAt }
    }

    fun searchBooks(query: String, allBooks: List<Book>) {
        if (query.isBlank()) { _searchResults.value = emptyList(); return }
        _searchResults.value = allBooks.filter {
            it.title.contains(query, ignoreCase = true)
        }
    }

    fun clearSearch() { _searchResults.value = emptyList() }
}
