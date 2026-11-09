package com.whstudio.saferlockscreen.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.whstudio.saferlockscreen.admin.LockDeviceAdminReceiver
import com.whstudio.saferlockscreen.guard.DeviceSignals
import com.whstudio.saferlockscreen.guard.GuardPolicy
import com.whstudio.saferlockscreen.guard.GuardPreferences
import com.whstudio.saferlockscreen.guard.PlannedAction
import com.whstudio.saferlockscreen.guard.SurfaceClassifier

/**
 * Lock-screen guard. While [KeyguardManager.isDeviceLocked] is true, expanded shade / Quick
 * Settings and the power menu are dismissed. Auto also covers the top edge and can force-lock.
 * Unlocked devices, including Smart Lock, are ignored.
 */
class ShadeGuardService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val preferences by lazy { GuardPreferences(this) }
    private val keyguard by lazy { getSystemService(KeyguardManager::class.java) }
    private val power by lazy { getSystemService(PowerManager::class.java) }
    private val devicePolicy by lazy { getSystemService(DevicePolicyManager::class.java) }
    private val overlay by lazy { TopEdgeOverlay(this) }

    private var lastDismissAt = 0L
    private var lastBackAt = 0L
    private var lastLockAt = 0L
    private var watchdogPosted = false
    private var escalationPosted = false
    private var contentScanPosted = false

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        handler.post { evaluate(null) }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            handler.post { evaluate(null) }
        }
    }

    private var keyguardWatch: KeyguardWatch? = null

    private val watchdog = Runnable {
        watchdogPosted = false
        evaluate(null)
    }

    private val escalate = Runnable {
        escalationPosted = false
        val settings = preferences.read()
        val signals = signals(null)
        if (GuardPolicy.shouldEscalateShade(settings, signals)) {
            lockNow()
        }
    }

    private val contentScan = Runnable {
        contentScanPosted = false
        evaluate(null)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        preferences.register(preferenceListener)
        registerScreenReceiver()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            keyguardWatch = KeyguardWatch(keyguard) { handler.post { evaluate(null) } }.also {
                it.register(mainExecutor)
            }
        }
        evaluate(null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            -> evaluate(event)
            // Content events fire continuously. Coalesce them and do not keep the event,
            // which the system recycles when this method returns. State is re-read from windows.
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (!contentScanPosted) {
                    contentScanPosted = true
                    handler.post(contentScan)
                }
            }
            else -> Unit
        }
    }

    override fun onInterrupt() = Unit

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (overlay.isShowing) overlay.show()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        watchdogPosted = false
        escalationPosted = false
        contentScanPosted = false
        overlay.hide()
        preferences.unregister(preferenceListener)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            keyguardWatch?.unregister()
            keyguardWatch = null
        }
        runCatching { unregisterReceiver(screenReceiver) }
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_USER_UNLOCKED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
    }

    private fun evaluate(event: AccessibilityEvent?) {
        try {
            val settings = preferences.read()
            val locked = keyguard.isDeviceLocked
            val interactive = power.isInteractive
            if (!settings.protectionEnabled || !locked || !interactive) {
                overlay.hide()
                stopWatchdog()
                cancelEscalation()
                return
            }
            val current = signals(event)
            val actions = GuardPolicy.plan(settings, current)
            apply(actions)
            // Do not reset the timer on every pass. The watchdog confirms the shade several
            // times a second; resetting would postpone force-lock forever.
            if (GuardPolicy.shouldEscalateShade(settings, current)) {
                if (!escalationPosted) {
                    escalationPosted = true
                    handler.postDelayed(escalate, ESCALATE_DELAY_MS)
                }
            } else {
                cancelEscalation()
            }
            ensureWatchdog()
        } catch (error: RuntimeException) {
            Log.w(TAG, "Guard pass failed: ${error.javaClass.simpleName}")
        }
    }

    private fun signals(event: AccessibilityEvent?): DeviceSignals {
        val locked = keyguard.isDeviceLocked
        if (!locked) {
            return DeviceSignals(
                deviceLocked = false,
                interactive = power.isInteractive,
                adminActive = false,
                surfaces = emptySet(),
            )
        }
        val currentWindows = try {
            windows ?: emptyList()
        } catch (_: IllegalStateException) {
            emptyList()
        }
        val snapshots = try {
            WindowSnapshots.capture(
                windows = currentWindows,
                event = event,
                screenHeightPx = resources.displayMetrics.heightPixels,
            )
        } finally {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                currentWindows.forEach { window ->
                    @Suppress("DEPRECATION")
                    window.recycle()
                }
            }
        }
        return DeviceSignals(
            deviceLocked = true,
            interactive = power.isInteractive,
            adminActive = devicePolicy.isAdminActive(LockDeviceAdminReceiver.component(this)),
            surfaces = SurfaceClassifier.classify(snapshots),
        )
    }

    private fun apply(actions: Set<PlannedAction>) {
        if (PlannedAction.SHOW_TOP_OVERLAY in actions) overlay.show() else overlay.hide()
        if (PlannedAction.DISMISS_SHADE in actions) dismissShade()
        if (PlannedAction.PRESS_BACK in actions) pressBack()
        if (PlannedAction.LOCK_NOW in actions) lockNow()
    }

    private fun dismissShade() {
        val now = SystemClock.uptimeMillis()
        if (now - lastDismissAt < DISMISS_DEBOUNCE_MS) return
        lastDismissAt = now
        val performed = performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        Log.i(TAG, "dismissShade performed=$performed")
    }

    private fun pressBack() {
        val now = SystemClock.uptimeMillis()
        if (now - lastBackAt < BACK_DEBOUNCE_MS) return
        lastBackAt = now
        val performed = performGlobalAction(GLOBAL_ACTION_BACK)
        Log.i(TAG, "dismissPowerMenu performed=$performed")
    }

    private fun lockNow() {
        val now = SystemClock.uptimeMillis()
        if (now - lastLockAt < LOCK_DEBOUNCE_MS) return
        lastLockAt = now
        try {
            devicePolicy.lockNow()
            Log.i(TAG, "lockNow")
        } catch (error: SecurityException) {
            Log.w(TAG, "lockNow rejected")
        }
    }

    private fun ensureWatchdog() {
        if (watchdogPosted) return
        watchdogPosted = true
        handler.postDelayed(watchdog, WATCHDOG_MS)
    }

    private fun stopWatchdog() {
        watchdogPosted = false
        handler.removeCallbacks(watchdog)
    }

    private fun cancelEscalation() {
        escalationPosted = false
        handler.removeCallbacks(escalate)
    }

    companion object {
        private const val TAG = "SaferLockscreen"
        private const val WATCHDOG_MS = 200L
        private const val ESCALATE_DELAY_MS = 450L
        private const val DISMISS_DEBOUNCE_MS = 70L
        private const val BACK_DEBOUNCE_MS = 400L
        private const val LOCK_DEBOUNCE_MS = 1_200L

        @Volatile
        private var instance: ShadeGuardService? = null

        fun onExternalSignal() {
            val service = instance ?: return
            service.handler.post { service.evaluate(null) }
        }
    }
}
