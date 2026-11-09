package com.whstudio.saferlockscreen.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
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
import com.whstudio.saferlockscreen.guard.DeviceSignals
import com.whstudio.saferlockscreen.guard.GuardPolicy
import com.whstudio.saferlockscreen.guard.GuardPreferences
import com.whstudio.saferlockscreen.guard.LockObservation
import com.whstudio.saferlockscreen.guard.PlannedAction
import com.whstudio.saferlockscreen.guard.SurfaceClassifier

/**
 * Lock-screen guard. While [KeyguardManager.isDeviceLocked] is true, expanded shade / Quick
 * Settings and the power menu are dismissed. The top edge is covered while locked.
 * The service does not turn the screen off.
 * Unlocked devices, including Smart Lock, are ignored. Lock changes are read from the public
 * [KeyguardManager.isDeviceLocked] query. The keyguard listener APIs are privileged and are not used.
 */
class ShadeGuardService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val preferences by lazy { GuardPreferences(this) }
    private val keyguard by lazy { getSystemService(KeyguardManager::class.java) }
    private val power by lazy { getSystemService(PowerManager::class.java) }
    private val overlay by lazy { TopEdgeOverlay(this) }

    private var lastDismissAt = 0L
    private var lastBackAt = 0L
    private var watchdogPosted = false
    private var contentScanPosted = false
    private var interactivePollPosted = false

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        handler.post { evaluate(null) }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            handler.post { evaluate(null) }
        }
    }

    private val watchdog = Runnable {
        watchdogPosted = false
        evaluate(null)
    }

    private val contentScan = Runnable {
        contentScanPosted = false
        evaluate(null)
    }

    private val interactivePoll = Runnable {
        interactivePollPosted = false
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
        contentScanPosted = false
        interactivePollPosted = false
        overlay.hide()
        preferences.unregister(preferenceListener)
        runCatching { unregisterReceiver(screenReceiver) }
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter()
        LockObservation.BROADCASTS.forEach(filter::addAction)
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
            refreshInteractivePoll(settings.protectionEnabled && interactive)
            if (!LockObservation.shouldGuard(settings.protectionEnabled, locked, interactive)) {
                overlay.hide()
                stopWatchdog()
                return
            }
            val current = signals(event)
            apply(GuardPolicy.plan(settings, current))
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
            surfaces = SurfaceClassifier.classify(snapshots),
        )
    }

    private fun apply(actions: Set<PlannedAction>) {
        if (PlannedAction.SHOW_TOP_OVERLAY in actions) overlay.show() else overlay.hide()
        if (PlannedAction.DISMISS_SHADE in actions) dismissShade()
        if (PlannedAction.PRESS_BACK in actions) pressBack()
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

    private fun ensureWatchdog() {
        if (watchdogPosted) return
        watchdogPosted = true
        handler.postDelayed(watchdog, WATCHDOG_MS)
    }

    private fun stopWatchdog() {
        watchdogPosted = false
        handler.removeCallbacks(watchdog)
    }

    private fun refreshInteractivePoll(active: Boolean) {
        if (!active) {
            interactivePollPosted = false
            handler.removeCallbacks(interactivePoll)
            return
        }
        if (interactivePollPosted) return
        interactivePollPosted = true
        handler.postDelayed(interactivePoll, LockObservation.INTERACTIVE_POLL_MS)
    }

    companion object {
        private const val TAG = "SaferLockscreen"
        private const val WATCHDOG_MS = 200L
        private const val DISMISS_DEBOUNCE_MS = 70L
        private const val BACK_DEBOUNCE_MS = 400L

        @Volatile
        private var instance: ShadeGuardService? = null

        fun onExternalSignal() {
            val service = instance ?: return
            service.handler.post { service.evaluate(null) }
        }
    }
}
