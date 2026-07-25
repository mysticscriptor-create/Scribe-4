package com.primaloptima.scribe

import android.app.Application
import android.content.Intent
import androidx.appcompat.app.AppCompatDelegate
import com.primaloptima.scribe.data.AppDatabase
import com.primaloptima.scribe.util.PrefsManager
import com.primaloptima.scribe.util.ThemeManager
import java.io.PrintWriter
import java.io.StringWriter

class ScribeApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val prefs: PrefsManager by lazy { PrefsManager(this) }
    val themeManager: ThemeManager by lazy { ThemeManager(this) }

    override fun onCreate() {
        // Disable AppCompat's DayNight auto-switching.  Scribe manages its own
        // theming entirely in code (ThemeManager / applyTheme); letting AppCompat
        // also try to switch between day/night resources causes an infinite
        // activity-recreation loop on devices that have system dark mode enabled.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        // Install the crash handler FIRST — before any other init — so we
        // catch failures that happen during lazy property initialisation.
        installCrashHandler()
        super.onCreate()
        instance = this
    }

    private fun installCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                // Convert the full stack trace to a plain string.
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val trace = buildString {
                    appendLine("=== CRASH REPORT ===")
                    appendLine("Thread : ${thread.name}")
                    appendLine("Caused by: ${throwable::class.java.name}")
                    appendLine()
                    append(sw.toString())
                }

                // Launch CrashActivity in a new task so it works even if the
                // process's activity back-stack is in an inconsistent state.
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
                // If even the crash handler fails, fall through to the system.
                defaultHandler?.uncaughtException(thread, throwable)
            }

            // Give the system a moment to start CrashActivity, then terminate.
            Thread.sleep(500)
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    companion object {
        lateinit var instance: ScribeApp
            private set
    }
}
