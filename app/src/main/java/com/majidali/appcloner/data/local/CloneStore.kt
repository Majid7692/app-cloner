package com.majidali.appcloner.data.local

import android.content.Context
import com.majidali.appcloner.domain.model.CloneInfo
import com.majidali.appcloner.domain.model.CloneLimits
import com.majidali.appcloner.domain.repository.CloneRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Stores the clone list as a private JSON file.
 *
 * Why plain JSON and not a database: the list is tiny (at most 2
 * entries in V1), the file is the backup format, and a human can read
 * it. The file lives in private storage, so no other app can see it.
 * A corrupt file is treated as "no clones yet", never as a crash.
 */
class CloneStore(context: Context) : CloneRepository {

    private val storeFile = File(context.filesDir, FILE_NAME)

    override fun listClones(): List<CloneInfo> = readAll()

    override fun findClone(packageName: String): CloneInfo? =
        readAll().firstOrNull { it.packageName == packageName }

    override fun canAddClone(): Boolean = readAll().size < CloneLimits.MAX_CLONES_V1

    override fun addClone(clone: CloneInfo): Boolean {
        val current = readAll().toMutableList()
        if (current.any { it.packageName == clone.packageName }) return false
        if (current.size >= CloneLimits.MAX_CLONES_V1) return false
        current.add(clone)
        return writeAll(current)
    }

    override fun updateClone(clone: CloneInfo): Boolean {
        val current = readAll().toMutableList()
        val index = current.indexOfFirst { it.packageName == clone.packageName }
        if (index < 0) return false
        current[index] = clone
        return writeAll(current)
    }

    override fun removeClone(packageName: String): Boolean {
        val current = readAll().toMutableList()
        val removed = current.removeAll { it.packageName == packageName }
        if (!removed) return false
        return writeAll(current)
    }

    override fun exportJson(): String = toJson(readAll())

    override fun importJson(json: String): Int {
        val imported = try {
            fromJson(json)
        } catch (e: Exception) {
            return -1
        }
        // Keep the first entries up to the V1 limit; skip the rest
        // instead of failing the whole restore.
        val kept = imported.take(CloneLimits.MAX_CLONES_V1)
        return if (writeAll(kept)) kept.size else -1
    }

    private fun readAll(): List<CloneInfo> {
        if (!storeFile.exists()) return emptyList()
        return try {
            fromJson(storeFile.readText())
        } catch (e: Exception) {
            // A damaged file means "no clones", not a crash.
            emptyList()
        }
    }

    private fun writeAll(clones: List<CloneInfo>): Boolean {
        return try {
            storeFile.writeText(toJson(clones))
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun toJson(clones: List<CloneInfo>): String {
        val array = JSONArray()
        for (clone in clones) {
            val obj = JSONObject()
            obj.put(KEY_PACKAGE, clone.packageName)
            obj.put(KEY_NAME, clone.customName)
            obj.put(KEY_ICON, clone.customIconFileName)
            obj.put(KEY_FROZEN, clone.isFrozen)
            obj.put(KEY_CREATED, clone.createdAtEpochMillis)
            array.put(obj)
        }
        return JSONObject().put(KEY_CLONES, array).toString()
    }

    private fun fromJson(json: String): List<CloneInfo> {
        val root = JSONObject(json)
        val array = root.optJSONArray(KEY_CLONES) ?: return emptyList()
        val result = ArrayList<CloneInfo>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val packageName = obj.optString(KEY_PACKAGE)
            if (packageName.isBlank()) continue
            result.add(
                CloneInfo(
                    packageName = packageName,
                    customName = obj.optString(KEY_NAME, packageName),
                    customIconFileName = obj.optString(KEY_ICON).ifBlank { null },
                    isFrozen = obj.optBoolean(KEY_FROZEN, false),
                    createdAtEpochMillis = obj.optLong(KEY_CREATED, System.currentTimeMillis())
                )
            )
        }
        return result
    }

    companion object {
        const val FILE_NAME: String = "clones.json"
        private const val KEY_CLONES = "clones"
        private const val KEY_PACKAGE = "package"
        private const val KEY_NAME = "name"
        private const val KEY_ICON = "icon"
        private const val KEY_FROZEN = "frozen"
        private const val KEY_CREATED = "created"
    }
}
