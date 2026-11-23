package com.majidali.appcloner.ui.picker

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.majidali.appcloner.AppClonerApp
import com.majidali.appcloner.databinding.ActivityAppPickerBinding
import com.majidali.appcloner.domain.model.AppInfo
import com.majidali.appcloner.domain.model.CloneLimits
import com.majidali.appcloner.domain.model.CloneResult
import com.majidali.appcloner.ui.common.UiHelpers

/**
 * Pick an installed app to clone.
 *
 * The list comes from an intent query (launchable apps only), so no
 * broad app-list permission is needed. Apps that are already cloned
 * are marked and cannot be picked twice. Tapping an app starts the
 * clone and closes the screen with a short message.
 */
class AppPickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppPickerBinding
    private val app get() = application as AppClonerApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.appList.layoutManager = LinearLayoutManager(this)
        loadApps()
    }

    private fun loadApps() {
        binding.loadingText.visibility = View.VISIBLE
        binding.appList.visibility = View.GONE
        // Listing is fast, but it touches the package manager, so it
        // runs off the main thread and posts the result back.
        Thread {
            val apps = try {
                app.container.cloneEngine.listClonableApps()
            } catch (e: Exception) {
                emptyList<AppInfo>()
            }
            val cloned = app.container.cloneRepository.listClones()
                .map { it.packageName }.toSet()
            runOnUiThread {
                binding.loadingText.visibility = View.GONE
                binding.appList.visibility = View.VISIBLE
                binding.appList.adapter = AppListAdapter(apps, cloned) { appInfo ->
                    cloneApp(appInfo)
                }
                binding.emptyText.visibility =
                    if (apps.isEmpty()) View.VISIBLE else View.GONE
            }
        }.start()
    }

    private fun cloneApp(appInfo: AppInfo) {
        if (!app.container.cloneRepository.canAddClone()) {
            UiHelpers.showError(
                this,
                "Clone limit reached",
                "V1 allows ${CloneLimits.MAX_CLONES_V1} clones. Remove " +
                    "one first to clone another app."
            )
            return
        }
        binding.loadingText.visibility = View.VISIBLE
        Thread {
            val result = app.container.cloneEngine.cloneApp(appInfo)
            runOnUiThread {
                binding.loadingText.visibility = View.GONE
                when (result) {
                    is CloneResult.Success -> {
                        app.container.cloneRepository.addClone(result.clone)
                        UiHelpers.toast(this, "Clone created: ${result.clone.customName}")
                        finish()
                    }
                    is CloneResult.Failure -> {
                        val command = result.adbCommand
                        if (command != null) {
                            UiHelpers.showError(
                                this,
                                "Clone failed",
                                result.message + "\n\nCommand:\n$command",
                                "Copy command"
                            ) { copyToClipboard(command) }
                        } else {
                            UiHelpers.showError(this, "Clone failed", result.message)
                        }
                    }
                }
            }
        }.start()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(android.content.ClipboardManager::class.java)
        clipboard?.setPrimaryClip(
            android.content.ClipData.newPlainText("ADB command", text)
        )
        UiHelpers.toast(this, "Command copied.")
    }
}
