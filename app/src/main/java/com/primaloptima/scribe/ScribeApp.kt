package com.primaloptima.scribe

import android.app.Application
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.provider.FontRequest
import androidx.core.provider.FontsContractCompat
import com.primaloptima.scribe.data.AppDatabase
import com.primaloptima.scribe.util.MarkdownUtil
import com.primaloptima.scribe.util.ScribeDataStore
import com.primaloptima.scribe.util.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.PrintWriter
import java.io.StringWriter

class ScribeApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    // Phase 2-B / 7: ScribeDataStore is the single preference layer.
    // PrefsManager has been fully retired — all callers now use dataStore.
    val dataStore: ScribeDataStore by lazy { ScribeDataStore(this) }

    val themeManager: ThemeManager by lazy { ThemeManager(this) }

    override fun onCreate() {
        // Disable AppCompat's DayNight auto-switching. Scribe manages its own
        // theming entirely in code (ThemeManager / applyTheme); letting AppCompat
        // also try to switch between day/night resources causes an infinite
        // activity-recreation loop on devices that have system dark mode enabled.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        // Install the crash handler FIRST — before any other init — so we
        // catch failures that happen during lazy property initialisation.
        installCrashHandler()
        super.onCreate()
        instance = this
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        preloadGoogleFonts()

        // Phase 1-C / 2-B: one-time word count backfill for existing notes.
        // Runs on IO; safe to call every launch — WHERE word_count = 0 guard
        // prevents overwriting notes that already have a valid word_count.
        runWordCountBackfill()

        // Phase 2-C: seed ThemeManager cache from DataStore so ThemeManager's
        // synchronous callers get accurate data on first access.
        seedThemeManagerCache()
    }

    /**
     * Backfills word_count for any note that still has 0 (i.e., notes that
     * existed before the Phase 1 migration ran, or brand-new empty notes).
     * Takes ~1 second for 100 notes; runs invisibly in the background.
     */
    private fun runWordCountBackfill() {
        @Suppress("OPT_IN_USAGE")
        GlobalScope.launch(Dispatchers.IO) {
            try {
                // Guard: only run once. If the flag is set, all notes already have
                // valid word counts from a previous launch — skip the full scan.
                if (dataStore.isWordCountBackfillDone()) return@launch

                val notes = database.noteDao().getAllIdAndContent()
                notes.forEach { n ->
                    if (n.content.isNotBlank()) {
                        val wc = MarkdownUtil.countWords(n.content)
                        database.noteDao().updateWordCount(n.id, wc)
                    }
                }
                dataStore.markWordCountBackfillDone()
            } catch (_: Exception) {
                // Backfill is best-effort — a failure just means some word counts
                // stay at 0 until the note is next edited. The flag is NOT set on
                // failure so the backfill retries on the next launch.
            }
        }
    }

    /**
     * Reads the active theme ID and custom themes JSON from DataStore and
     * hands them to ThemeManager so its synchronous getters work correctly
     * from the very first call (e.g., in ThemeViewModel.reload() at init).
     */
    private fun seedThemeManagerCache() {
        @Suppress("OPT_IN_USAGE")
        GlobalScope.launch {
            try {
                val customJson = dataStore.getCustomThemesJson()
                val activeId   = dataStore.getActiveThemeId()
                themeManager.onDataStoreReady(customJson, activeId)
            } catch (_: Exception) {
                // ThemeManager defaults to "paper" theme if DataStore read fails
            }
        }
    }

    /**
     * Kicks off background prefetch for all Google Fonts used by Scribe.
     * Without this, fonts are fetched lazily on first use, which can cause
     * a brief flash of the fallback system font on first launch.
     */
    private fun preloadGoogleFonts() {
        val fonts = listOf(
            "Playfair Display",
            "Inter",
            "Courier Prime",
            "Cormorant Garamond",
            "Caveat",
            "Lora",
            "JetBrains Mono"
        )
        fonts.forEach { fontName ->
            try {
                val request = FontRequest(
                    "com.google.android.gms.fonts",
                    "com.google.android.gms",
                    fontName,
                    R.array.com_google_android_gms_fonts_certs
                )
                FontsContractCompat.requestFont(
                    this,
                    request,
                    object : FontsContractCompat.FontRequestCallback() {
                        // No-op callbacks — we just want the prefetch side-effect
                    },
                    Handler(Looper.getMainLooper())
                )
            } catch (_: Exception) {
                // Font prefetch is best-effort; failures are silent
            }
        }
    }

    private fun installCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val trace = buildString {
                    appendLine("=== CRASH REPORT ===")
                    appendLine("Thread : ${thread.name}")
                    appendLine("Caused by: ${throwable::class.java.name}")
                    appendLine()
                    append(sw.toString())
                }

                val intent = Intent(applicationContext, CrashActivity::class.java).apply {
                    putExtra(CrashActivity.EXTRA_STACK_TRACE, trace)
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    )
                }
                applicationContext.startActivity(intent)
            } catch (_: Exception) {
                defaultHandler?.uncaughtException(thread, throwable)
            }

            Thread.sleep(500)
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    companion object {
        lateinit var instance: ScribeApp
            private set
    }
}
