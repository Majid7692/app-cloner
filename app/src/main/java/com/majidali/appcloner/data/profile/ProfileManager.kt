package com.majidali.appcloner.data.profile

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import com.majidali.appcloner.admin.CloneDeviceAdminReceiver

/**
 * Owns the managed work profile: creating it, checking it, turning
 * it on and off, and talking to the copy of App Cloner inside it.
 *
 * The profile is created the official way, with the system
 * provisioning screen ([DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE]).
 * No root, no ADB and no hidden API is needed to create it. Android
 * itself shows the "your organisation will manage this profile"
 * warning; the setup wizard explains in plain words that the
 * "organisation" is the user's own phone and nothing is sent
 * anywhere.
 *
 * Talking across the profile border: the app runs in both users, and
 * the two copies pass requests with ordinary intents. Android only
 * lets such an intent cross the border when the profile owner has
 * registered a cross-profile intent filter for it
 * ([DevicePolicyManager.addCrossProfileIntentFilter]). The filters
 * are registered once, right after provisioning. One action carries
 * every request ([ACTION_PROFILE_REQUEST], with an "op" extra:
 * clone / remove / freeze) and one action carries every result
 * ([ACTION_PROFILE_RESULT]).
 *
 * Turning the profile off hides every clone at once (the launcher
 * badges disappear and frozen apps stop). Turning it back on
 * restores everything exactly as it was — clones lose no data.
 * This uses the public quiet-mode API on UserManager.
 */
class ProfileManager(private val context: Context) {

    private val devicePolicyManager: DevicePolicyManager =
        context.getSystemService(DevicePolicyManager::class.java)
    private val userManager: UserManager =
        context.getSystemService(UserManager::class.java)
    private val packageManager: PackageManager = context.packageManager

    val adminComponent: ComponentName =
        ComponentName(context, CloneDeviceAdminReceiver::class.java)

    /** True when a managed profile exists and this app owns it. */
    fun isProfileReady(): Boolean {
        return try {
            isProfileOwner() && getProfileUserHandle() != null
        } catch (e: Exception) {
            false
        }
    }

