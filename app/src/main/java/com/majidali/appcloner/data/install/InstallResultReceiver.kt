package com.majidali.appcloner.data.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.majidali.appcloner.data.profile.ProfileManager

/**
 * Receives the result of a PackageInstaller session (install or
 * uninstall) and reports it back to the personal-side app.
 *
 * The receiver runs in whichever user started the session — for a
 * clone, that is the work profile. It answers across the border with
 * a [ProfileManager.ACTION_PROFILE_RESULT] intent, so the main screen
 * in the personal profile can update the clone list. The receiver
 * itself never touches the clone list: the list lives in one place
 * only (the personal user's private storage), and the profile-side
 * copy only reports what happened.
 *
 * Status meanings come from PackageInstaller: 0 is success, anything
 * else carries a short message in EXTRA_STATUS_MESSAGE.
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val operation = intent.getStringExtra(ProfileManager.EXTRA_OPERATION) ?: return
        val packageName = intent.getStringExtra(ProfileManager.EXTRA_PACKAGE) ?: return
        val label = intent.getStringExtra(ProfileManager.EXTRA_LABEL) ?: packageName

        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
        val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        val success = status == PackageInstaller.STATUS_SUCCESS
        val message = when {
            success && operation == ProfileManager.OP_CLONE ->
                "Clone created: $label"
            success && operation == ProfileManager.OP_REMOVE ->
                "Clone removed."
            success -> "Done."
            else -> installFailureMessage(packageName, status, detail)
        }

        val profileManager = ProfileManager(context)
        profileManager.sendProfileIntent(
            profileManager.profileResultIntent(
                operation = operation,
                packageName = packageName,
                success = success,
                message = message
            ).apply {
                // The main screen needs the label to store the clone.
                putExtra(ProfileManager.EXTRA_LABEL, label)
            }
        )
    }

    /**
     * Plain-words failure message. The status code is kept for the
     * technically curious, but the sentence is what matters.
     */
    private fun installFailureMessage(
        packageName: String,
        status: Int,
        detail: String?
    ): String {
        val hint = when (status) {
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                "This app cannot run as a second copy on this phone."
            PackageInstaller.STATUS_FAILURE_STORAGE ->
                "There is not enough free space on the phone."
            PackageInstaller.STATUS_FAILURE_ABORTED ->
                "The install was cancelled."
            else -> "Android refused the install."
        }
        val extra = if (!detail.isNullOrBlank()) " ($detail)" else ""
        return "$hint Package: $packageName, code $status$extra"
    }
}
