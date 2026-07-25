package com.primaloptima.scribe.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.primaloptima.scribe.ScribeApp
import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.util.HistoryManager
import com.primaloptima.scribe.util.MarkdownUtil
import com.primaloptima.scribe.util.SAFHelper
import com.primaloptima.scribe.util.DefaultThemes
import com.primaloptima.scribe.util.ThemeDataStoreRepo
import com.primaloptima.scribe.util.WritingStats
import com.primaloptima.scribe.util.model.AppTheme
import com.primaloptima.scribe.util.model.ExternalRoot
import com.primaloptima.scribe.util.model.HistorySnapshot
import com.primaloptima.scribe.util.model.OutlineEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ScribeApp
    private val prefs = app.prefs
    private val db = app.database
    private val themeManager = app.themeManager
    private val dataStoreRepo = ThemeDataStoreRepo(application)
    private val historyManager = HistoryManager(prefs)
    val writingStats = WritingStats(prefs)

    // ── Active note ───────────────────────────────────────────────────────────

    private val _activeNote = MutableLiveData<Note?>()
    val activeNote: LiveData<Note?> = _activeNote

    private val _outline = MutableLiveData<List<OutlineEntry>>(emptyList())
    val outline: LiveData<List<OutlineEntry>> = _outline

    // ── Floating Windows & Pinned Slots ────────────────────────────────────────

    private val _floatingWindows = MutableLiveData<List<com.primaloptima.scribe.util.model.FloatingWindow>>(emptyList())
    val floatingWindows: LiveData<List<com.primaloptima.scribe.util.model.FloatingWindow>> = _floatingWindows

    private val _pinnedTopNotes = MutableLiveData<List<String>>(emptyList())
    val pinnedTopNotes: LiveData<List<String>> = _pinnedTopNotes

    private val _pinnedTopIndex = MutableLiveData<Int>(0)
    val pinnedTopIndex: LiveData<Int> = _pinnedTopIndex

    private val _pinnedBottomNotes = MutableLiveData<List<String>>(emptyList())
    val pinnedBottomNotes: LiveData<List<String>> = _pinnedBottomNotes

    private val _pinnedBottomIndex = MutableLiveData<Int>(0)
    val pinnedBottomIndex: LiveData<Int> = _pinnedBottomIndex

    fun addPinnedTop(noteId: String) {
        val list = _pinnedTopNotes.value.orEmpty().toMutableList()
        if (!list.contains(noteId)) {
            list.add(noteId)
            _pinnedTopNotes.value = list
            _pinnedTopIndex.value = list.size - 1
        } else {
            _pinnedTopIndex.value = list.indexOf(noteId)
        }
    }

    fun removePinnedTop(noteId: String) {
        val list = _pinnedTopNotes.value.orEmpty().toMutableList()
        list.remove(noteId)
        _pinnedTopNotes.value = list
        val currIdx = _pinnedTopIndex.value ?: 0
        if (list.isEmpty()) {
            _pinnedTopIndex.value = 0
        } else {
            _pinnedTopIndex.value = currIdx.coerceIn(0, list.size - 1)
        }
    }

    fun prevPinnedTop() {
        val list = _pinnedTopNotes.value.orEmpty()
        if (list.size <= 1) return
        val curr = _pinnedTopIndex.value ?: 0
        _pinnedTopIndex.value = if (curr > 0) curr - 1 else list.size - 1
    }

    fun nextPinnedTop() {
        val list = _pinnedTopNotes.value.orEmpty()
        if (list.size <= 1) return
        val curr = _pinnedTopIndex.value ?: 0
        _pinnedTopIndex.value = if (curr < list.size - 1) curr + 1 else 0
    }

    fun addPinnedBottom(noteId: String) {
        val list = _pinnedBottomNotes.value.orEmpty().toMutableList()
        if (!list.contains(noteId)) {
            list.add(noteId)
            _pinnedBottomNotes.value = list
            _pinnedBottomIndex.value = list.size - 1
        } else {
            _pinnedBottomIndex.value = list.indexOf(noteId)
        }
    }

    fun removePinnedBottom(noteId: String) {
        val list = _pinnedBottomNotes.value.orEmpty().toMutableList()
        list.remove(noteId)
        _pinnedBottomNotes.value = list
        val currIdx = _pinnedBottomIndex.value ?: 0
        if (list.isEmpty()) {
            _pinnedBottomIndex.value = 0
        } else {
            _pinnedBottomIndex.value = currIdx.coerceIn(0, list.size - 1)
        }
    }

    fun prevPinnedBottom() {
        val list = _pinnedBottomNotes.value.orEmpty()
        if (list.size <= 1) return
        val curr = _pinnedBottomIndex.value ?: 0
        _pinnedBottomIndex.value = if (curr > 0) curr - 1 else list.size - 1
    }

    fun nextPinnedBottom() {
        val list = _pinnedBottomNotes.value.orEmpty()
        if (list.size <= 1) return
        val curr = _pinnedBottomIndex.value ?: 0
        _pinnedBottomIndex.value = if (curr < list.size - 1) curr + 1 else 0
    }

    fun openFloatingWindow(noteId: String) {
        val current = _floatingWindows.value.orEmpty().toMutableList()
        if (current.none { it.noteId == noteId }) {
            current.add(
                com.primaloptima.scribe.util.model.FloatingWindow(
                    id = System.currentTimeMillis().toString(),
                    noteId = noteId,
                    x = 80f,
                    y = 120f,
                    width = 280,
                    height = 200,
                    zOrder = current.size
                )
            )
            _floatingWindows.value = current
        }
    }

    fun closeFloatingWindow(windowId: String) {
        val current = _floatingWindows.value.orEmpty().filter { it.id != windowId }
        _floatingWindows.value = current
    }

    fun toggleCollapseFloatingWindow(windowId: String) {
        val current = _floatingWindows.value.orEmpty().map {
            if (it.id == windowId) it.copy(collapsed = !it.collapsed) else it
        }
        _floatingWindows.value = current
    }

    fun moveFloatingWindow(windowId: String, x: Float, y: Float) {
        val current = _floatingWindows.value.orEmpty().map {
            if (it.id == windowId) it.copy(x = x, y = y) else it
        }
        _floatingWindows.value = current
    }

    // ── Theme ─────────────────────────────────────────────────────────────────

    private val _theme = MutableLiveData<AppTheme>()
    val theme: LiveData<AppTheme> = _theme

    // ── Word count ────────────────────────────────────────────────────────────

    private val _wordCount = MutableLiveData(0)
    val wordCount: LiveData<Int> = _wordCount

    private val _charCount = MutableLiveData(0)
    val charCount: LiveData<Int> = _charCount

    private val _readingTime = MutableLiveData(0)
    val readingTime: LiveData<Int> = _readingTime

    // ── Goal ──────────────────────────────────────────────────────────────────

    private val _goalProgress = MutableLiveData(0f)
    val goalProgress: LiveData<Float> = _goalProgress

    private val _goalReached = MutableLiveData(false)
    val goalReached: LiveData<Boolean> = _goalReached

    // ── Recovery ──────────────────────────────────────────────────────────────

    private val _recoveryAvailable = MutableLiveData(false)
    val recoveryAvailable: LiveData<Boolean> = _recoveryAvailable

    // ── SAF external folder ───────────────────────────────────────────────────

    private val _externalRoot = MutableLiveData<ExternalRoot?>()
    val externalRoot: LiveData<ExternalRoot?> = _externalRoot

    private val _externalLoading = MutableLiveData(false)
    val externalLoading: LiveData<Boolean> = _externalLoading

    // ── Autosave ──────────────────────────────────────────────────────────────

    private var autosaveJob: Job? = null
    private var lastSavedContent: String = ""
    private var lastWordCount: Int = 0
    private val AUTOSAVE_DEBOUNCE_MS = 500L

    // ── Zen / UI state ────────────────────────────────────────────────────────

    private val _zenMode = MutableLiveData(false)
    val zenMode: LiveData<Boolean> = _zenMode

    fun toggleZen() { _zenMode.value = !(_zenMode.value ?: false) }
    fun setZen(v: Boolean) { _zenMode.value = v }

    val typewriterMode: Boolean get() = prefs.typewriterMode

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        viewModelScope.launch {
            dataStoreRepo.activeThemeIdFlow.collectLatest { themeId ->
                _theme.value = themeManager.allThemes()
                    .firstOrNull { it.id == themeId }
                    ?: DefaultThemes.all.first()
            }
        }
        writingStats.reconcileStreak()
        loadExternalRoot()
    }

    // ── Note loading ──────────────────────────────────────────────────────────

    /** The currently in-flight loadNote Job; cancelled by clearActiveNote(). */
    private var loadNoteJob: Job? = null

    fun loadNote(noteId: String) {
        // Guard: if the requested note is already active or a load is in flight
        // for the same note, don't launch a duplicate coroutine.
        if (_activeNote.value?.id == noteId) return
        if (loadNoteJob?.isActive == true) return
        loadNoteJob = viewModelScope.launch {
            val note = withContext(Dispatchers.IO) { db.noteDao().getById(noteId) } ?: return@launch
            // For SAF-backed notes, lazily read content from disk if not yet loaded
            val loaded = if (note.externalUri != null && !note.loaded) {
                try {
                    val content = SAFHelper.readFile(getApplication(), Uri.parse(note.externalUri))
                    val updated = note.copy(content = content, loaded = true)
                    withContext(Dispatchers.IO) { db.noteDao().update(updated) }
                    updated
                } catch (_: Exception) { note.copy(loaded = true) }
            } else note

            _activeNote.value = loaded
            lastSavedContent = loaded.content
            lastWordCount = MarkdownUtil.countWords(loaded.content)
            updateStats(loaded.content)
            checkRecovery(loaded)
        }
    }

    /** Clear the active note and cancel any pending autosave or in-flight load.
     *  Call this when the active note is deleted so autosave can't resurrect it. */
    fun clearActiveNote() {
        loadNoteJob?.cancel()
        loadNoteJob = null
        autosaveJob?.cancel()
        autosaveJob = null
        _activeNote.value = null
        _outline.value = emptyList()
        _wordCount.value = 0
        _charCount.value = 0
        _readingTime.value = 0
        _goalProgress.value = 0f
        _recoveryAvailable.value = false
        lastSavedContent = ""
        lastWordCount = 0
    }

    // ── Content change (called on every autosave tick) ────────────────────────

    fun onContentChanged(content: String) {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(AUTOSAVE_DEBOUNCE_MS)
            saveContent(content)
        }
        // Update word count and outline synchronously for responsiveness
        updateStats(content)
        _outline.value = MarkdownUtil.extractOutline(content)
        // Recovery buffer — write synchronously (fast, in-memory prefs)
        val noteId = _activeNote.value?.id ?: return
        prefs.setRecovery(noteId, content)
    }

    private suspend fun saveContent(content: String) {
        val note = _activeNote.value ?: return
        if (content == lastSavedContent) return

        val newWords = MarkdownUtil.countWords(content)
        val delta = newWords - lastWordCount
        lastWordCount = newWords

        // Write to Room or SAF
        withContext(Dispatchers.IO) {
            if (note.externalUri != null) {
                try { SAFHelper.writeFile(getApplication(), Uri.parse(note.externalUri), content) }
                catch (_: Exception) {}
            }
            db.noteDao().updateContent(note.id, content, System.currentTimeMillis())
        }

        // History snapshot (time/diff gated)
        withContext(Dispatchers.IO) { historyManager.maybeSnapshot(note.id, content) }

        lastSavedContent = content
        writingStats.recordWordDelta(delta)
        updateGoal()
    }

    fun saveVersionSnapshotOnLeave(content: String) {
        val note = _activeNote.value ?: return
        if (content.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val words = MarkdownUtil.countWords(content)
            db.noteVersionDao().insert(
                com.primaloptima.scribe.data.NoteVersion(
                    noteId = note.id,
                    content = content,
                    wordCount = words,
                    timestamp = System.currentTimeMillis()
                )
            )
            db.noteVersionDao().trimOldVersions(note.id)
        }
    }

    suspend fun getNoteVersions(noteId: String): List<com.primaloptima.scribe.data.NoteVersion> {
        return withContext(Dispatchers.IO) {
            db.noteVersionDao().getVersions(noteId)
        }
    }

    fun flushContent(content: String) {
        autosaveJob?.cancel()
        val note = _activeNote.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            if (content != lastSavedContent) {
                if (note.externalUri != null) {
                    try { SAFHelper.writeFile(getApplication(), Uri.parse(note.externalUri), content) }
                    catch (_: Exception) {}
                }
                db.noteDao().updateContent(note.id, content, System.currentTimeMillis())
                lastSavedContent = content
            }
        }
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    private fun updateStats(content: String) {
        _wordCount.value = MarkdownUtil.countWords(content)
        _charCount.value = MarkdownUtil.countChars(content)
        _readingTime.value = MarkdownUtil.readingTimeMinutes(content)
        updateGoal()
    }

    private fun updateGoal() {
        val goal = writingStats.dailyGoal.toFloat()
        val today = writingStats.todayWords.toFloat()
        _goalProgress.value = if (goal > 0) (today / goal).coerceIn(0f, 1f) else 0f
        _goalReached.value = writingStats.goalReached
    }

    // ── Recovery ──────────────────────────────────────────────────────────────

    private fun checkRecovery(note: Note) {
        val saved = prefs.getRecovery(note.id) ?: return
        if (saved != note.content) _recoveryAvailable.value = true
    }

    fun getRecoveryContent(): String? {
        val noteId = _activeNote.value?.id ?: return null
        return prefs.getRecovery(noteId)
    }

    fun dismissRecovery() {
        val noteId = _activeNote.value?.id ?: return
        prefs.clearRecovery(noteId)
        _recoveryAvailable.value = false
    }

    // ── History ───────────────────────────────────────────────────────────────

    fun getSnapshots(): List<HistorySnapshot> {
        val noteId = _activeNote.value?.id ?: return emptyList()
        return historyManager.getSnapshots(noteId)
    }

    fun restoreSnapshot(content: String) {
        val note = _activeNote.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            db.noteDao().updateContent(note.id, content, System.currentTimeMillis())
        }
        _activeNote.value = note.copy(content = content, updatedAt = System.currentTimeMillis())
        lastSavedContent = content
        updateStats(content)
    }

    // ── SAF ───────────────────────────────────────────────────────────────────

    private fun loadExternalRoot() {
        val json = prefs.externalRootJson ?: return
        try {
            val ext = com.google.gson.Gson().fromJson(json, ExternalRoot::class.java)
            _externalRoot.value = ext
        } catch (_: Exception) {}
    }

    fun disconnectExternalFolder() {
        _externalRoot.value = null
        prefs.externalRootJson = null
    }
}
