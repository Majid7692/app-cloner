package com.majidali.appcloner.domain.engine

import com.majidali.appcloner.domain.model.AppInfo
import com.majidali.appcloner.domain.model.CloneInfo
import com.majidali.appcloner.domain.model.CloneResult

/**
 * How clones are created and managed.
 *
 * V1 ships exactly one engine: [WorkProfileCloneEngine], which installs
 * an already-installed app into the managed work profile. V2 adds a
 * sandbox engine for more than 2 clones; it plugs in here without
 * changing the screens, because every screen talks to this interface
 * and never to an engine directly.
 *
 * Two sides, one interface: the app runs in BOTH users (personal and
 * work profile). The personal-side methods ([cloneApp], [removeClone],
 * [setFrozen]) are what the screens call. They hand the real work to
 * the profile-side copy through a cross-profile intent, and the
 * profile-side methods ([installFromApkFiles], [uninstallFromProfile],
 * [applyFrozen]) do the work there, because only the profile-side
 * copy may install, uninstall or hide apps inside the profile.
 */
interface CloneEngine {

    /** Short name shown in the Limits screen, e.g. "Work profile". */
    val engineName: String

    /** True when this engine can run on this phone right now. */
    fun isAvailable(): Boolean

    /** Why this engine is not available, in plain words, or null. */
    fun unavailableReason(): String?

    /** Apps the user can clone with this engine. */
    fun listClonableApps(): List<AppInfo>

    /**
     * Create a clone of [app] (personal side). Returns
     * [CloneResult.Success] when the request reached the profile, or
     * [CloneResult.Failure] with a plain-words message and, when
     * useful, one ADB command that does the same thing.
     */
    fun cloneApp(app: AppInfo): CloneResult

    /** Remove a clone (personal side). Returns false when the request
     *  could not even be sent. */
    fun removeClone(clone: CloneInfo): Boolean

    /**
     * Freeze (hide) or unfreeze a clone (personal side). A frozen
     * clone does not run and sends no notifications.
     */
    fun setFrozen(clone: CloneInfo, frozen: Boolean): Boolean

    /** Is this package currently installed inside the profile? */
    fun isInstalledInProfile(packageName: String): Boolean

    /**
     * Profile-side install: install the app from its APK files
     * ([apkPaths], base APK first, then splits) into this profile
     * with a PackageInstaller session. Returns true when the session
     * started; the result arrives later through InstallResultReceiver.
     */
    fun installFromApkFiles(packageName: String, label: String, apkPaths: List<String>): Boolean

    /** Profile-side uninstall of a clone. */
    fun uninstallFromProfile(packageName: String): Boolean

    /** Profile-side freeze / unfreeze. */
    fun applyFrozen(packageName: String, frozen: Boolean): Boolean
}

/**
 * Placeholder for the V2 sandbox engine.
 *
 * It is deliberately NOT wired into the app in V1: sandbox cloning
 * (running many copies inside one host app) needs a different
 * permission and reliability story, and shipping a half-working
 * version would break the "no crashes, clear errors" promise.
 * V2 replaces this class with a real implementation and flips one
 * line in [com.majidali.appcloner.AppContainer].
 */
class SandboxCloneEngine : CloneEngine {
    override val engineName: String = "Sandbox (V2)"

    override fun isAvailable(): Boolean = false

    override fun unavailableReason(): String =
        "Sandbox mode comes in V2. V1 clones apps through the work " +
            "profile, which is the reliable way on this phone."

    override fun listClonableApps(): List<AppInfo> = emptyList()

    override fun cloneApp(app: AppInfo): CloneResult =
        CloneResult.Failure(unavailableReason())

    override fun removeClone(clone: CloneInfo): Boolean = false

    override fun setFrozen(clone: CloneInfo, frozen: Boolean): Boolean = false

    override fun isInstalledInProfile(packageName: String): Boolean = false

    override fun installFromApkFiles(packageName: String, label: String, apkPaths: List<String>): Boolean = false

    override fun uninstallFromProfile(packageName: String): Boolean = false

    override fun applyFrozen(packageName: String, frozen: Boolean): Boolean = false
}
