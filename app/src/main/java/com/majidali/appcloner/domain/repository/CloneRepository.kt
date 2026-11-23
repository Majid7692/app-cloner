package com.majidali.appcloner.domain.repository

import com.majidali.appcloner.domain.model.CloneInfo
import com.majidali.appcloner.domain.model.CloneLimits

/**
 * The clone list: what the user has cloned, with their custom names,
 * icons and frozen state. The repository owns the V1 rule "at most
 * [CloneLimits.MAX_CLONES_V1] clones" so no screen can forget it.
 *
 * Storage is a private JSON file (see CloneStore). No account, no
 * server, nothing leaves the phone except when the user exports a
 * backup file themselves.
 */
interface CloneRepository {
    /** All clones, oldest first. */
    fun listClones(): List<CloneInfo>

    /** Find one clone by its original package name, or null. */
    fun findClone(packageName: String): CloneInfo?

    /** True when another clone may be added under the V1 limit. */
    fun canAddClone(): Boolean

    /**
     * Save a new clone. Returns false when the V1 limit is reached
     * or the package is already cloned.
     */
    fun addClone(clone: CloneInfo): Boolean

    /** Save changes to one clone (rename, icon, frozen state). */
    fun updateClone(clone: CloneInfo): Boolean

    /** Forget one clone. Returns false when it was not in the list. */
    fun removeClone(packageName: String): Boolean

    /** Whole list as JSON, for the backup file. */
    fun exportJson(): String

    /**
     * Replace the list from a backup file. Returns the number of
     * clones restored, or -1 when the file is not a valid backup.
     * Extra entries beyond the V1 limit are skipped, never crashed on.
     */
    fun importJson(json: String): Int
}