    /** True when this app is the profile owner of a managed profile. */
    fun isProfileOwner(): Boolean {
        return try {
            devicePolicyManager.isProfileOwnerApp(context.packageName)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * The user handle of the managed profile this app owns, or null.
     *
     * Only a profile this app owns counts. Without the ownership
     * check, the first other user on the phone is picked up instead
     * — on Xiaomi phones that is the Dual Apps user (user 999), so
     * the main screen once showed the profile switch ON while the
     * profile was "not set up", and switching acted on a profile
     * this app does not own. (The public SDK has no per-handle
     * owner lookup, so ownership of any managed profile is the
     * check; when this app owns one, the first other user is it —
     * work profiles take the lowest free user id, below Dual Apps
     * and Private Space.)
     */
    fun getProfileUserHandle(): UserHandle? {
        return try {
            if (!isProfileOwner()) return null
            val myHandle = Process.myUserHandle()
            userManager.userProfiles.firstOrNull { it != myHandle }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Numeric user id of the profile, for ADB commands. -1 if unknown.
     *
     * The public SDK has no UserHandle.getIdentifier(), so the id is
     * read from the handle's own text form ("UserHandle{10}").
     * The profile-side copy can also compute it directly as
     * Process.myUid() / 100000 (Android UID layout: user id * 100000
     * + app id), and the two agree.
     */
    fun getProfileUserId(): Int {
        val handle = getProfileUserHandle() ?: return -1
        return userIdFromHandle(handle)
    }

    /** Numeric user id for a handle, parsed from its text form. */
    fun userIdFromHandle(handle: UserHandle): Int {
        val digits = handle.toString().filter { it.isDigit() }
        return digits.toIntOrNull() ?: -1
    }

    /** This copy's own numeric user id (0 for the personal side). */
    fun myUserId(): Int = Process.myUid() / 100000

    /** True when this copy runs inside the managed profile. */
    fun isRunningInsideProfile(): Boolean {
        return try {
            userManager.isManagedProfile
        } catch (e: Exception) {
            false
        }
    }

    /** The intent that starts system provisioning. */
    fun provisioningIntent(): Intent {
        return Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE).apply {
            putExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                adminComponent
            )
            // Keep the phone's own apps available inside the profile.
            putExtra(DevicePolicyManager.EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED, true)
        }
    }

    /**
     * Register the cross-profile intent filters, both directions.
     * Called once after provisioning. The parent may reach the
     * profile's request handler; the profile may answer the parent.
     * Clearing first keeps the call idempotent.
     */
    fun registerCrossProfileFilters() {
        try {
            devicePolicyManager.clearCrossProfileIntentFilters(adminComponent)
            val requestFilter = IntentFilter(ACTION_PROFILE_REQUEST)
            devicePolicyManager.addCrossProfileIntentFilter(
                adminComponent,
                requestFilter,
                DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED
            )
            val resultFilter = IntentFilter(ACTION_PROFILE_RESULT)
            devicePolicyManager.addCrossProfileIntentFilter(
                adminComponent,
                resultFilter,
                DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT
            )
        } catch (e: Exception) {
            // Filters are needed for cloning; a failure here surfaces
            // as a clear "could not reach the profile" message when
            // the user tries to clone, never as a crash.
        }
    }

    /**
     * Send one request across the border (parent -> profile, or the
     * other way for results). Returns false when there is no profile
     * to send to.
     */
    fun sendProfileIntent(intent: Intent): Boolean {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Request intent: clone / remove / freeze one package. */
    fun profileRequestIntent(
        operation: String,
        packageName: String,
        label: String = packageName,
        apkPaths: List<String> = emptyList(),
        frozen: Boolean = false
    ): Intent {
        return Intent(ACTION_PROFILE_REQUEST).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_OPERATION, operation)
            putExtra(EXTRA_PACKAGE, packageName)
            putExtra(EXTRA_LABEL, label)
            putExtra(EXTRA_FROZEN, frozen)
            if (apkPaths.isNotEmpty()) {
                putExtra(EXTRA_APK_PATHS, apkPaths.toTypedArray())
            }
        }
    }

    /** Result intent: the outcome of one request. */
    fun profileResultIntent(
        operation: String,
        packageName: String,
        success: Boolean,
        message: String
    ): Intent {
        return Intent(ACTION_PROFILE_RESULT).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_OPERATION, operation)
            putExtra(EXTRA_PACKAGE, packageName)
            putExtra(EXTRA_SUCCESS, success)
            putExtra(EXTRA_MESSAGE, message)
        }
    }

    /**
     * Switch the whole profile off (quiet: all clones hidden and
     * stopped) or back on. Returns false when the app is not the
     * profile owner — never act on a profile this app does not own.
     */
    fun setProfileEnabled(enabled: Boolean): Boolean {
        if (!isProfileReady()) return false
        return try {
            val handle = getProfileUserHandle() ?: return false
            userManager.requestQuietModeEnabled(!enabled, handle)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * True when the profile is currently awake (not quiet).
     * False when there is no profile this app owns — an awake
     * answer for a profile that does not exist is what once showed
     * the switch ON next to "not set up".
     */
    fun isProfileAwake(): Boolean {
        if (!isProfileReady()) return false
        return try {
            val handle = getProfileUserHandle() ?: return false
            !userManager.isQuietModeEnabled(handle)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * The briefcase badge Android draws on profile apps. When no work
     * profile exists yet the drawable is returned unchanged — the
     * caller then shows the plain icon instead of a badge.
     */
    fun badgedIcon(drawable: Drawable): Drawable {
        val handle = getProfileUserHandle() ?: return drawable
        return packageManager.getUserBadgedIcon(drawable, handle)
    }

    /** The label Android shows for a profile app (name + badge). */
    fun badgedLabel(label: CharSequence): CharSequence {
        val handle = getProfileUserHandle() ?: return label
        return packageManager.getUserBadgedLabel(label, handle)
    }

    companion object {
        /** Cross-profile request, parent -> profile. */
        const val ACTION_PROFILE_REQUEST: String =
            "com.majidali.appcloner.PROFILE_REQUEST"

        /** Cross-profile result, profile -> parent. */
        const val ACTION_PROFILE_RESULT: String =
            "com.majidali.appcloner.PROFILE_RESULT"

        const val EXTRA_OPERATION: String = "op"
        const val EXTRA_PACKAGE: String = "package"
        const val EXTRA_LABEL: String = "label"
        const val EXTRA_APK_PATHS: String = "apk_paths"
        const val EXTRA_FROZEN: String = "frozen"
        const val EXTRA_SUCCESS: String = "success"
        const val EXTRA_MESSAGE: String = "message"
        const val EXTRA_ICON_BYTES: String = "icon_bytes"

        const val OP_CLONE: String = "clone"
        const val OP_REMOVE: String = "remove"
        const val OP_FREEZE: String = "freeze"
        const val OP_SHORTCUT: String = "shortcut"
    }
}
