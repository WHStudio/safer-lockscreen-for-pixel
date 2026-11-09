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
        return GuardSettings(
            protectionEnabled = prefs.getBoolean(KEY_ENABLED, GuardSettings.DEFAULT.protectionEnabled),
            blockQuickSettings = prefs.getBoolean(KEY_BLOCK_QS, GuardSettings.DEFAULT.blockQuickSettings),
            blockPowerMenu = prefs.getBoolean(KEY_BLOCK_POWER, GuardSettings.DEFAULT.blockPowerMenu),
            method = BlockMethod.fromId(prefs.getString(KEY_METHOD, GuardSettings.DEFAULT.method.id)),
        )
    }

    fun update(transform: (GuardSettings) -> GuardSettings) {
        val next = transform(read())
        prefs.edit()
            .putBoolean(KEY_ENABLED, next.protectionEnabled)
            .putBoolean(KEY_BLOCK_QS, next.blockQuickSettings)
            .putBoolean(KEY_BLOCK_POWER, next.blockPowerMenu)
            .putString(KEY_METHOD, next.method.id)
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
        const val KEY_METHOD = "block_method"
    }
}
