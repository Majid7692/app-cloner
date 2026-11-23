package com.majidali.appcloner.data.profile

/**
 * The one ADB command that does what the app does, for the rare case
 * the in-app path is unavailable (for example, the profile-side copy
 * has not started yet).
 *
 * These commands never run by themselves: the app shows them to the
 * user, who runs them once from a computer. Nothing here needs root.
 * USB debugging and "Install via USB" must be on (Settings →
 * Developer options), and the phone must be signed in to its Mi
 * account on Xiaomi phones, or the computer cannot install anything.
 */
object AdbGuide {

    /**
     * Install an already-installed app into the work profile.
     * Android copies the whole package, splits included.
     */
    fun installExistingCommand(profileUserId: Int, packageName: String): String {
        val user = if (profileUserId >= 0) profileUserId else 10
        return "adb shell pm install-existing --user $user $packageName"
    }

    /** List users on the phone, to find the profile's user id. */
    const val LIST_USERS_COMMAND: String = "adb shell pm list users"

    /** Remove the whole work profile (last resort, deletes clones). */
    fun removeProfileCommand(profileUserId: Int): String {
        val user = if (profileUserId >= 0) profileUserId else 10
        return "adb shell pm remove-user $user"
    }
}
