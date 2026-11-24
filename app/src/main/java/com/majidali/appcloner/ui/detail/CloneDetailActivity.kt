package com.majidali.appcloner.ui.detail

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.majidali.appcloner.AppClonerApp
import com.majidali.appcloner.R
import com.majidali.appcloner.data.profile.ProfileManager
import com.majidali.appcloner.databinding.ActivityCloneDetailBinding
import com.majidali.appcloner.domain.model.AppInfo
import com.majidali.appcloner.domain.model.CloneInfo
import com.majidali.appcloner.domain.model.CloneResult
import com.majidali.appcloner.ui.common.UiHelpers
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One clone: rename it, pick a new icon, add its home screen
 * shortcut, freeze or unfreeze it, re-clone it, or remove it.
 *
 * Rename and icon change the home screen shortcut only — the app
 * itself inside the work profile keeps its own name and icon.
 * The custom icon is stored as a small PNG in private storage and
 * its bytes travel with the shortcut request, because the profile
 * side cannot read the personal side's private files.
 */
class CloneDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCloneDetailBinding
    private val app get() = application as AppClonerApp
    private var clone: CloneInfo? = null

    private val pickIconLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        saveCustomIcon(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCloneDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val packageName = intent.getStringExtra(EXTRA_PACKAGE) ?: run {
            finish()
            return
        }
        clone = app.container.cloneRepository.findClone(packageName)
        val current = clone ?: run {
            UiHelpers.toast(this, "That clone is gone.")
            finish()
            return
        }
        binding.cloneNameInput.setText(current.customName)
        binding.clonePackageText.text = current.packageName
        binding.clonedOnText.text = getString(
            R.string.detail_cloned_on,
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(Date(current.createdAtEpochMillis))
        )
        binding.frozenText.visibility =
            if (current.isFrozen) View.VISIBLE else View.GONE

        binding.saveNameButton.setOnClickListener {
            val name = binding.cloneNameInput.text.toString().trim()
            if (name.isEmpty()) {
                UiHelpers.toast(this, "Give the clone a name first.")
                return@setOnClickListener
            }
            val updated = current.copy(customName = name)
            app.container.cloneRepository.updateClone(updated)
            clone = updated
            UiHelpers.toast(this, "Name saved. The home screen shortcut uses the new name.")
        }

        binding.pickIconButton.setOnClickListener {
            pickIconLauncher.launch("image/*")
        }

        binding.shortcutButton.setOnClickListener {
            val latest = clone ?: return@setOnClickListener
            val iconBytes = readIconBytes(latest.customIconFileName)
            val sent = app.container.profileManager.sendProfileIntent(
                app.container.profileManager.profileRequestIntent(
                    operation = ProfileManager.OP_SHORTCUT,
                    packageName = latest.packageName,
                    label = latest.customName
                ).apply {
                    if (iconBytes != null) {
                        putExtra(ProfileManager.EXTRA_ICON_BYTES, iconBytes)
                    }
                }
            )
            UiHelpers.toast(
                this,
                if (sent) "Shortcut request sent — check your home screen."
                else "The shortcut could not be sent. Check the work profile."
            )
        }

        binding.freezeButton.setOnClickListener {
            val latest = clone ?: return@setOnClickListener
            val frozen = !latest.isFrozen
            val sent = app.container.cloneEngine.setFrozen(latest, frozen)
            UiHelpers.toast(
                this,
                if (sent) {
                    if (frozen) "Freezing…" else "Unfreezing…"
                } else {
                    "Could not reach the work profile."
                }
            )
            finish()
        }

        binding.recloneButton.setOnClickListener {
            val latest = clone ?: return@setOnClickListener
            // Re-install the same app into the profile — the fix
            // when a clone stops opening after an update, or was
            // removed from the profile outside this app. The list
            // entry stays (same package), and the main screen
            // updates it when the profile answers. No new slot is
            // used: the clone already occupies one.
            UiHelpers.toast(this, getString(R.string.picker_installing, latest.customName))
            Thread {
                val result = app.container.cloneEngine.cloneApp(
                    AppInfo(
                        packageName = latest.packageName,
                        label = latest.customName,
                        isSystemApp = false,
                        hasSplits = false
                    )
                )
                runOnUiThread {
                    when (result) {
                        is CloneResult.Success -> finish()
                        is CloneResult.Failure -> {
                            val command = result.adbCommand
                            if (command != null) {
                                UiHelpers.showError(
                                    this,
                                    "Re-clone failed",
                                    result.message + "\n\nCommand:\n$command",
                                    "Copy command"
                                ) { copyToClipboard(command) }
                            } else {
                                UiHelpers.showError(this, "Re-clone failed", result.message)
                            }
                        }
                    }
                }
            }.start()
        }

        binding.removeButton.setOnClickListener {
            val latest = clone ?: return@setOnClickListener
            UiHelpers.askYesNo(
                this,
                "Remove clone?",
                "This removes ${latest.customName} from the work " +
                    "profile, with all its data. The original app " +
                    "stays untouched."
            ) {
                val sent = app.container.cloneEngine.removeClone(latest)
                UiHelpers.toast(
                    this,
                    if (sent) "Removal started…" else "Could not reach the work profile."
                )
                finish()
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(android.content.ClipboardManager::class.java)
        clipboard?.setPrimaryClip(
            android.content.ClipData.newPlainText("ADB command", text)
        )
        UiHelpers.toast(this, "Command copied.")
    }

    /**
     * Downscale the picked picture to 192x192 and store it as a PNG
     * in private storage. Small on purpose: the bytes travel with
     * the shortcut request, and Android intents have a size limit.
     *
     * The picture is downsampled while it is decoded, not after: a
     * full camera photo decoded at full size first can run the app
     * out of memory on a low-end phone. The sample size is chosen
     * from the picture's real size, so the decoded bitmap is never
     * much bigger than the 192 pixels it ends up as.
     */
    private fun saveCustomIcon(uri: Uri) {
        val current = clone ?: return
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            }
            val largestSide = maxOf(bounds.outWidth, bounds.outHeight)
            var sampleSize = 1
            while (largestSide / sampleSize > 192) sampleSize *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: run {
                UiHelpers.toast(this, "That picture could not be read.")
                return
            }
            val scaled = Bitmap.createScaledBitmap(bitmap, 192, 192, true)
            val iconsDir = File(filesDir, "icons").apply { mkdirs() }
            val fileName = current.packageName.replace('.', '_') + ".png"
            FileOutputStream(File(iconsDir, fileName)).use { out ->
                scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val updated = current.copy(customIconFileName = fileName)
            app.container.cloneRepository.updateClone(updated)
            clone = updated
            UiHelpers.toast(this, "Icon saved. Add the shortcut again to see it.")
        } catch (e: Exception) {
            UiHelpers.showError(
                this,
                "Could not save the icon",
                "Try a smaller picture, or a photo from your gallery."
            )
        }
    }

    private fun readIconBytes(fileName: String?): ByteArray? {
        if (fileName.isNullOrBlank()) return null
        return try {
            File(File(filesDir, "icons"), fileName).readBytes()
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val EXTRA_PACKAGE: String = "package"
    }
}
