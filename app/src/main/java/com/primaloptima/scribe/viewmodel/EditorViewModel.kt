package com.primaloptima.scribe.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.primaloptima.scribe.ScribeApp
import com.primaloptima.scribe.util.AppJson
import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.data.NoteVersion
import com.primaloptima.scribe.util.DefaultThemes
import com.primaloptima.scribe.util.MarkdownUtil
import com.primaloptima.scribe.util.RecoveryManager
import com.primaloptima.scribe.util.SAFHelper
import com.primaloptima.scribe.util.model.AppTheme
import com.primaloptima.scribe.util.model.ExternalRoot
import kotlinx.serialization.decodeFromString
import com.primaloptima.scribe.util.model.FloatingWindow
import com.primaloptima.scribe.util.model.OutlineEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EditorViewModel(
    application: Application,
    private val savedState: SavedStateHandle
) : AndroidViewModel(application) {

    private companion object {
        const val KEY_LAST_SAVED_CONTENT = "last_saved_content"
        const val KEY_LAST_WORD_COUNT    = "last_word_count"
        const val KEY_LAST_SNAPSHOT_WC   = "last_snapshot_word_count"
        const val KEY_PENDING_CONTENT    = "pending_content"
    }

    private val app = application as ScribeApp
    private val db = app.database
    private val dataStore = app.dataStore
    private val themeManager = app.themeManager

    // ── Date helpers ──────────────────────────────────────────────────────────

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private fun todayStr(): String = dateFmt.format(Date())

    // ── Active note ───────────────────────────────────────────────────────────

    private val _activeNote = MutableStateFlow<Note?>(null)
    val activeNote: StateFlow<Note?> = _activeNote.asStateFlow()

    private val _outline = MutableStateFlow<List<OutlineEntry>>(emptyList())
    val outline: StateFlow<List<OutlineEntry>> = _outline.asStateFlow()

    // ── Floating Windows & Pinned Slots ────────────────────────────────────────

    private val _floatingWindows = MutableStateFlow<List<FloatingWindow>>(emptyList())
    val floatingWindows: StateFlow<List<FloatingWindow>> = _floatingWindows.asStateFlow()

    private val _pinnedTopNotes = MutableStateFlow<List<String>>(emptyList())
    val pinnedTopNotes: StateFlow<List<String>> = _pinnedTopNotes.asStateFlow()

    private val _pinnedTopIndex = MutableStateFlow(0)
    val pinnedTopIndex: StateFlow<Int> = _pinnedTopIndex.asStateFlow()

    private val _pinnedBottomNotes = MutableStateFlow<List<String>>(emptyList())
    val pinnedBottomNotes: StateFlow<List<String>> = _pinnedBottomNotes.asStateFlow()

    private val _pinnedBottomIndex = MutableStateFlow(0)
    val pinnedBottomIndex: StateFlow<Int> = _pinnedBottomIndex.asStateFlow()

    // ── Companion panel UI prefs (persisted) ──────────────────────────────────
    val companionTabBarBottom: StateFlow<Boolean> = dataStore.companionTabBarBottomFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val companionSplitHorizontal: StateFlow<Boolean> = dataStore.companionSplitHorizontalFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setCompanionTabBarBottom(v: Boolean) {
        viewModelScope.launch { dataStore.setCompanionTabBarBottom(v) }
    }

    fun setCompanionSplitHorizontal(v: Boolean) {
        viewModelScope.launch { dataStore.setCompanionSplitHorizontal(v) }
    }

    // ── Pinned notes helper: save to DataStore ────────────────────────────────
    private fun persistPinnedTop() {
        viewModelScope.launch {
            dataStore.setPinnedTopJson(AppJson.encodeToString(_pinnedTopNotes.value))
        }
    }

    private fun persistPinnedBottom() {
        viewModelScope.launch {
            dataStore.setPinnedBottomJson(AppJson.encodeToString(_pinnedBottomNotes.value))
        }
    }

    fun addPinnedTop(noteId: String) {
        val list = _pinnedTopNotes.value.toMutableList()
        if (!list.contains(noteId)) {
            list.add(noteId)
            _pinnedTopNotes.value = list
            _pinnedTopIndex.value = list.size - 1
        } else {
            _pinnedTopIndex.value = list.indexOf(noteId)
        }
        persistPinnedTop()
    }

    fun removePinnedTop(noteId: String) {
        val list = _pinnedTopNotes.value.toMutableList()
        list.remove(noteId)
        _pinnedTopNotes.value = list
        val currIdx = _pinnedTopIndex.value
        _pinnedTopIndex.value = if (list.isEmpty()) 0 else currIdx.coerceIn(0, list.size - 1)
        persistPinnedTop()
    }

    fun prevPinnedTop() {
        val list = _pinnedTopNotes.value
        if (list.size <= 1) return
        val curr = _pinnedTopIndex.value
        _pinnedTopIndex.value = if (curr > 0) curr - 1 else list.size - 1
    }

    fun nextPinnedTop() {
        val list = _pinnedTopNotes.value
        if (list.size <= 1) return
        val curr = _pinnedTopIndex.value
        _pinnedTopIndex.value = if (curr < list.size - 1) curr + 1 else 0
    }

    /** Swap top & bottom pinned slot contents. */
    fun swapPinnedSlots() {
        val oldTop    = _pinnedTopNotes.value
        val oldBottom = _pinnedBottomNotes.value
        _pinnedTopNotes.value    = oldBottom
        _pinnedBottomNotes.value = oldTop
        _pinnedTopIndex.value    = 0
        _pinnedBottomIndex.value = 0
        persistPinnedTop()
        persistPinnedBottom()
    }

    fun addPinnedBottom(noteId: String) {
        val list = _pinnedBottomNotes.value.toMutableList()
        if (!list.contains(noteId)) {
            list.add(noteId)
            _pinnedBottomNotes.value = list
            _pinnedBottomIndex.value = list.size - 1
        } else {
            _pinnedBottomIndex.value = list.indexOf(noteId)
        }
        persistPinnedBottom()
    }

    fun removePinnedBottom(noteId: String) {
        val list = _pinnedBottomNotes.value.toMutableList()
        list.remove(noteId)
        _pinnedBottomNotes.value = list
        val currIdx = _pinnedBottomIndex.value
        _pinnedBottomIndex.value = if (list.isEmpty()) 0 else currIdx.coerceIn(0, list.size - 1)
        persistPinnedBottom()
    }

    fun prevPinnedBottom() {
        val list = _pinnedBottomNotes.value
        if (list.size <= 1) return
        val curr = _pinnedBottomIndex.value
        _pinnedBottomIndex.value = if (curr > 0) curr - 1 else list.size - 1
    }

    fun nextPinnedBottom() {
        val list = _pinnedBottomNotes.value
        if (list.size <= 1) return
        val curr = _pinnedBottomIndex.value
        _pinnedBottomIndex.value = if (curr < list.size - 1) curr + 1 else 0
    }

    fun openFloatingWindow(noteId: String) {
        val current = _floatingWindows.value.toMutableList()
        if (current.none { it.noteId == noteId }) {
            current.add(
                FloatingWindow(
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
        _floatingWindows.value = _floatingWindows.value.filter { it.id != windowId }
    }

    fun toggleCollapseFloatingWindow(windowId: String) {
        _floatingWindows.value = _floatingWindows.value.map {
            if (it.id == windowId) it.copy(collapsed = !it.collapsed) else it
        }
    }

    fun moveFloatingWindow(windowId: String, x: Float, y: Float) {
        _floatingWindows.value = _floatingWindows.value.map {
            if (it.id == windowId) it.copy(x = x, y = y) else it
        }
    }

    // ── Theme ─────────────────────────────────────────────────────────────────

    private val _theme = MutableStateFlow<AppTheme?>(null)
    val theme: StateFlow<AppTheme?> = _theme.asStateFlow()

    // ── Word count ────────────────────────────────────────────────────────────

    private val _wordCount = MutableStateFlow(0)
    val wordCount: StateFlow<Int> = _wordCount.asStateFlow()

    private val _charCount = MutableStateFlow(0)
    val charCount: StateFlow<Int> = _charCount.asStateFlow()

    private val _readingTime = MutableStateFlow(0)
    val readingTime: StateFlow<Int> = _readingTime.asStateFlow()

    // ── Goal ──────────────────────────────────────────────────────────────────

    private val _goalProgress = MutableStateFlow(0f)
    val goalProgress: StateFlow<Float> = _goalProgress.asStateFlow()

    private val _goalReached = MutableStateFlow(false)
    val goalReached: StateFlow<Boolean> = _goalReached.asStateFlow()

    // Reactive DataStore-backed daily goal (kept — goal setting lives in DataStore).
    private val _dailyGoalState = dataStore.dailyGoalFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 500)

    // ── Recovery ──────────────────────────────────────────────────────────────

    private val _recoveryAvailable = MutableStateFlow(false)
    val recoveryAvailable: StateFlow<Boolean> = _recoveryAvailable.asStateFlow()

    // ── SAF external folder ───────────────────────────────────────────────────

    private val _externalRoot = MutableStateFlow<ExternalRoot?>(null)
    val externalRoot: StateFlow<ExternalRoot?> = _externalRoot.asStateFlow()

    private val _externalLoading = MutableStateFlow(false)
    val externalLoading: StateFlow<Boolean> = _externalLoading.asStateFlow()

    // ── Autosave ──────────────────────────────────────────────────────────────

    private var autosaveJob: Job? = null
    private var statsJob: Job? = null
    // Bug 4: debounce RecoveryManager writes (was firing on every keystroke).
    private var recoveryJob: Job? = null

    // Bug 3: Back these four fields with SavedStateHandle so they survive
    // process death. All existing reads/writes use the same property names —
    // the backing change is fully transparent to the rest of the class.
    private var lastSavedContent: String
        get() = savedState[KEY_LAST_SAVED_CONTENT] ?: ""
        set(v) { savedState[KEY_LAST_SAVED_CONTENT] = v }

    private var lastWordCount: Int
        get() = savedState[KEY_LAST_WORD_COUNT] ?: 0
        set(v) { savedState[KEY_LAST_WORD_COUNT] = v }

    /** Word count at the time of the last auto-snapshot — used for the min-words gate. */
    private var lastSnapshotWordCount: Int
        get() = savedState[KEY_LAST_SNAPSHOT_WC] ?: 0
        set(v) { savedState[KEY_LAST_SNAPSHOT_WC] = v }

    // Bug 1: track latest content so flushPendingContent() can flush without
    // needing the text passed in from ScribeActivity.
    private var pendingContent: String
        get() = savedState[KEY_PENDING_CONTENT] ?: ""
        set(v) { savedState[KEY_PENDING_CONTENT] = v }

    private val AUTOSAVE_DEBOUNCE_MS = 500L
    private val STATS_DEBOUNCE_MS    = 300L
    private val RECOVERY_DEBOUNCE_MS = 2000L

    // ── Zen / UI state ────────────────────────────────────────────────────────

    private val _zenMode = MutableStateFlow(false)
    val zenMode: StateFlow<Boolean> = _zenMode.asStateFlow()

    fun toggleZen() { _zenMode.value = !_zenMode.value }
    fun setZen(v: Boolean) { _zenMode.value = v }

    // Typewriter mode: reactive StateFlow instead of a synchronous prefs read
    val typewriterMode: StateFlow<Boolean> = dataStore.typewriterModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        viewModelScope.launch {
            dataStore.activeThemeIdFlow.collectLatest { themeId ->
                _theme.value = themeManager.allThemes()
                    .firstOrNull { it.id == themeId }
                    ?: DefaultThemes.all.first()
            }
        }
        // Restore persisted pinned note IDs on startup
        viewModelScope.launch {
            dataStore.pinnedTopJsonFlow.collectLatest { json ->
                if (!json.isNullOrBlank()) {
                    try {
                        val ids = AppJson.decodeFromString<List<String>>(json)
                        _pinnedTopNotes.value = ids
                        _pinnedTopIndex.value = if (ids.isEmpty()) 0 else _pinnedTopIndex.value.coerceIn(0, ids.size - 1)
                    } catch (_: Exception) { }
                }
            }
        }
        viewModelScope.launch {
            dataStore.pinnedBottomJsonFlow.collectLatest { json ->
                if (!json.isNullOrBlank()) {
                    try {
                        val ids = AppJson.decodeFromString<List<String>>(json)
                        _pinnedBottomNotes.value = ids
                        _pinnedBottomIndex.value = if (ids.isEmpty()) 0 else _pinnedBottomIndex.value.coerceIn(0, ids.size - 1)
                    } catch (_: Exception) { }
                }
            }
        }
        loadExternalRoot()
    }

    // ── Note loading ──────────────────────────────────────────────────────────

    /** The currently in-flight loadNote Job; cancelled by clearActiveNote(). */
    private var loadNoteJob: Job? = null

    fun loadNote(noteId: String) {
        if (_activeNote.value?.id == noteId) return
        if (loadNoteJob?.isActive == true) return
        loadNoteJob = viewModelScope.launch {
            val note = withContext(Dispatchers.IO) { db.noteDao().getById(noteId) } ?: return@launch
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
            lastSnapshotWordCount = lastWordCount
            updateStatsSync(loaded.content)
            checkRecovery(loaded)
            dataStore.setActiveNoteId(loaded.id)
        }
    }

    fun clearActiveNote() {
        loadNoteJob?.cancel()
        loadNoteJob = null
        autosaveJob?.cancel()
        autosaveJob = null
        statsJob?.cancel()
        statsJob = null
        recoveryJob?.cancel()   // Bug 4: cancel debounced recovery write on note exit
        recoveryJob = null
        _activeNote.value = null
        _outline.value = emptyList()
        _wordCount.value = 0
        _charCount.value = 0
        _readingTime.value = 0
        _goalProgress.value = 0f
        _recoveryAvailable.value = false
        lastSavedContent = ""
        lastWordCount = 0
        lastSnapshotWordCount = 0
        viewModelScope.launch { dataStore.setActiveNoteId(null) }
    }

    // ── Content change ────────────────────────────────────────────────────────

    fun onContentChanged(content: String) {
        // Bug 1: track latest content so flushPendingContent() can flush without
        // needing the caller to pass the text in separately.
        pendingContent = content

        // 1. Cancel and reschedule autosave (debounced at 500ms)
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(AUTOSAVE_DEBOUNCE_MS)
            saveContent(content)
        }

        // 2. Stats update — debounced separately on Default dispatcher (CPU work off main thread)
        statsJob?.cancel()
        statsJob = viewModelScope.launch {
            delay(STATS_DEBOUNCE_MS)
            withContext(Dispatchers.Default) {
                val wc = MarkdownUtil.countWords(content)
                val cc = MarkdownUtil.countChars(content)
                val rt = MarkdownUtil.readingTimeMinutes(content)
                val outline = MarkdownUtil.extractOutline(content)
                withContext(Dispatchers.Main) {
                    _wordCount.value = wc
                    _charCount.value = cc
                    _readingTime.value = rt
                    _outline.value = outline
                }
            }
        }

        // 3. Recovery write — Bug 4: debounced at 2s (was firing on every keystroke,
        // causing excessive SharedPreferences I/O on older devices).
        val noteId = _activeNote.value?.id ?: return
        recoveryJob?.cancel()
        recoveryJob = viewModelScope.launch {
            delay(RECOVERY_DEBOUNCE_MS)
            withContext(Dispatchers.IO) {
                RecoveryManager.set(getApplication(), noteId, content)
            }
        }
    }

    /**
     * Bug 1+5 fix: called before navigating to History (or any sub-screen) so
     * keystrokes typed within the 500ms debounce window are not lost.
     * Cancels the pending autosave job and immediately persists the latest content.
     */
    fun flushPendingContent() {
        autosaveJob?.cancel()
        val content = pendingContent
        if (content.isNotEmpty() && content != lastSavedContent) {
            viewModelScope.launch(Dispatchers.IO) {
                saveContent(content)
            }
        }
    }

    private suspend fun saveContent(content: String) {
        val note = _activeNote.value ?: return
        if (content == lastSavedContent) return

        val newWords = MarkdownUtil.countWords(content)
        val delta = newWords - lastWordCount
        lastWordCount = newWords

        withContext(Dispatchers.IO) {
            if (note.externalUri != null) {
                try { SAFHelper.writeFile(getApplication(), Uri.parse(note.externalUri), content) }
                catch (_: Exception) {}
            }
            // Fix (Bug 3): Both DB writes run inside a single @Transaction so they
            // can never diverge if the process is killed between them.
            val today = todayStr()
            db.writingLogDao().saveContentAndDelta(
                noteDao    = db.noteDao(),
                noteId     = note.id,
                content    = content,
                wordCount  = newWords,
                updatedAt  = System.currentTimeMillis(),
                date       = today,
                bookId     = note.bookId,
                folderPath = note.folderPath,
                delta      = delta
            )
            if (delta != 0) {
                updateGoalFromDb(today)
            }
        }

        // Keep _activeNote in sync with the saved content so that when Nav3
        // recreates the Editor composition (e.g. returning from History),
        // activeNote.content is not stale and the editor is populated correctly.
        _activeNote.value = note.copy(content = content, updatedAt = System.currentTimeMillis())
        lastSavedContent = content
    }

    // ── Goal (DB-backed, replaces DataStore-backed updateGoal) ────────────────

    /**
     * Reads today's total from writing_log and updates goal progress.
     * Called after every save that produced a non-zero delta.
     * Must be called from a coroutine already on IO (inside withContext(Dispatchers.IO)).
     */
    private suspend fun updateGoalFromDb(today: String) {
        val goal  = _dailyGoalState.value.toFloat()
        val words = db.writingLogDao().getTodayWords(today).toFloat()
        _goalProgress.value = if (goal > 0) (words / goal).coerceIn(0f, 1f) else 0f
        _goalReached.value  = words >= goal
    }

    /**
     * Synchronous goal update used at note-load time (no writing_log read needed —
     * today's total is not yet meaningful until the user starts editing).
     */
    private fun updateGoalSync() {
        _goalProgress.value = 0f
        _goalReached.value  = false
    }

    /**
     * Called when the writer leaves a note (DisposableEffect onDispose).
     * Saves an "auto" snapshot only if auto-history is enabled and the net
     * word change since the last snapshot meets the minimum threshold.
     */
    fun saveVersionSnapshotOnLeave(content: String) {
        val note = _activeNote.value ?: return
        if (content.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            if (!dataStore.getAutoHistoryEnabled()) return@launch
            val currentWords = MarkdownUtil.countWords(content)
            val wordDelta = Math.abs(currentWords - lastSnapshotWordCount)
            if (wordDelta < dataStore.getAutoHistoryMinWords()) return@launch
            db.noteVersionDao().insert(
                NoteVersion(
                    noteId = note.id,
                    content = content,
                    wordCount = currentWords,
                    timestamp = System.currentTimeMillis(),
                    type = NoteVersion.TYPE_AUTO
                )
            )
            db.noteVersionDao().trimByType(note.id, NoteVersion.TYPE_AUTO, dataStore.getAutoHistorySlots())
            lastSnapshotWordCount = currentWords
        }
    }

    /**
     * Called when the writer taps the checkpoint (save) button in the toolbar.
     * Always saves regardless of word count, bypasses the min-words gate.
     */
    fun saveManualSnapshot(content: String) {
        val note = _activeNote.value ?: return
        if (content.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            if (!dataStore.getManualCheckpointsEnabled()) return@launch
            val words = MarkdownUtil.countWords(content)
            db.noteVersionDao().insert(
                NoteVersion(
                    noteId = note.id,
                    content = content,
                    wordCount = words,
                    timestamp = System.currentTimeMillis(),
                    type = NoteVersion.TYPE_MANUAL
                )
            )
            db.noteVersionDao().trimByType(note.id, NoteVersion.TYPE_MANUAL, dataStore.getManualCheckpointSlots())
            lastSnapshotWordCount = words
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
                val wc = MarkdownUtil.countWords(content)
                // Fix (Bug 2): compute and record the delta so words written between
                // the last autosave and app-going-to-background are not silently lost
                // from writing_log (daily goal, charts, streaks).
                val delta = wc - lastWordCount
                val today = todayStr()
                db.writingLogDao().saveContentAndDelta(
                    noteDao    = db.noteDao(),
                    noteId     = note.id,
                    content    = content,
                    wordCount  = wc,
                    updatedAt  = System.currentTimeMillis(),
                    date       = today,
                    bookId     = note.bookId,
                    folderPath = note.folderPath,
                    delta      = delta
                )
                lastWordCount = wc
                lastSavedContent = content
            }
        }
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    /** Synchronous stats update — called immediately when a note is loaded. */
    private fun updateStatsSync(content: String) {
        _wordCount.value = MarkdownUtil.countWords(content)
        _charCount.value = MarkdownUtil.countChars(content)
        _readingTime.value = MarkdownUtil.readingTimeMinutes(content)
        _outline.value = MarkdownUtil.extractOutline(content)
        updateGoalSync()
    }

    // ── Recovery ──────────────────────────────────────────────────────────────

    private fun checkRecovery(note: Note) {
        val saved = RecoveryManager.get(getApplication(), note.id) ?: return
        if (saved != note.content) _recoveryAvailable.value = true
    }

    fun getRecoveryContent(): String? {
        val noteId = _activeNote.value?.id ?: return null
        return RecoveryManager.get(getApplication(), noteId)
    }

    fun dismissRecovery() {
        val noteId = _activeNote.value?.id ?: return
        viewModelScope.launch(Dispatchers.IO) {
            RecoveryManager.clear(getApplication(), noteId)
        }
        _recoveryAvailable.value = false
    }

    // ── History ───────────────────────────────────────────────────────────────

    fun restoreSnapshot(content: String) {
        val note = _activeNote.value ?: return
        val restoredWc = MarkdownUtil.countWords(content)
        // Fix (Bug 1): capture the baseline before mutating it so the corrective
        // delta is calculated against the right value.
        val previousWc = lastWordCount
        viewModelScope.launch(Dispatchers.IO) {
            val today = todayStr()
            val delta = restoredWc - previousWc
            // Atomically update notes and write_log so both reflect the restore.
            db.writingLogDao().saveContentAndDelta(
                noteDao    = db.noteDao(),
                noteId     = note.id,
                content    = content,
                wordCount  = restoredWc,
                updatedAt  = System.currentTimeMillis(),
                date       = today,
                bookId     = note.bookId,
                folderPath = note.folderPath,
                delta      = delta
            )
        }
        _activeNote.value = note.copy(content = content, updatedAt = System.currentTimeMillis())
        lastSavedContent = content
        // Fix (Bug 1): reset lastWordCount so the next autosave delta is
        // calculated from the restored baseline, not the pre-restore value.
        lastWordCount = restoredWc
        updateStatsSync(content)
    }

    // ── SAF ───────────────────────────────────────────────────────────────────

    private fun loadExternalRoot() {
        viewModelScope.launch {
            val json = dataStore.getExternalRootJson() ?: return@launch
            try {
                val ext = AppJson.decodeFromString<ExternalRoot>(json)
                _externalRoot.value = ext
            } catch (_: Exception) {}
        }
    }

    fun disconnectExternalFolder() {
        _externalRoot.value = null
        viewModelScope.launch { dataStore.setExternalRootJson(null) }
    }
}
