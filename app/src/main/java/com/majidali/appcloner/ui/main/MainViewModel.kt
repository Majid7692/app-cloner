package com.majidali.appcloner.ui.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.majidali.appcloner.AppContainer
import com.majidali.appcloner.domain.model.CloneInfo

/**
 * State for the main screen. It only holds data; every decision
 * (limits, profile state) lives in the repository and the engine.
 */
class MainViewModel(private val container: AppContainer) : ViewModel() {

    private val _clones = MutableLiveData<List<CloneInfo>>(emptyList())
    val clones: LiveData<List<CloneInfo>> = _clones

    private val _profileReady = MutableLiveData(false)
    val profileReady: LiveData<Boolean> = _profileReady

    private val _profileAwake = MutableLiveData(true)
    val profileAwake: LiveData<Boolean> = _profileAwake

    /** Re-read everything from storage. Call on resume and after
     *  every change, so the screen never shows stale data. */
    fun refresh() {
        _clones.value = container.cloneRepository.listClones()
        _profileReady.value = container.profileManager.isProfileReady()
        _profileAwake.value = container.profileManager.isProfileAwake()
    }

    /** How many more clones V1 allows right now. */
    fun remainingSlots(): Int {
        val used = container.cloneRepository.listClones().size
        val max = com.majidali.appcloner.domain.model.CloneLimits.MAX_CLONES_V1
        return (max - used).coerceAtLeast(0)
    }

    /**
     * Freeze or unfreeze one clone. Returns true when the request
     * reached the profile. The list itself changes only when the
     * profile answers (see MainActivity.handleProfileResult), so the
     * screen never shows a state the phone does not have.
     */
    fun setFrozen(clone: CloneInfo, frozen: Boolean): Boolean {
        return container.cloneEngine.setFrozen(clone, frozen)
    }

    /**
     * Ask the profile to remove one clone. Returns true when the
     * request was sent. The list entry disappears only when the
     * profile confirms the removal.
     */
    fun removeClone(clone: CloneInfo): Boolean {
        return container.cloneEngine.removeClone(clone)
    }

    /** Turn the whole profile off or on. */
    fun setProfileEnabled(enabled: Boolean): Boolean {
        val ok = container.profileManager.setProfileEnabled(enabled)
        refresh()
        return ok
    }
}
