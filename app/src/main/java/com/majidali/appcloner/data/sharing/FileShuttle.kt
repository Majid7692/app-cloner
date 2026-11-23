package com.majidali.appcloner.data.sharing

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Moves a file from the personal profile to a clone (or back) — the
 * file shuttle.
 *
 * The rule is simple and has no exceptions: files cross the profile
 * border ONLY as content:// URIs from FileProvider, with a one-time
 * read grant. Raw file paths are never used.
 *
 * Why: a raw path means something different in each profile, and on
 * some phones (notably Huawei and Oppo) the MediaStore returns the
 * wrong file — or no file — when a path from the other profile is
 * handed to it. A content URI with a grant always points at exactly
 * one file. The device checklist (BUILD-REPORT.md) tests this by
 * saving a photo in the clone and sharing it back, on more than one
 * phone brand.
 *
 * V1 scope: the shuttle shares a file the user picks. It does not
 * sync whole folders and it does not run in the background.
 */
class FileShuttle(private val context: Context) {

    /**
     * Copy a file the user picked into the app's private shared
     * folder, then return its shareable content URI.
     * Returns null when the copy fails.
     */
    fun stageForSharing(sourceUri: Uri, fileName: String): Uri? {
        return try {
            val sharedDir = File(context.filesDir, SHARED_DIR).apply { mkdirs() }
            val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val target = File(sharedDir, safeName)
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                target
            )
        } catch (e: Exception) {
            null
        }
    }

    /** Share intent for a staged file: content URI + one-time grant. */
    fun shareIntent(contentUri: Uri, mimeType: String): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Delete staged files older than a day, to save space. */
    fun cleanStagedFiles() {
        try {
            val sharedDir = File(context.filesDir, SHARED_DIR)
            if (!sharedDir.exists()) return
            val cutoff = System.currentTimeMillis() - 24L * 60L * 60L * 1000L
            sharedDir.listFiles()?.forEach { file ->
                if (file.lastModified() < cutoff) file.delete()
            }
        } catch (e: Exception) {
            // Cleaning is best-effort; a failure here must never crash
            // the screen the user is looking at.
        }
    }

    companion object {
        const val SHARED_DIR: String = "shared"
    }
}
