package com.majidali.appcloner.ui.setup

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.majidali.appcloner.AppClonerApp
import com.majidali.appcloner.databinding.ActivitySetupBinding
import com.majidali.appcloner.ui.common.UiHelpers
import com.majidali.appcloner.ui.limits.LimitsActivity

/**
 * Setup wizard, in plain language, one step at a time.
 *
 * Step 1 — What a work profile is: a separate, private space on the
 *   same phone. Apps cloned into it keep their own data, their own
 *   login, and their own notifications. The launcher marks them with
 *   a small briefcase badge.
 * Step 2 — The organisation warning: Android will say an
 *   organisation manages the profile. That "organisation" is this
 *   app on your own phone. Nothing is sent anywhere, and no one else
 *   can see or manage the profile.
 * Step 3 — Battery: some phones kill background apps to save power.
 *   The guide opens the battery settings so the clone's notifications
 *   keep arriving. On Xiaomi phones this step matters most.
 * Step 4 — Notifications (Android 13+): the app asks once for
 *   permission to show clone notifications.
 * Step 5 — Create the profile: Android's own provisioning screen
 *   runs. Accept it and the profile is ready.
 *
 * No step is skipped silently: if a step fails, the wizard says what
 * happened and what to try next, instead of crashing or freezing.
 */
class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding

    private val app get() = application as AppClonerApp

    private val provisioningLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            binding.step5Done.visibility = View.VISIBLE
            binding.createProfileButton.isEnabled = false
            UiHelpers.toast(this, "Work profile created. You can start cloning.")
        } else {
            UiHelpers.showError(
                this,
                "Profile was not created",
                "The setup was cancelled or refused. You can try " +
                    "again — nothing was changed on your phone."
            )
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        binding.step4Done.visibility = if (granted) View.VISIBLE else View.GONE
        if (!granted) {
            UiHelpers.toast(
                this,
                "No problem — clones will still work, but their " +
                    "notifications may stay silent."
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Step 3 — battery guide.
        binding.batteryButton.setOnClickListener {
            try {
                startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                )
                binding.step3Done.visibility = View.VISIBLE
            } catch (e: Exception) {
                UiHelpers.showError(
                    this,
                    "Could not open settings",
                    "Open Settings yourself, then Battery, and allow " +
                        "App Cloner to run in the background."
                )
            }
        }

        // Step 4 — notifications (Android 13 and newer only).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            binding.step4Row.visibility = View.VISIBLE
            binding.notificationButton.setOnClickListener {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            refreshNotificationStep()
        } else {
            binding.step4Row.visibility = View.GONE
        }

        // Step 5 — create the profile.
        binding.createProfileButton.setOnClickListener {
            val intent = app.container.profileManager.provisioningIntent()
            provisioningLauncher.launch(intent)
        }

        binding.limitsLink.setOnClickListener {
            startActivity(Intent(this, LimitsActivity::class.java))
        }

        refreshProfileStep()
    }

    override fun onResume() {
        super.onResume()
        refreshProfileStep()
        refreshNotificationStep()
    }

    private fun refreshProfileStep() {
        val ready = app.container.profileManager.isProfileReady()
        binding.step5Done.visibility = if (ready) View.VISIBLE else View.GONE
        binding.createProfileButton.isEnabled = !ready
        if (ready) {
            binding.createProfileButton.text = "Work profile is ready"
        }
    }

    private fun refreshNotificationStep() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        binding.step4Done.visibility = if (granted) View.VISIBLE else View.GONE
        binding.notificationButton.isEnabled = !granted
    }
}
