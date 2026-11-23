package com.majidali.appcloner.ui.main

import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.majidali.appcloner.AppClonerApp
import com.majidali.appcloner.R
import com.majidali.appcloner.data.profile.ProfileManager
import com.majidali.appcloner.databinding.ActivityMainBinding
import com.majidali.appcloner.domain.model.CloneInfo
import com.majidali.appcloner.domain.model.CloneLimits
import com.majidali.appcloner.ui.common.UiHelpers
import com.majidali.appcloner.ui.detail.CloneDetailActivity
import com.majidali.appcloner.ui.limits.LimitsActivity
import com.majidali.appcloner.ui.picker.AppPickerActivity
import com.majidali.appcloner.ui.setup.SetupActivity

/**
 * Main screen: the list of clones, the profile switch, and the
 * actions (add, back up, share a file).
 *
 * This activity runs in BOTH users (personal and work profile), and
 * it is the meeting point for cross-profile messages:
 * - A PROFILE_REQUEST received here runs only when this copy lives
 *   inside the work profile. It carries out the clone, remove or
 *   freeze and answers with a PROFILE_RESULT.
 * - A PROFILE_RESULT received here runs only on the personal side.
 *   It updates the clone list — the one and only copy of the list,
 *   stored in the personal user's private storage.
 *
 * Keeping the list in one place matters: the two copies of the app
 * have separate private storage, so a list written by the profile
 * side would never be seen by the main screen.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: CloneListAdapter

    private val app get() = application as AppClonerApp

    private val exportBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val ok = app.container.backupManager.exportTo(uri)
        UiHelpers.toast(
            this,
            if (ok) "Backup saved." else "Backup could not be saved. Try again."
        )
    }

    private val importBackupLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val restored = app.container.backupManager.importFrom(uri)
        if (restored >= 0) {
            UiHelpers.toast(this, "Backup restored: $restored clone(s).")
            viewModel.refresh()
        } else {
            UiHelpers.showError(
                this,
                "Not a backup",
                "That file is not an App Cloner backup. Pick the " +
                    "file you saved from Backup in this app."
            )
        }
    }

    private val pickFileToShareLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "shared-file"
        val staged = app.container.fileShuttle.stageForSharing(uri, fileName)
        if (staged == null) {
            UiHelpers.showError(
                this,
                "Could not share",
                "That file could not be prepared for sharing. Try " +
                    "a photo or a document from your files."
            )
            return@registerForActivityResult
        }
        val shareIntent = app.container.fileShuttle.shareIntent(staged, "*/*")
        startActivity(Intent.createChooser(shareIntent, "Share file"))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MainViewModel(app.container) as T
        })[MainViewModel::class.java]

        adapter = CloneListAdapter(
            onOpen = { clone -> openClone(clone) },
            onFreezeToggle = { clone ->
                val frozen = !clone.isFrozen
                val sent = viewModel.setFrozen(clone, frozen)
                if (!sent) {
                    UiHelpers.showError(
                        this,
                        "Could not freeze",
                        "Freezing works only when the work profile is " +
                            "set up and this app owns it. Open Setup."
                    )
                } else {
                    UiHelpers.toast(
                        this,
                        if (frozen) "Freezing ${clone.customName}…" else "Unfreezing ${clone.customName}…"
                    )
                }
            },
            onDetails = { clone -> openCloneDetails(clone) }
        )
        binding.cloneList.adapter = adapter

        binding.addCloneButton.setOnClickListener { addClone() }
        binding.setupButton.setOnClickListener {
            startActivity(Intent(this, SetupActivity::class.java))
        }
        binding.profileSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!viewModel.setProfileEnabled(isChecked)) {
                UiHelpers.toast(this, "The profile could not be switched. Open Setup.")
                viewModel.refresh()
            }
        }
        binding.shareFileButton.setOnClickListener {
            pickFileToShareLauncher.launch("*/*")
        }

        viewModel.clones.observe(this) { clones ->
            adapter.submitList(clones)
            binding.emptyState.visibility =
                if (clones.isEmpty()) View.VISIBLE else View.GONE
            binding.slotsText.text =
                "Clones: ${clones.size} of ${CloneLimits.MAX_CLONES_V1}"
            binding.addCloneButton.isEnabled = clones.size < CloneLimits.MAX_CLONES_V1
        }
        viewModel.profileReady.observe(this) { ready ->
            binding.profileStatus.text =
                if (ready) "Work profile: ready" else "Work profile: not set up"
            binding.setupButton.visibility =
                if (ready) View.GONE else View.VISIBLE
        }
        viewModel.profileAwake.observe(this) { awake ->
            binding.profileSwitch.isChecked = awake
            binding.profileSwitch.text =
                if (awake) "Work profile is ON" else "Work profile is OFF"
        }

        // Cross-profile messages arrive here.
        handleProfileIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleProfileIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_limits -> {
                startActivity(Intent(this, LimitsActivity::class.java))
                true
            }
            R.id.action_backup -> {
                exportBackupLauncher.launch(app.container.backupManager.backupFileName())
                true
            }
            R.id.action_restore -> {
                importBackupLauncher.launch(arrayOf("application/json"))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun addClone() {
        if (viewModel.profileReady.value != true) {
            UiHelpers.showError(
                this,
                "Set up first",
                "Create the work profile first. It takes about two " +
                    "minutes — open Setup."
            )
            return
        }
        startActivity(Intent(this, AppPickerActivity::class.java))
    }

    /**
     * Open a clone. Clones live in the work profile, so they are
     * launched through LauncherApps with the profile's user handle —
     * a plain launch intent would open the personal copy instead.
     */
    private fun openClone(clone: CloneInfo) {
        try {
            val profileHandle = app.container.profileManager.getProfileUserHandle()
            if (profileHandle == null) {
                UiHelpers.toast(this, "The work profile is not set up.")
                return
            }
            val launcherApps = getSystemService(LauncherApps::class.java)
            val activities = launcherApps.getActivityList(clone.packageName, profileHandle)
            val component = activities.firstOrNull()?.componentName
            if (component == null) {
                UiHelpers.showError(
                    this,
                    "Clone is not ready",
                    "The clone is not installed in the work profile " +
                        "yet. Open the clone and tap Re-clone."
                )
                return
            }
            launcherApps.startMainActivity(component, profileHandle, null, null)
        } catch (e: SecurityException) {
            UiHelpers.showError(
                this,
                "Could not open",
                "Android refused to open the clone. Check that the " +
                    "work profile is switched on."
            )
        }
    }

    private fun openCloneDetails(clone: CloneInfo) {
        val intent = Intent(this, CloneDetailActivity::class.java).apply {
            putExtra(CloneDetailActivity.EXTRA_PACKAGE, clone.packageName)
        }
        startActivity(intent)
    }

    /**
     * Cross-profile messages. Requests run only inside the profile;
     * results run only on the personal side. Anything else is
     * ignored, so a stray intent can never change the clone list.
     */
    private fun handleProfileIntent(intent: Intent?) {
        if (intent == null) return
        val profileManager = app.container.profileManager
        when (intent.action) {
            ProfileManager.ACTION_PROFILE_REQUEST -> {
                if (!profileManager.isRunningInsideProfile()) return
                handleProfileRequest(intent)
            }
            ProfileManager.ACTION_PROFILE_RESULT -> {
                if (profileManager.isRunningInsideProfile()) return
                handleProfileResult(intent)
            }
        }
    }

    /** Profile side: carry out one request, then answer back. */
    private fun handleProfileRequest(intent: Intent) {
        val engine = app.container.cloneEngine
        val profileManager = app.container.profileManager
        val operation = intent.getStringExtra(ProfileManager.EXTRA_OPERATION) ?: return
        val packageName = intent.getStringExtra(ProfileManager.EXTRA_PACKAGE) ?: return
        val label = intent.getStringExtra(ProfileManager.EXTRA_LABEL) ?: packageName

        when (operation) {
            ProfileManager.OP_CLONE -> {
                val apkPaths = intent.getStringArrayExtra(ProfileManager.EXTRA_APK_PATHS)
                    ?.toList() ?: emptyList()
                val started = engine.installFromApkFiles(packageName, label, apkPaths)
                if (!started) {
                    // The install never started: answer with a
                    // failure right away, so the main screen is not
                    // left waiting.
                    profileManager.sendProfileIntent(
                        profileManager.profileResultIntent(
                            operation = ProfileManager.OP_CLONE,
                            packageName = packageName,
                            success = false,
                            message = "The install could not be started. " +
                                "Check the work profile and try again."
                        )
                    )
                }
                // On success the answer comes later, from
                // InstallResultReceiver, when Android reports the
                // install result.
            }
            ProfileManager.OP_REMOVE -> {
                val started = engine.uninstallFromProfile(packageName)
                if (!started) {
                    profileManager.sendProfileIntent(
                        profileManager.profileResultIntent(
                            operation = ProfileManager.OP_REMOVE,
                            packageName = packageName,
                            success = false,
                            message = "The clone could not be removed."
                        )
                    )
                }
            }
            ProfileManager.OP_FREEZE -> {
                val frozen = intent.getBooleanExtra(ProfileManager.EXTRA_FROZEN, false)
                val ok = engine.applyFrozen(packageName, frozen)
                profileManager.sendProfileIntent(
                    profileManager.profileResultIntent(
                        operation = ProfileManager.OP_FREEZE,
                        packageName = packageName,
                        success = ok,
                        message = if (ok) {
                            if (frozen) "Clone frozen." else "Clone unfrozen."
                        } else {
                            "Freezing failed. Check the work profile."
                        }
                    ).apply {
                        putExtra(ProfileManager.EXTRA_FROZEN, frozen)
                    }
                )
            }
            ProfileManager.OP_SHORTCUT -> {
                val iconBytes = intent.getByteArrayExtra(ProfileManager.EXTRA_ICON_BYTES)
                val ok = com.majidali.appcloner.data.profile.ShortcutHelper(this)
                    .createShortcut(packageName, label, iconBytes)
                profileManager.sendProfileIntent(
                    profileManager.profileResultIntent(
                        operation = ProfileManager.OP_SHORTCUT,
                        packageName = packageName,
                        success = ok,
                        message = if (ok) {
                            "Shortcut added to your home screen."
                        } else {
                            "The shortcut could not be added. Open the " +
                                "clone once, then try again."
                        }
                    )
                )
            }
        }
        finish()
    }

    /** Personal side: apply one result to the clone list. */
    private fun handleProfileResult(intent: Intent) {
        val operation = intent.getStringExtra(ProfileManager.EXTRA_OPERATION) ?: return
        val packageName = intent.getStringExtra(ProfileManager.EXTRA_PACKAGE) ?: return
        val success = intent.getBooleanExtra(ProfileManager.EXTRA_SUCCESS, false)
        val message = intent.getStringExtra(ProfileManager.EXTRA_MESSAGE)
            ?: if (success) "Done." else "Something went wrong."

        when (operation) {
            ProfileManager.OP_CLONE -> {
                if (success) {
                    val label = intent.getStringExtra(ProfileManager.EXTRA_LABEL)
                        ?: packageName
                    app.container.cloneRepository.addClone(
                        CloneInfo(packageName = packageName, customName = label)
                    )
                    viewModel.refresh()
                }
                UiHelpers.toast(this, message)
            }
            ProfileManager.OP_REMOVE -> {
                if (success) {
                    app.container.cloneRepository.removeClone(packageName)
                    viewModel.refresh()
                }
                UiHelpers.toast(this, message)
            }
            ProfileManager.OP_FREEZE -> {
                if (success) {
                    val frozen = intent.getBooleanExtra(ProfileManager.EXTRA_FROZEN, false)
                    val clone = app.container.cloneRepository.findClone(packageName)
                    if (clone != null) {
                        app.container.cloneRepository.updateClone(
                            clone.copy(isFrozen = frozen)
                        )
                        viewModel.refresh()
                    }
                }
                UiHelpers.toast(this, message)
            }
            ProfileManager.OP_SHORTCUT -> {
                UiHelpers.toast(this, message)
            }
        }
    }
}
