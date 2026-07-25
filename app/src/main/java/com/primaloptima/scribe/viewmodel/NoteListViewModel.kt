package com.primaloptima.scribe.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.primaloptima.scribe.ScribeApp
import com.primaloptima.scribe.data.Book
import com.primaloptima.scribe.data.Folder
import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.util.SAFHelper
import com.primaloptima.scribe.util.model.ExternalRoot
import com.primaloptima.scribe.util.model.SafScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NoteListViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ScribeApp
    private val db = app.database
    private val prefs = app.prefs
    private val gson = Gson()

    val notes: LiveData<List<Note>> = db.noteDao().observeAll().asLiveData()
    val folders: LiveData<List<Folder>> = db.noteDao().observeFolders().asLiveData()

    private val _externalRoot = MutableLiveData<ExternalRoot?>()
    val externalRoot: LiveData<ExternalRoot?> = _externalRoot

    private val _externalLoading = MutableLiveData(false)
    val externalLoading: LiveData<Boolean> = _externalLoading

    private val _searchResults = MutableLiveData<List<Note>>(emptyList())
    val searchResults: LiveData<List<Note>> = _searchResults

    init {
        loadExternalRoot()
        ensureDefaultBook()
        checkFirstLaunch()
    }

    // ── First launch ──────────────────────────────────────────────────────────

    private fun ensureDefaultBook() {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            db.bookDao().insertIfAbsent(
                Book(id = Note.DEFAULT_BOOK_ID, title = "My Notes",
                     createdAt = now, updatedAt = now)
            )
            db.noteDao().insertFolder(Folder(path = "/"))
        }
    }

    private fun checkFirstLaunch() {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = db.noteDao().getAll()
            if (existing.isEmpty()) {
                insertSampleNotes()
            }
        }
    }

    private suspend fun insertSampleNotes() {
        val now = System.currentTimeMillis()
        val bid = Note.DEFAULT_BOOK_ID
        db.noteDao().insertFolders(listOf(
            Folder(path = "/"),
            Folder(path = "/Journal"),
            Folder(path = "/Drafts")
        ))
        db.noteDao().insertAll(listOf(
            Note(id = "welcome", name = "Welcome", bookId = bid, folderPath = "/", ext = "md",
                 createdAt = now - 86400000L, updatedAt = now - 86400000L,
                 content = """# Welcome to Scribe

Your private, on-device writing space — distraction-free and fully offline.

## What you can do here

- Write in Markdown with **bold**, *italic*, `code`, and [links](https://example.com)
- Customise fonts, colours, spacing in *Themes*
- Pin notes to the right side for reference while you write
- Open multiple floating windows for side-by-side reading

> The cursor jumps out of quotes when you press Enter. Try it.

Swipe in from the left edge inside the editor to see your book's files.

---

Happy writing."""),
            Note(id = "tips", name = "Tips", bookId = bid, folderPath = "/", ext = "md",
                 createdAt = now - 43200000L, updatedAt = now - 43200000L,
                 content = """# Tips

## Smart pairs

Type `"`, `(`, `[`, `{`, or `'` and Scribe inserts the matching close character. Press **Enter** while the cursor sits before a closing pair and the cursor jumps past it instead of breaking the line.

## Custom shortcuts

Open the menu and tap *Shortcuts* to add your own. Each shortcut can:

- Insert plain text (em-dash, ellipsis, signature)
- Wrap selection (markdown bold, italic, code)
- Insert a paired character"""),
            Note(id = "journal-jan", name = "January", bookId = bid, folderPath = "/Journal", ext = "md",
                 createdAt = now - 21600000L, updatedAt = now - 21600000L,
                 content = """# January

A new year. The page is open.

> "Begin doing what you want to do now." — Marie Beynon Ray

What I want to write about this year:

- Slow mornings
- The light through the kitchen window
- The neighbour's cat
- Letters I never sent"""),
            Note(id = "draft-essay", name = "On attention", bookId = bid, folderPath = "/Drafts", ext = "md",
                 createdAt = now - 1800000L, updatedAt = now - 1800000L,
                 content = """# On attention

The first draft is mostly about getting the shape down. Don't fix the prose yet. Don't pick at the words. Pour it out and then walk away for an hour.

When you come back, read it aloud. The sentences that make you stumble are the sentences that need work.""")
        ))
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    fun createNote(
        bookId: String = Note.DEFAULT_BOOK_ID,
        folderPath: String = "/",
        name: String = "Untitled",
        onCreated: (Note) -> Unit
    ) {
        viewModelScope.launch {
            val exr = _externalRoot.value
            if (exr != null) {
                val folder = folders.value?.firstOrNull { it.path == folderPath }
                val parentUri = Uri.parse(folder?.externalUri ?: exr.uri)
                try {
                    val uri = SAFHelper.createFile(getApplication(), parentUri, name, "md")
                    val note = Note(
                        id = "ext::${uri}", name = name,
                        bookId = bookId, folderPath = folderPath, ext = "md",
                        externalUri = uri.toString(), loaded = true,
                        createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()
                    )
                    withContext(Dispatchers.IO) { db.noteDao().insert(note) }
                    withContext(Dispatchers.Main) { onCreated(note) }
                    return@launch
                } catch (_: Exception) {}
            }
            val note = Note(
                id = java.util.UUID.randomUUID().toString(),
                name = name, bookId = bookId, folderPath = folderPath, ext = "md",
                createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()
            )
            withContext(Dispatchers.IO) { db.noteDao().insert(note) }
            withContext(Dispatchers.Main) { onCreated(note) }
        }
    }

    fun renameNote(noteId: String, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.noteDao().updateName(noteId, newName, System.currentTimeMillis())
        }
    }

    fun moveNote(noteId: String, targetFolder: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.noteDao().moveNote(noteId, targetFolder, System.currentTimeMillis())
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            try {
                val note = withContext(Dispatchers.IO) { db.noteDao().getById(noteId) }
                if (note?.externalUri != null) {
                    try {
                        SAFHelper.deleteDocument(getApplication(), Uri.parse(note.externalUri))
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
            withContext(Dispatchers.IO) { db.noteDao().deleteById(noteId) }
        }
    }

    /** Duplicate [noteId] into the same book/folder as a DB-only copy (no SAF write). */
    fun duplicateNote(noteId: String) {
        viewModelScope.launch {
            val original = withContext(Dispatchers.IO) { db.noteDao().getById(noteId) } ?: return@launch
            val newNote  = original.copy(
                id          = java.util.UUID.randomUUID().toString(),
                name        = "${original.name} (copy)",
                externalUri = null,
                createdAt   = System.currentTimeMillis(),
                updatedAt   = System.currentTimeMillis()
            )
            withContext(Dispatchers.IO) { db.noteDao().insert(newNote) }
        }
    }

    fun createFolder(bookId: String = Note.DEFAULT_BOOK_ID, path: String) {
        viewModelScope.launch {
            val cleaned = if (path.startsWith("/")) path else "/$path"
            val exr = _externalRoot.value
            if (exr != null) {
                try {
                    val folderName = cleaned.split("/").last()
                    val uri = SAFHelper.createFolder(getApplication(), Uri.parse(exr.uri), folderName)
                    withContext(Dispatchers.IO) {
                        db.noteDao().insertFolder(Folder(path = cleaned, externalUri = uri.toString()))
                    }
                } catch (_: Exception) {}
            } else {
                withContext(Dispatchers.IO) {
                    db.noteDao().insertFolder(Folder(bookId = bookId, path = cleaned))
                }
            }
        }
    }

    fun deleteFolder(bookId: String = Note.DEFAULT_BOOK_ID, path: String) {
        if (path == "/") return
        viewModelScope.launch(Dispatchers.IO) {
            db.noteDao().deleteFolder(bookId, path)
            val orphans = db.noteDao().getByBookFolder(bookId, path)
            for (note in orphans) {
                db.noteDao().moveNote(note.id, "/", System.currentTimeMillis())
            }
        }
    }

    // ── SAF ───────────────────────────────────────────────────────────────────

    fun connectExternalFolder(uri: Uri, name: String) {
        val root = ExternalRoot(uri = uri.toString(), name = name)
        prefs.externalRootJson = gson.toJson(root)
        _externalRoot.value = root
        SAFHelper.takePersistablePermission(getApplication(), uri)
        scanExternalFolder(root)
    }

    fun refreshExternalFolder() {
        val root = _externalRoot.value ?: return
        scanExternalFolder(root)
    }

    fun disconnectExternalFolder() {
        val root = _externalRoot.value ?: return
        try { SAFHelper.releasePersistablePermission(getApplication(), Uri.parse(root.uri)) }
        catch (_: Exception) {}
        _externalRoot.value = null
        prefs.externalRootJson = null
        viewModelScope.launch(Dispatchers.IO) {
            db.noteDao().deleteAllExternal()
            db.noteDao().deleteAllExternalFolders()
        }
    }

    private fun loadExternalRoot() {
        val json = prefs.externalRootJson ?: return
        try { _externalRoot.value = gson.fromJson(json, ExternalRoot::class.java) }
        catch (_: Exception) {}
    }

    private fun scanExternalFolder(root: ExternalRoot) {
        _externalLoading.value = true
        viewModelScope.launch {
            try {
                val result: SafScanResult = withContext(Dispatchers.IO) {
                    SAFHelper.scanFolderTree(getApplication(), Uri.parse(root.uri))
                }
                withContext(Dispatchers.IO) {
                    db.noteDao().deleteAllExternal()
                    db.noteDao().deleteAllExternalFolders()
                    db.noteDao().insertFolder(Folder(path = "/", externalUri = root.uri))
                    val folderEntries = result.folders.map {
                        Folder(path = it.relativePath, externalUri = it.uri)
                    }
                    db.noteDao().insertFolders(folderEntries)
                    val noteEntries = result.files.map { f ->
                        val ext = if (f.ext in setOf("md", "mdown", "markdown")) "md" else "txt"
                        Note(
                            id = "ext::${f.uri}", name = f.name,
                            bookId = Note.DEFAULT_BOOK_ID,
                            folderPath = f.folderPath,
                            ext = ext, content = "", loaded = false,
                            externalUri = f.uri,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                    }
                    db.noteDao().insertAll(noteEntries)
                }
            } catch (_: Exception) {}
            _externalLoading.value = false
        }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    fun search(query: String) {
        if (query.isBlank()) { _searchResults.value = emptyList(); return }
        viewModelScope.launch(Dispatchers.IO) {
            val results = db.noteDao().search(query)
            withContext(Dispatchers.Main) { _searchResults.value = results }
        }
    }

    fun notesInFolder(folderPath: String): List<Note> =
        (notes.value ?: emptyList()).filter { it.folderPath == folderPath }
            .sortedByDescending { it.updatedAt }

    fun childFolders(folderPath: String): List<Folder> {
        val prefix = if (folderPath == "/") "/" else "$folderPath/"
        return (folders.value ?: emptyList()).filter { folder ->
            folder.path != folderPath
                && folder.path.startsWith(prefix)
                && !folder.path.removePrefix(prefix).contains("/")
        }.sortedBy { it.path }
    }
}
