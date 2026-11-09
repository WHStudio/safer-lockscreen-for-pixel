package com.whstudio.saferlockscreen.ui

import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.whstudio.saferlockscreen.admin.LockDeviceAdminReceiver
import com.whstudio.saferlockscreen.guard.BlockMethod
import com.whstudio.saferlockscreen.guard.GuardSettings
import com.whstudio.saferlockscreen.service.ShadeGuardService

internal data class ProtectionState(
    val settings: GuardSettings,
    val accessibilityEnabled: Boolean,
    val adminActive: Boolean,
    val deviceSecure: Boolean,
) {
    val needsAdmin: Boolean
        get() = settings.method != BlockMethod.DISMISS

    val fullyActive: Boolean
        get() = settings.protectionEnabled &&
            accessibilityEnabled &&
            (!needsAdmin || adminActive)

    val partiallyActive: Boolean
        get() = settings.protectionEnabled && accessibilityEnabled && !fullyActive
}

internal fun loadProtectionState(context: Context, settings: GuardSettings): ProtectionState {
    return ProtectionState(
        settings = settings,
        accessibilityEnabled = isAccessibilityServiceEnabled(context),
        adminActive = isDeviceAdminActive(context),
        deviceSecure = context.getSystemService(KeyguardManager::class.java).isDeviceSecure,
    )
}

internal fun isDeviceAdminActive(context: Context): Boolean {
    val manager = context.getSystemService(DevicePolicyManager::class.java)
    return manager.isAdminActive(LockDeviceAdminReceiver.component(context))
}

internal fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val resolver = context.contentResolver
    val enabled = Settings.Secure.getInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
    if (!enabled) return false
    val expected = ComponentName(context, ShadeGuardService::class.java).flattenToString()
    val listed = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        ?: return false
    return listed.split(':').any { it.equals(expected, ignoreCase = true) }
}
