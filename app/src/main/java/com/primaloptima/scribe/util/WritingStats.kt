package com.primaloptima.scribe.util

import com.primaloptima.scribe.util.model.StreakData
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Manages the writing stats (daily goal, streaks, session word count).
 * Mirrors WritingStatsContext.tsx.
 */
class WritingStats(private val prefs: PrefsManager) {

    private val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    var sessionWords: Int = 0
        private set

    /** Today's persisted word count (read from prefs on first access). */
    val todayWords: Int get() = prefs.getTodayWords(todayStr())

    val dailyGoal: Int get() = prefs.dailyGoal

    val goalReached: Boolean get() = todayWords >= dailyGoal

    fun currentStreak(): Int = prefs.getStreak().currentStreak
    fun longestStreak(): Int = prefs.getStreak().longestStreak

    /**
     * Record a word delta (positive or negative).
     * Updates session count, today's total, and streak.
     */
    fun recordWordDelta(delta: Int) {
        if (delta == 0) return
        sessionWords = maxOf(0, sessionWords + delta)
        val today = todayStr()
        val newTotal = maxOf(0, todayWords + delta)
        prefs.setTodayWords(today, newTotal)
        if (delta > 0) updateStreak(today)
    }

    /** Call when the user changes their goal in Settings. */
    fun setDailyGoal(goal: Int) { prefs.dailyGoal = maxOf(50, goal) }

    /** Reconcile streak on app launch — reset if a day was skipped. */
    fun reconcileStreak() {
        val s = prefs.getStreak()
        val today = todayStr()
        val yesterday = yesterdayStr()
        if (s.lastWriteDate == today || s.lastWriteDate == yesterday || s.lastWriteDate == null) return
        // A day (or more) was missed — reset streak
        prefs.saveStreak(s.copy(currentStreak = 0))
    }

    private fun updateStreak(today: String) {
        val s = prefs.getStreak()
        if (s.lastWriteDate == today) return
        val yesterday = yesterdayStr()
        val nextCurrent = if (s.lastWriteDate == yesterday) s.currentStreak + 1 else 1
        val nextLongest = maxOf(s.longestStreak, nextCurrent)
        prefs.saveStreak(StreakData(
            currentStreak = nextCurrent,
            longestStreak = nextLongest,
            lastWriteDate = today
        ))
    }

    fun todayStr(): String = fmt.format(Date())

    private fun yesterdayStr(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        return fmt.format(cal.time)
    }
}
