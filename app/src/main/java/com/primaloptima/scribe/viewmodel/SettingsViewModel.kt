package com.primaloptima.scribe.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.primaloptima.scribe.ScribeApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ScribeApp
    private val dataStore = app.dataStore
    private val db = app.database
    private val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    // ── Editor settings ───────────────────────────────────────────────────────

    val showWordCount: StateFlow<Boolean> = dataStore.showWordCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val typewriterMode: StateFlow<Boolean> = dataStore.typewriterModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val lineSpacing: StateFlow<String> = dataStore.lineSpacingFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "comfortable")

    val editorFontSize: StateFlow<Int> = dataStore.editorFontSizeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 16)

    // ── Home / navigation ─────────────────────────────────────────────────────

    val homeStartPage: StateFlow<String> = dataStore.homeStartPageFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "books")

    // ── Goals & stats ─────────────────────────────────────────────────────────

    val dailyGoal: StateFlow<Int> = dataStore.dailyGoalFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 500)

    private val _todayWords    = MutableStateFlow(0)
    val todayWords: StateFlow<Int> = _todayWords.asStateFlow()

    private val _currentStreak = MutableStateFlow(0)
    val currentStreak: StateFlow<Int> = _currentStreak.asStateFlow()

    private val _longestStreak = MutableStateFlow(0)
    val longestStreak: StateFlow<Int> = _longestStreak.asStateFlow()

    fun loadStats() {
        viewModelScope.launch {
            _todayWords.value = withContext(Dispatchers.IO) {
                db.writingLogDao().getTodayWords(todayStr)
            }
            val dates = withContext(Dispatchers.IO) {
                db.writingLogDao().getAllWritingDates().map { it.date }
            }
            val (current, longest) = computeStreaks(dates)
            _currentStreak.value = current
            _longestStreak.value = longest
        }
    }

    // ── Version history ───────────────────────────────────────────────────────

    val autoHistoryEnabled: StateFlow<Boolean> = dataStore.autoHistoryEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val manualCheckpointsEnabled: StateFlow<Boolean> = dataStore.manualCheckpointsEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val autoHistorySlots: StateFlow<Int> = dataStore.autoHistorySlotsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 10)

    val manualCheckpointSlots: StateFlow<Int> = dataStore.manualCheckpointSlotsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 10)

    val autoHistoryMinWords: StateFlow<Int> = dataStore.autoHistoryMinWordsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 10)

    // ── Setters ───────────────────────────────────────────────────────────────

    fun setShowWordCount(v: Boolean)          { viewModelScope.launch { dataStore.setShowWordCount(v) } }
    fun setTypewriterMode(v: Boolean)         { viewModelScope.launch { dataStore.setTypewriterMode(v) } }
    fun setLineSpacing(v: String)             { viewModelScope.launch { dataStore.setLineSpacing(v) } }
    fun setEditorFontSize(size: Int)          { viewModelScope.launch { dataStore.setEditorFontSize(size) } }
    fun setHomeStartPage(page: String)        { viewModelScope.launch { dataStore.setHomeStartPage(page) } }
    fun setDailyGoal(goal: Int)              { viewModelScope.launch { dataStore.setDailyGoal(goal) } }
    fun setAutoHistoryEnabled(v: Boolean)     { viewModelScope.launch { dataStore.setAutoHistoryEnabled(v) } }
    fun setManualCheckpointsEnabled(v: Boolean){ viewModelScope.launch { dataStore.setManualCheckpointsEnabled(v) } }
    fun setAutoHistorySlots(v: Int)           { viewModelScope.launch { dataStore.setAutoHistorySlots(v) } }
    fun setManualCheckpointSlots(v: Int)      { viewModelScope.launch { dataStore.setManualCheckpointSlots(v) } }
    fun setAutoHistoryMinWords(v: Int)        { viewModelScope.launch { dataStore.setAutoHistoryMinWords(v) } }
}

// Computes (currentStreak, longestStreak) from a list of "yyyy-MM-dd" date strings.
// Mirrors the logic in DashboardViewModel so SettingsViewModel can work independently.
private fun computeStreaks(dates: List<String>): Pair<Int, Int> {
    if (dates.isEmpty()) return 0 to 0
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val sorted = dates.mapNotNull { format.parse(it) }.sortedDescending()
    if (sorted.isEmpty()) return 0 to 0
    val today = format.parse(format.format(Date())) ?: return 0 to 0
    val oneDayMs = 86_400_000L
    val gapFromToday = (today.time - sorted.first().time) / oneDayMs
    var current = 0
    if (gapFromToday <= 1) {
        current = 1
        for (i in 1 until sorted.size) {
            val gap = (sorted[i - 1].time - sorted[i].time) / oneDayMs
            if (gap == 1L) current++ else break
        }
    }
    var longest = 1
    var run = 1
    for (i in 1 until sorted.size) {
        val gap = (sorted[i - 1].time - sorted[i].time) / oneDayMs
        if (gap == 1L) { run++; longest = maxOf(longest, run) } else run = 1
    }
    return current to maxOf(longest, current)
}
