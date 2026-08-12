package com.primaloptima.scribe.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.primaloptima.scribe.ScribeApp
import com.primaloptima.scribe.data.Book
import com.primaloptima.scribe.data.Folder
import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.ui.screens.DailyWordEntry
import com.primaloptima.scribe.util.BookGoal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ScribeApp
    private val db = app.database
    private val dataStore = app.dataStore

    private val writingDates: StateFlow<List<String>> =
        db.writingLogDao().observeWritingDates()
            .map { rows -> rows.map { it.date } }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val streakPair: StateFlow<Pair<Int, Int>> =
        writingDates
            .map(::computeWritingStreaks)
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0 to 0)

    val currentStreak: StateFlow<Int> = streakPair
        .map { it.first }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val longestStreak: StateFlow<Int> = streakPair
        .map { it.second }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val todayWords: StateFlow<Int> = run {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        db.writingLogDao().observeTodayWords(today)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    }

    val weeklyWordData: StateFlow<List<Triple<String, Int, Boolean>>> = run {
        val keyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dayFormat = SimpleDateFormat("EEE", Locale.US)
        val sevenDaysAgo = keyFormat.format(
            Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -6) }.time
        )

        db.writingLogDao().observeWeeklyWords(sevenDaysAgo)
            .map { rows ->
                val today = keyFormat.format(Date())
                val byDate = rows.associate { it.date to it.total }
                (6 downTo 0).map { daysAgo ->
                    val date = Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, -daysAgo)
                    }.time
                    val dateString = keyFormat.format(date)
                    Triple(
                        dayFormat.format(date).take(1),
                        byDate[dateString] ?: 0,
                        dateString == today
                    )
                }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    val dailyGoal: StateFlow<Int> = dataStore.dailyGoalFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 500)

    val ongoingProjectBookId: StateFlow<String?> = dataStore.ongoingProjectIdFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _currentBookGoal = kotlinx.coroutines.flow.MutableStateFlow(BookGoal())
    val currentBookGoal: StateFlow<BookGoal> = _currentBookGoal

    init {
        viewModelScope.launch {
            ongoingProjectBookId.collectLatest { bookId ->
                _currentBookGoal.value =
                    if (bookId == null) BookGoal() else dataStore.getBookGoal(bookId)
            }
        }
    }

    val ongoingProjectChapters: StateFlow<List<Note>> =
        ongoingProjectBookId
            .flatMapLatest { bookId ->
                if (bookId == null) {
                    flowOf(emptyList())
                } else {
                    db.noteDao().observeByBook(bookId)
                        .map { notes -> notes.filter { it.folderPath == "/Chapters" } }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun refreshStreaks() = Unit

    suspend fun refreshTodayWords() = Unit

    suspend fun loadWeeklyWordData() = Unit

    fun setOngoingProject(bookId: String) {
        viewModelScope.launch {
            dataStore.setOngoingProjectId(bookId)
            withContext(Dispatchers.IO) {
                db.noteDao().insertFolder(Folder(bookId = bookId, path = "/Chapters"))
            }
        }
    }

    fun clearOngoingProject() {
        viewModelScope.launch { dataStore.setOngoingProjectId(null) }
    }

    fun saveBookGoal(bookId: String, goal: BookGoal) {
        _currentBookGoal.value = goal
        viewModelScope.launch { dataStore.saveBookGoal(bookId, goal) }
    }

    fun setDailyGoal(goal: Int) {
        viewModelScope.launch { dataStore.setDailyGoal(goal) }
    }

    fun createChapter(onCreated: (Note) -> Unit) {
        val bookId = ongoingProjectBookId.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val existing = db.noteDao().getByBookFolder(bookId, "/Chapters")
            val note = Note(
                id = UUID.randomUUID().toString(),
                bookId = bookId,
                name = "Chapter ${existing.size + 1}",
                content = "",
                folderPath = "/Chapters",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            db.noteDao().insert(note)
            withContext(Dispatchers.Main) { onCreated(note) }
        }
    }
}

private fun computeWritingStreaks(dates: List<String>): Pair<Int, Int> {
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
        for (index in 1 until sorted.size) {
            val gap = (sorted[index - 1].time - sorted[index].time) / oneDayMs
            if (gap == 1L) current++ else break
        }
    }

    var longest = 1
    var run = 1
    for (index in 1 until sorted.size) {
        val gap = (sorted[index - 1].time - sorted[index].time) / oneDayMs
        if (gap == 1L) {
            run++
            longest = maxOf(longest, run)
        } else {
            run = 1
        }
    }

    return current to maxOf(longest, current)
}