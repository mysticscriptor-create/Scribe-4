package com.primaloptima.scribe.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.primaloptima.scribe.ScribeApp
import com.primaloptima.scribe.data.DailyWordRow
import com.primaloptima.scribe.ui.screens.ChartRange
import com.primaloptima.scribe.ui.screens.DailyWordEntry
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class StatsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = (application as ScribeApp).database

    private val _chartData = MutableStateFlow<List<DailyWordEntry>>(emptyList())
    val chartData: StateFlow<List<DailyWordEntry>> = _chartData

    val folderWordTotals: StateFlow<Map<String, Int>> =
        db.noteDao().observeWordCountPerFolder()
            .map { rows -> rows.associate { "${it.bookId}|${it.folderPath}" to it.total } }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    suspend fun loadChartData(range: ChartRange) {
        val keyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dayFormat = SimpleDateFormat("EEE", Locale.US)
        val fullFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
        val today = keyFormat.format(Date())

        _chartData.value = when (range) {
            ChartRange.WEEK, ChartRange.TWO_WEEKS, ChartRange.MONTH -> {
                val start = keyFormat.format(
                    Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, -(range.days - 1))
                    }.time
                )
                val rows = withContext(Dispatchers.IO) {
                    db.writingLogDao().getWordsByDateRange(start, today)
                }
                rows.toDailyEntries(range.days, keyFormat, dayFormat, fullFormat)
            }
            ChartRange.YEAR -> {
                val monthKeyFormat = SimpleDateFormat("yyyy-MM", Locale.US)
                val monthLabelFormat = SimpleDateFormat("MMM", Locale.US)
                val monthFullFormat = SimpleDateFormat("MMMM yyyy", Locale.US)
                val start = keyFormat.format(
                    Calendar.getInstance().apply { add(Calendar.MONTH, -11) }.time
                )
                val rows = withContext(Dispatchers.IO) {
                    db.writingLogDao().getWordsByMonth(start)
                }
                val byMonth = rows.associate { it.month to it.total }
                (11 downTo 0).map { monthsAgo ->
                    val date = Calendar.getInstance().apply {
                        add(Calendar.MONTH, -monthsAgo)
                    }.time
                    DailyWordEntry(
                        label = monthLabelFormat.format(date),
                        fullDateStr = monthFullFormat.format(date),
                        wordCount = byMonth[monthKeyFormat.format(date)] ?: 0,
                        timestamp = date.time
                    )
                }
            }
        }
    }

    suspend fun getFolderWordTotals(): Map<String, Int> = folderWordTotals.value
}

private fun List<DailyWordRow>.toDailyEntries(
    days: Int,
    keyFormat: SimpleDateFormat,
    dayFormat: SimpleDateFormat,
    fullFormat: SimpleDateFormat
): List<DailyWordEntry> {
    val byDate = associate { it.date to it.total }
    return (days - 1 downTo 0).map { daysAgo ->
        val date = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -daysAgo)
        }.time
        DailyWordEntry(
            label = dayFormat.format(date),
            fullDateStr = fullFormat.format(date),
            wordCount = byDate[keyFormat.format(date)] ?: 0,
            timestamp = date.time
        )
    }
}