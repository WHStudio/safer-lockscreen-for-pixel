package com.whstudio.saferlockscreen.service

import android.app.KeyguardManager
import android.os.Build
import androidx.annotation.RequiresApi
import java.util.concurrent.Executor

/**
 * Kept in its own class so API 31–32 never loads [KeyguardManager.KeyguardLockedStateListener].
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class KeyguardWatch(
    private val keyguard: KeyguardManager,
    private val onChange: () -> Unit,
) {
    private val listener = KeyguardManager.KeyguardLockedStateListener { onChange() }

    fun register(executor: Executor) {
        keyguard.addKeyguardLockedStateListener(executor, listener)
    }

    fun unregister() {
        keyguard.removeKeyguardLockedStateListener(listener)
    }
}
