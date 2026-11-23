package com.majidali.appcloner.data.crash

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.majidali.appcloner.ui.crash.CrashReportActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Catches crashes the app cannot recover from and saves the exact
 * error, so the cause can be fixed instead of guessed.
 *
 * Why this exists: v1.0.0 crashed on first open on one phone (Redmi
 * 13C, Android 14) while every check on the build machine passed —
 * libraries, layout, theme and startup code all verified. Without
 * the real error text, the next fix would be a guess. This catcher
 * writes the error to a private file and shows it on the next
 * launch, in plain text the user can share or screenshot.
 *
 * Rules it follows:
 * - No new permission, no network, no third-party crash SDK. The
 *   report never leaves the phone unless the user shares it.
 * - The handler itself must never throw: every step is guarded, and
 *   a failure falls back to Android's own crash handling.
 * - The report file is only ever written here and only ever read by
 *   the crash screen. Dismissing the screen deletes it.
 */
object CrashReporter {

    const val FILE_NAME: String = "crash-report.txt"

    private val installing = AtomicBoolean(false)
    private val handling = AtomicBoolean(false)

    /**
     * Install the handler. Call first in Application.onCreate,
     * before anything else is built, so a crash anywhere later is
     * caught. Safe to call twice: only the first call installs.
     */
    fun install(context: Context) {
        if (!installing.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (!handling.compareAndSet(false, true)) {
                // Already handling a crash (for example the crash
                // screen itself crashed): hand over to Android.
                previous?.uncaughtException(thread, throwable)
                return@setDefaultUncaughtExceptionHandler
            }
            // Save the exact error, then try to show it at once.
            // (The screen may not open — the process is dying — but
            // the file is there, and the main screen shows it next
            // launch.)
            saveReport(appContext, throwable)
            var shown = false
            try {
                val intent = Intent(appContext, CrashReportActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                appContext.startActivity(intent)
                shown = true
            } catch (_: Exception) {
                // The screen could not be opened; the file is still
                // there, and the main screen shows it next launch.
            }
            if (!shown) {
                try {
                    previous?.uncaughtException(thread, throwable)
                } catch (_: Exception) {
                }
            }
            try {
                android.os.Process.killProcess(android.os.Process.myPid())
            } catch (_: Exception) {
            }
            try {
                kotlin.system.exitProcess(10)
            } catch (_: Exception) {
            }
        }
    }

    /** True when a crash report from an earlier run is waiting. */
    fun hasSavedReport(context: Context): Boolean {
        return try {
            File(context.filesDir, FILE_NAME).exists()
        } catch (_: Exception) {
            false
        }
    }

    /** The saved report text, or null when there is none. */
    fun readSavedReport(context: Context): String? {
        return try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) null else file.readText()
        } catch (_: Exception) {
            null
        }
    }

    /** Delete the saved report, for example when the user dismisses it. */
    fun clearSavedReport(context: Context) {
        try {
            File(context.filesDir, FILE_NAME).delete()
        } catch (_: Exception) {
        }
    }

    /**
     * Save one crash report to the private report file. Never throws:
     * a failure to write must never hide the original crash. Public
     * so the Application can also record a startup failure itself
     * (see AppClonerApp): a crash while building the app's objects
     * is written here the same way, so the main screen can show it
     * on this very launch instead of dying before it starts.
     */
    fun saveReport(context: Context, throwable: Throwable) {
        try {
            writeReport(context.applicationContext, throwable)
        } catch (_: Exception) {
            // Writing must never hide the original crash.
        }
    }

    private fun writeReport(context: Context, throwable: Throwable) {
        val header = buildString {
            appendLine("App Cloner crash report")
            appendLine("App version: ${appVersion(context)}")
            appendLine("Phone: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Time: ${timestamp()}")
            appendLine()
        }
        val trace = try {
            Log.getStackTraceString(throwable)
        } catch (_: Exception) {
            throwable.toString()
        }
        File(context.filesDir, FILE_NAME).writeText(header + trace)
    }

    private fun appVersion(context: Context): String {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName} (${info.versionCode})"
        } catch (_: Exception) {
            "unknown"
        }
    }

    private fun timestamp(): String {
        return try {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        } catch (_: Exception) {
            "unknown"
        }
    }
}
