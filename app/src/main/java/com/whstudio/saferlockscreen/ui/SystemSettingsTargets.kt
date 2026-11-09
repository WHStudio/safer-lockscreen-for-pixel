package com.whstudio.saferlockscreen.ui

/**
 * Settings screens a normal app is allowed to open.
 *
 * [ACCESSIBILITY_DETAILS] resolves to the per-service page. On Android 17, starting it
 * requires [android.Manifest.permission.OPEN_ACCESSIBILITY_DETAILS_SETTINGS], which is
 * signature-or-privileged. A sideloaded app cannot hold that permission, and declaring it
 * does not grant it. The public destination is the accessibility list.
 */
internal object SystemSettingsTargets {
    const val ACCESSIBILITY_LIST = "android.settings.ACCESSIBILITY_SETTINGS"
    const val ACCESSIBILITY_DETAILS = "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"

    fun accessibilityAction(): String = ACCESSIBILITY_LIST

    fun isSafeToStart(action: String?): Boolean {
        return action != null && action != ACCESSIBILITY_DETAILS
    }
}
