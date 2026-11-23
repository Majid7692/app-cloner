package com.majidali.appcloner.admin

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.majidali.appcloner.data.profile.ProfileManager

/**
 * The device admin receiver that owns the managed work profile.
 *
 * Android calls [onProfileProvisioningComplete] once, right after the
 * user accepts the provisioning screen. Three things happen here:
 * 1. The profile gets its name, so the user can tell it apart in
 *    Settings. The name is only visible on this phone.
 * 2. The profile is switched on, so clones can run and notify
 *    straight away.
 * 3. The cross-profile intent filters are registered, so the
 *    personal-side app can ask the profile-side copy to clone, remove
 *    or freeze apps — and the profile side can answer back.
 *
 * Nothing else happens here: no password is set, nothing is locked,
 * nothing is wiped.
 */
class CloneDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        try {
            val admin = ComponentName(context, CloneDeviceAdminReceiver::class.java)
            val devicePolicyManager =
                context.getSystemService(DevicePolicyManager::class.java)
            devicePolicyManager.setProfileName(admin, PROFILE_NAME)
            devicePolicyManager.setProfileEnabled(admin)
            ProfileManager(context).registerCrossProfileFilters()
        } catch (e: Exception) {
            // Provisioning already succeeded; a failure to rename the
            // profile or register filters must not undo it or crash
            // the setup flow. The Setup screen offers to retry.
        }
    }

    companion object {
        const val PROFILE_NAME: String = "App Cloner"
    }
}
