package com.whstudio.saferlockscreen.guard

import android.content.Context
import android.content.SharedPreferences

/**
 * Stored in device-protected storage so the accessibility service can read it after reboot,
 * before the first credential unlock.
 */
internal class GuardPreferences(context: Context) {
    private val prefs: SharedPreferences = context.createDeviceProtectedStorageContext()
        .getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun read(): GuardSettings {
        // Older builds stored dismiss / force-lock here. Those modes are gone; drop the value
        // so a previous choice cannot be read back into a lock path.
        if (prefs.contains(KEY_REMOVED_METHOD)) {
            prefs.edit().remove(KEY_REMOVED_METHOD).apply()
        }
        return GuardSettings(
            protectionEnabled = prefs.getBoolean(KEY_ENABLED, GuardSettings.DEFAULT.protectionEnabled),
            blockQuickSettings = prefs.getBoolean(KEY_BLOCK_QS, GuardSettings.DEFAULT.blockQuickSettings),
            blockPowerMenu = prefs.getBoolean(KEY_BLOCK_POWER, GuardSettings.DEFAULT.blockPowerMenu),
        )
    }

    fun update(transform: (GuardSettings) -> GuardSettings) {
        val next = transform(read())
        prefs.edit()
            .putBoolean(KEY_ENABLED, next.protectionEnabled)
            .putBoolean(KEY_BLOCK_QS, next.blockQuickSettings)
            .putBoolean(KEY_BLOCK_POWER, next.blockPowerMenu)
            .remove(KEY_REMOVED_METHOD)
            .apply()
    }

    fun register(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregister(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private companion object {
        const val NAME = "safer_lockscreen"
        const val KEY_ENABLED = "protection_enabled"
        const val KEY_BLOCK_QS = "block_quick_settings"
        const val KEY_BLOCK_POWER = "block_power_menu"
        const val KEY_REMOVED_METHOD = "block_method"
    }
}
