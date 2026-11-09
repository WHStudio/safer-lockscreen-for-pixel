package com.whstudio.saferlockscreen

import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.whstudio.saferlockscreen.admin.LockDeviceAdminReceiver
import com.whstudio.saferlockscreen.guard.GuardPreferences
import com.whstudio.saferlockscreen.guard.GuardSettings
import com.whstudio.saferlockscreen.ui.HomeScreen
import com.whstudio.saferlockscreen.ui.ProtectionState
import com.whstudio.saferlockscreen.ui.SaferTheme
import com.whstudio.saferlockscreen.ui.SystemSettingsTargets
import com.whstudio.saferlockscreen.ui.loadProtectionState

class MainActivity : ComponentActivity() {
    private val preferences by lazy { GuardPreferences(this) }
    private var state by mutableStateOf(
        ProtectionState(
            settings = GuardSettings.DEFAULT,
            accessibilityEnabled = false,
            adminActive = false,
            deviceSecure = true,
        ),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        refresh()
        setContent {
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) refresh()
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            SaferTheme {
                HomeScreen(
                    state = state,
                    onChangeSettings = { transform ->
                        preferences.update(transform)
                        refresh()
                    },
                    onEnableAdmin = ::enableAdmin,
                    onDisableAdmin = ::disableAdmin,
                    onOpenAccessibility = ::openAccessibility,
                    onLockNow = ::lockNow,
                )
            }
        }
    }

    private fun refresh() {
        state = loadProtectionState(this, preferences.read())
    }

    private fun enableAdmin() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, LockDeviceAdminReceiver.component(this@MainActivity))
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.device_admin_explanation))
        }
        if (!startIfAvailable(intent)) {
            showSettingsFailure()
        }
    }

    private fun disableAdmin() {
        val manager = getSystemService(DevicePolicyManager::class.java)
        val component = LockDeviceAdminReceiver.component(this)
        if (manager.isAdminActive(component)) {
            manager.removeActiveAdmin(component)
        }
        refresh()
    }

    private fun openAccessibility() {
        val opened = startIfAvailable(Intent(SystemSettingsTargets.accessibilityAction()))
        if (!opened) {
            showSettingsFailure()
        }
    }

    private fun lockNow() {
        val manager = getSystemService(DevicePolicyManager::class.java)
        val component = LockDeviceAdminReceiver.component(this)
        if (!manager.isAdminActive(component)) return
        manager.lockNow()
    }

    private fun startIfAvailable(intent: Intent): Boolean {
        if (!SystemSettingsTargets.isSafeToStart(intent.action)) return false
        return try {
            startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private fun showSettingsFailure() {
        Toast.makeText(this, R.string.settings_open_failed, Toast.LENGTH_LONG).show()
    }
}
