package com.majidali.appcloner.data.profile

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon

/**
 * Home screen shortcuts for clones.
 *
 * A shortcut must be created from inside the profile it belongs to:
 * only the profile-side copy can ask the launcher for a shortcut
 * that opens the profile's app. So this helper runs on the profile
 * side, on a PROFILE_REQUEST with op "shortcut".
 *
 * The shortcut carries the clone's custom name and icon. The icon
 * arrives as a small PNG byte array in the request (the custom icon
 * file lives in the personal user's private storage, which the
 * profile cannot read). The array is kept small — the detail screen
 * downscales the picked picture to 192x192 first — so it stays well
 * under Android's intent size limit.
 *
 * Renaming and re-iconing change only this shortcut. The app itself
 * inside the profile keeps its own name and icon, because Android
 * does not let one app rename another.
 */
class ShortcutHelper(private val context: Context) {

    fun createShortcut(
        packageName: String,
        label: String,
        iconBytes: ByteArray?
    ): Boolean {
        return try {
            val launchIntent = context.packageManager
                .getLaunchIntentForPackage(packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ?: return false
            val shortcutManager = context.getSystemService(ShortcutManager::class.java)
            val icon = iconBytes?.let { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { bitmap ->
                    Icon.createWithBitmap(bitmap)
                }
            } ?: Icon.createWithResource(context, android.R.mipmap.sym_def_app_icon)
            val shortcut = ShortcutInfo.Builder(context, "clone-$packageName")
                .setShortLabel(label)
                .setLongLabel(label)
                .setIcon(icon)
                .setIntent(launchIntent)
                .build()
            shortcutManager.requestPinShortcut(shortcut, null)
        } catch (e: Exception) {
            false
        }
    }
}
