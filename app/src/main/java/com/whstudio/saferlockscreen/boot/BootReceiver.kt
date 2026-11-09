package com.whstudio.saferlockscreen.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.whstudio.saferlockscreen.service.ShadeGuardService

/**
 * Locked boot, normal boot, and the first unlock. The accessibility service is started by
 * the system when it is enabled and direct-boot aware. This receiver asks it to re-read
 * lock state if the process is already up.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_USER_UNLOCKED,
            -> ShadeGuardService.onExternalSignal()
        }
    }
}
