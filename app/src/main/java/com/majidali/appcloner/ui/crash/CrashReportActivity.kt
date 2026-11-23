package com.majidali.appcloner.ui.crash

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.majidali.appcloner.R
import com.majidali.appcloner.data.crash.CrashReporter
import com.majidali.appcloner.ui.main.MainActivity

/**
 * Shows the crash report saved by [CrashReporter], in plain text.
 *
 * The layout uses framework views only (no Material widgets) and
 * this screen extends plain android.app.Activity — not
 * AppCompatActivity — and runs under a plain framework theme, so it
 * can open even when the crash came from an AppCompat/Material
 * library. It uses no view binding. If this screen itself fails to
 * open, it deletes the report and closes, so the user is never stuck
 * on a broken crash screen — the main screen then opens normally.
 */
class CrashReportActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_crash_report)
        } catch (_: Exception) {
            CrashReporter.clearSavedReport(this)
            finish()
            return
        }
        val report = try {
            CrashReporter.readSavedReport(this)
        } catch (_: Exception) {
            null
        }
        if (report.isNullOrBlank()) {
            finish()
            return
        }
        try {
            findViewById<TextView>(R.id.crashText).text = report
            findViewById<Button>(R.id.shareButton).setOnClickListener {
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "App Cloner crash report")
                    putExtra(Intent.EXTRA_TEXT, report)
                }
                startActivity(Intent.createChooser(share, "Share crash details"))
            }
            findViewById<Button>(R.id.dismissButton).setOnClickListener {
                CrashReporter.clearSavedReport(this)
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        } catch (_: Exception) {
            CrashReporter.clearSavedReport(this)
            finish()
        }
    }
}
