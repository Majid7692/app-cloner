package com.majidali.appcloner.data.local

import android.content.Context
import android.net.Uri
import com.majidali.appcloner.domain.repository.CloneRepository
import java.io.IOException

/**
 * Backup and restore of the clone list.
 *
 * The backup is one JSON file the user saves where they like, using
 * Android's file picker. Nothing is uploaded anywhere. Restoring only
 * brings back the list (names, icons, frozen state); the apps
 * themselves must be re-cloned, because a backup file cannot install
 * apps — and that is a good thing.
 */
class BackupManager(
    private val context: Context,
    private val repository: CloneRepository
) {

    /** File name offered when the user saves a backup. */
    fun backupFileName(): String = "app-cloner-backup.json"

    /**
     * Write the clone list to [uri] (chosen by the user in the file
     * picker). Returns true on success.
     */
    fun exportTo(uri: Uri): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(repository.exportJson().toByteArray(Charsets.UTF_8))
            } ?: return false
            true
        } catch (e: IOException) {
            false
        }
    }

    /**
     * Read a backup from [uri] and restore the list.
     * Returns the number of clones restored, or -1 when the file is
     * not a valid App Cloner backup.
     */
    fun importFrom(uri: Uri): Int {
        return try {
            val text = context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            } ?: return -1
            repository.importJson(text)
        } catch (e: IOException) {
            -1
        }
    }
}
