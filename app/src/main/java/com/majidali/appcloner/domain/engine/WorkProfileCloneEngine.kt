package com.majidali.appcloner.domain.engine

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import com.majidali.appcloner.admin.CloneDeviceAdminReceiver
import com.majidali.appcloner.data.profile.AdbGuide
import com.majidali.appcloner.data.profile.ProfileManager
import com.majidali.appcloner.domain.model.AppInfo
import com.majidali.appcloner.domain.model.CloneInfo
import com.majidali.appcloner.domain.model.CloneResult
import java.io.File
import java.io.FileInputStream

/**
 * Clones apps through the managed work profile (the Island / Shelter
 * pattern, implemented here from scratch with public APIs only).
 *
 * How it works, in one paragraph: Android lets one app own a managed
 * work profile. Inside that profile, apps run as real, separate
 * installs with their own data, their own login, their own
 * notifications, and the briefcase badge in the launcher. To clone an
 * app, the personal-side app reads where the app's APK files live,
 * hands those paths to the copy of App Cloner inside the profile, and
 * that copy installs them there with a PackageInstaller session.
 * Android installs the whole package — base APK plus every split —
 * so a base+splits app does not crash.
 *
 * What this engine does NOT do: it never copies APK files by hand
 * into the profile, never re-signs anything, and never touches
 * another app's data. That is why Google login, Play updates and
 * banking keep working in the clone: the clone is a genuine install
 * of the same signed package, not a repackaged copy.
 *
 * One API that is NOT used, deliberately: PackageManager's
 * installExistingPackage is a hidden API — it is not in the public
 * SDK, so calling it would not even compile, and reflecting into it
 * is blocked on modern Android. The PackageInstaller session above
 * is the public equivalent, and the ADB command
 * "pm install-existing" is kept as the documented fallback.
 *
 * Honest limits, also shown in the app and the README:
 * 1. Installing into the profile only works from inside the profile.
 *    The request crosses over with an intent; if that hand-off is
 *    unavailable, the user gets one clear ADB command instead of a
 *    silent failure.
 * 2. A few apps refuse to run as a second copy at all (WhatsApp and
 *    Google apps check for this). The Limits screen names them.
 * 3. The first clone may ask once, in the work profile, to confirm
 *    the install. That is Android's own prompt, not the app's.
 */
