package com.whstudio.saferlockscreen.guard

/**
 * How a normal app learns whether the device is locked.
 *
 * [android.app.KeyguardManager.addKeyguardLockedStateListener] and
 * [android.app.KeyguardManager.addDeviceLockedStateListener] both require
 * [android.Manifest.permission.SUBSCRIBE_TO_KEYGUARD_LOCKED_STATE]. That permission is
 * signature|privileged|module|role, so a sideloaded app cannot hold it. Calling either
 * listener crashes the process. Declaring the permission does not grant it.
 *
 * [android.app.KeyguardManager.isDeviceLocked] is a public query and needs no extra
 * permission. It is false when Smart Lock or Extend Unlock has already authenticated the
 * user. The broadcasts below are delivered to third-party apps. While the screen is on,
 * the service also polls [android.app.KeyguardManager.isDeviceLocked] because those
 * broadcasts do not cover every lock transition.
 */
internal object LockObservation {
    const val INTERACTIVE_POLL_MS = 1_000L

    val BROADCASTS: List<String> = listOf(
        "android.intent.action.SCREEN_ON",
        "android.intent.action.SCREEN_OFF",
        "android.intent.action.USER_PRESENT",
        "android.intent.action.USER_UNLOCKED",
    )

    fun shouldGuard(protectionEnabled: Boolean, deviceLocked: Boolean, interactive: Boolean): Boolean {
        return protectionEnabled && deviceLocked && interactive
    }
}
