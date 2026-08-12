package com.primaloptima.scribe.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.primaloptima.scribe.ScribeApp
import com.primaloptima.scribe.data.Book
import com.primaloptima.scribe.data.Folder
import com.primaloptima.scribe.data.Note
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BooksViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ScribeApp
    private val db = app.database
    private val dataStore = app.dataStore

    val books: StateFlow<List<Book>> = db.bookDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val homeStartPage: StateFlow<String> = dataStore.homeStartPageFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "books")

    val gridColumns: StateFlow<Int> = dataStore.gridColumnsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2)

    val bookWordCounts: StateFlow<Map<String, Int>> = db.noteDao()
        .observeWordCountsByBook()
        .map { rows -> rows.associate { it.bookId to it.total } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val bookNoteCounts: StateFlow<Map<String, Int>> = db.noteDao()
        .observeNoteCountsByBook()
        .map { rows -> rows.associate { it.bookId to it.count } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val bookFolderCounts: StateFlow<Map<String, Int>> = db.noteDao()
        .observeFolderCountsByBook()
        .map { rows -> rows.associate { it.bookId to it.count } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val vaultWordCount: StateFlow<Int> = db.noteDao().observeVaultWordCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    enum class SortMode { DATE_UPDATED, DATE_CREATED, TITLE_AZ, MANUAL }

    private val _sortMode = MutableStateFlow(SortMode.DATE_UPDATED)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    val sortedBooks: StateFlow<List<Book>> = combine(books, sortMode) { bookList, mode ->
        when (mode) {
            SortMode.DATE_CREATED -> bookList.sortedByDescending { it.createdAt }
            SortMode.TITLE_AZ -> bookList.sortedBy { it.title.lowercase() }
            SortMode.MANUAL -> bookList.sortedBy { it.sortOrder }
            SortMode.DATE_UPDATED -> bookList.sortedByDescending { it.updatedAt }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchResults = MutableStateFlow<List<Book>>(emptyList())
    val searchResults: StateFlow<List<Book>> = _searchResults.asStateFlow()

    fun setGridColumns(columns: Int) {
        viewModelScope.launch { dataStore.setGridColumns(columns) }
    }

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
    }

    fun searchBooks(query: String, allBooks: List<Book> = books.value) {
        _searchResults.value = if (query.isBlank()) {
            emptyList()
        } else {
            allBooks.filter { it.title.contains(query, ignoreCase = true) }
        }
    }

    fun clearSearch() {
        _searchResults.value = emptyList()
    }

    fun createBook(title: String, onCreated: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            db.bookDao().insert(Book(id = id, title = title, createdAt = now, updatedAt = now))
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

    fun createQuickNote(onCreated: (Note) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = db.noteDao().getByFolder("/Quick Notes")
            val quickNotes = if (existing.isEmpty()) {
                db.noteDao().getByBookFolder(Note.DEFAULT_BOOK_ID, "/Quick Notes")
            } else {
                existing
            }
            val now = System.currentTimeMillis()
            val note = Note(
                id = UUID.randomUUID().toString(),
                bookId = Note.DEFAULT_BOOK_ID,
                name = "Note ${quickNotes.size + 1}",
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
}