package com.majidali.appcloner.domain.model

/**
 * One installed app that the user may clone.
 *
 * @param packageName the app's unique package, e.g. "com.whatsapp".
 * @param label the name shown on the home screen.
 * @param isSystemApp true for apps that came with the phone.
 * @param hasSplits true when the app was installed as base + split APKs.
 *   Most Play Store apps are split APKs. A clone must carry every split;
 *   copying only the base APK crashes on launch. The work profile engine
 *   handles this by installing the already-installed package (which
 *   keeps all its splits) instead of copying APK files around.
 */
data class AppInfo(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean,
    val hasSplits: Boolean
)

/**
 * One clone that lives inside the managed work profile.
 *
 * A clone is identified by the original package name. The custom name
 * and icon only change the home screen shortcut; the app itself inside
 * the profile keeps its own name and icon, because Android does not
 * let one app rename another.
 *
 * @param packageName original package, e.g. "com.whatsapp".
 * @param customName name shown on the clone's home screen shortcut.
 * @param customIconFileName file name of the custom icon in private
 *   storage, or null when the original icon is used.
 * @param isFrozen true when the clone is hidden (frozen) inside the
 *   profile. A frozen clone does not run and sends no notifications.
 * @param createdAtEpochMillis when the clone was created.
 */
data class CloneInfo(
    val packageName: String,
    val customName: String,
    val customIconFileName: String? = null,
    val isFrozen: Boolean = false,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
)

/** Result of one clone operation, with a human-readable message. */
sealed class CloneResult {
    /** The clone was created (or already existed) in the profile. */
    data class Success(val clone: CloneInfo) : CloneResult()

    /**
     * The clone could not be created. [message] is written for a normal
     * person, not a developer: what happened and what to try next.
     * [adbCommand] is set when the fix needs one command from a computer.
     */
    data class Failure(val message: String, val adbCommand: String? = null) : CloneResult()
}

/** Reasons a clone operation can fail, kept separate from UI text. */
class CloneException(message: String, val adbCommand: String? = null) : Exception(message)

/** Hard limits for V1, in one place so the UI and the engine agree. */
object CloneLimits {
    /** V1 allows at most this many clones. A work profile holds one
     *  extra copy of each app; the limit keeps the phone fast and the
     *  setup simple. V2 (sandbox mode) raises this, not V1. */
    const val MAX_CLONES_V1: Int = 2

    /** Largest custom icon the app stores, in kilobytes. */
    const val MAX_CUSTOM_ICON_KB: Int = 512
}