class WorkProfileCloneEngine(
    private val context: Context,
    private val profileManager: ProfileManager
) : CloneEngine {

    override val engineName: String = "Work profile"

    private val packageManager: PackageManager = context.packageManager
    private val adminComponent = ComponentName(context, CloneDeviceAdminReceiver::class.java)

    override fun isAvailable(): Boolean = profileManager.isProfileReady()

    override fun unavailableReason(): String? {
        if (profileManager.isProfileReady()) return null
        return "The work profile is not set up yet. Open Setup and " +
            "follow the steps — it takes about two minutes."
    }

    override fun listClonableApps(): List<AppInfo> {
        // Intent query, not QUERY_ALL_PACKAGES: Android returns the
        // launchable apps without a broad "see every app" permission.
        val launchIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val activities = packageManager.queryIntentActivities(launchIntent, 0)
        val seen = LinkedHashMap<String, AppInfo>()
        for (resolveInfo in activities) {
            val activityInfo = resolveInfo.activityInfo ?: continue
            val packageName = activityInfo.packageName
            // Never offer to clone App Cloner itself.
            if (packageName == context.packageName) continue
            if (seen.containsKey(packageName)) continue
            val appInfo = try {
                packageManager.getApplicationInfo(packageName, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                continue
            }
            val label = packageManager.getApplicationLabel(appInfo).toString()
            val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            seen[packageName] = AppInfo(
                packageName = packageName,
                label = label,
                isSystemApp = isSystem,
                hasSplits = hasSplitApks(packageName)
            )
        }
        return seen.values.sortedBy { it.label.lowercase() }
    }

    /**
     * Personal side: resolve the APK files and hand the request to
     * the profile-side copy. The clone itself is finished
     * asynchronously; the result comes back as a cross-profile
     * intent and the main screen updates the list then.
     */
    override fun cloneApp(app: AppInfo): CloneResult {
        val reason = unavailableReason()
        if (reason != null) return CloneResult.Failure(reason)

        val apkPaths = resolveApkPaths(app.packageName)
        if (apkPaths.isEmpty()) {
            return CloneResult.Failure(
                message = "The files of ${app.label} could not be found. " +
                    "Some apps hide their files; try another app, or " +
                    "install it from the Play Store inside the work profile.",
                adbCommand = AdbGuide.installExistingCommand(
                    profileManager.getProfileUserId(),
                    app.packageName
                )
            )
        }

        val sent = profileManager.sendProfileIntent(
            profileManager.profileRequestIntent(
                operation = ProfileManager.OP_CLONE,
                packageName = app.packageName,
                label = app.label,
                apkPaths = apkPaths
            )
        )
        return if (sent) {
            // The profile reports back; the list refreshes then.
            CloneResult.Success(
                CloneInfo(packageName = app.packageName, customName = app.label)
            )
        } else {
            CloneResult.Failure(
                message = "The clone could not be started from here. " +
                    "Connect the phone to a computer once and run " +
                    "the command below, then try again.",
                adbCommand = AdbGuide.installExistingCommand(
                    profileManager.getProfileUserId(),
                    app.packageName
                )
            )
        }
    }

    override fun removeClone(clone: CloneInfo): Boolean {
        val sent = profileManager.sendProfileIntent(
            profileManager.profileRequestIntent(
                operation = ProfileManager.OP_REMOVE,
                packageName = clone.packageName
            )
        )
        if (!sent) return false
        return true
    }

    override fun setFrozen(clone: CloneInfo, frozen: Boolean): Boolean {
        return profileManager.sendProfileIntent(
            profileManager.profileRequestIntent(
                operation = ProfileManager.OP_FREEZE,
                packageName = clone.packageName,
                frozen = frozen
            )
        )
    }

    /**
     * Is there a launchable activity for this package in the profile?
     * LauncherApps answers this for the profile user, which is the
     * only honest check from the personal side.
     */
    override fun isInstalledInProfile(packageName: String): Boolean {
        return try {
            val handle = profileManager.getProfileUserHandle() ?: return false
            val launcherApps = context.getSystemService(LauncherApps::class.java)
            launcherApps.getActivityList(packageName, handle).isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Profile side: install from APK files with a PackageInstaller
     * session. Base APK first, then every split, so split APKs come
     * along. The result arrives later through InstallResultReceiver.
     */
    override fun installFromApkFiles(
        packageName: String,
        label: String,
        apkPaths: List<String>
    ): Boolean {
        if (apkPaths.isEmpty()) return false
        return try {
            val installer = packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            ).apply {
                setAppPackageName(packageName)
                setInstallReason(PackageManager.INSTALL_REASON_USER)
                // A profile owner installing inside its own profile
                // asks Android not to demand an extra tap. If the
                // system still wants one, it shows its own prompt;
                // the install is not lost, it waits for the user.
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            val sessionId = installer.createSession(params)
            val session = installer.openSession(sessionId)
            try {
                apkPaths.forEachIndexed { index, path ->
                    val file = File(path)
                    val name = if (index == 0) "base.apk" else "split$index.apk"
                    session.openWrite(name, 0, file.length()).use { out ->
                        FileInputStream(file).use { input ->
                            input.copyTo(out)
                        }
                        session.fsync(out)
                    }
                }
                val resultIntent = Intent(
                    context,
                    com.majidali.appcloner.data.install.InstallResultReceiver::class.java
                ).apply {
                    putExtra(ProfileManager.EXTRA_OPERATION, ProfileManager.OP_CLONE)
                    putExtra(ProfileManager.EXTRA_PACKAGE, packageName)
                    putExtra(ProfileManager.EXTRA_LABEL, label)
                }
                val pending = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    resultIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                session.commit(pending.intentSender)
            } finally {
                session.close()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Profile side: uninstall a clone. */
    override fun uninstallFromProfile(packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resultIntent = Intent(
                    context,
                    com.majidali.appcloner.data.install.InstallResultReceiver::class.java
                ).apply {
                    putExtra(ProfileManager.EXTRA_OPERATION, ProfileManager.OP_REMOVE)
                    putExtra(ProfileManager.EXTRA_PACKAGE, packageName)
                }
                val pending = PendingIntent.getBroadcast(
                    context,
                    packageName.hashCode(),
                    resultIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                packageManager.packageInstaller.uninstall(packageName, pending.intentSender)
            } else {
                // API 28: no uninstall() API; ask Android's own
                // uninstall screen instead.
                val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                    data = android.net.Uri.parse("package:$packageName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Profile side: hide (freeze) or unhide a clone. */
    override fun applyFrozen(packageName: String, frozen: Boolean): Boolean {
        return try {
            val devicePolicyManager =
                context.getSystemService(android.app.admin.DevicePolicyManager::class.java)
            // setApplicationHidden is a profile-owner API: hiding an
            // app inside the profile stops it from running and from
            // sending notifications. Unhiding restores it exactly as
            // it was — a frozen clone loses no data.
            devicePolicyManager.setApplicationHidden(adminComponent, packageName, frozen)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Where the app's APK files live: base APK plus every split.
     * APK files are world-readable, which is why the profile-side
     * copy can read them and feed them to its installer session.
     */
    private fun resolveApkPaths(packageName: String): List<String> {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val paths = ArrayList<String>()
            paths.add(appInfo.sourceDir)
            appInfo.splitSourceDirs?.let { paths.addAll(it) }
            // Keep only files that really exist; a stale entry would
            // fail the whole install session.
            paths.filter { File(it).exists() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Best-effort check for split APKs, from the installed package. */
    private fun hasSplitApks(packageName: String): Boolean {
        return try {
            val info = packageManager.getPackageInfo(packageName, 0)
            info.splitNames != null && info.splitNames.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
}
