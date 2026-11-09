package com.whstudio.saferlockscreen.admin

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.whstudio.saferlockscreen.R

/**
 * Kept so a device admin enabled by an older build can be deactivated.
 * The app does not call lockNow and does not ask to be activated.
 */
class LockDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return context.getString(R.string.device_admin_disable_warning)
    }

    companion object {
        fun component(context: Context): ComponentName {
            return ComponentName(context, LockDeviceAdminReceiver::class.java)
        }
    }
}
