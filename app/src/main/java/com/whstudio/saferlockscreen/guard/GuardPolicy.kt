package com.whstudio.saferlockscreen.guard

/**
 * Pure decision for one observation.
 *
 * [BlockMethod.AUTO] never blank the screen. The lock screen, keyguard, and collapsed shade
 * are not a reason to call lockNow, and an ambiguous shade match must not escalate into one.
 * Only the opt-in [BlockMethod.FORCE_LOCK] method locks. Emergency UI suppresses back and
 * force-lock so a call is not torn down.
 */
internal object GuardPolicy {
    fun plan(settings: GuardSettings, signals: DeviceSignals): Set<PlannedAction> {
        if (!settings.protectionEnabled || !signals.deviceLocked) {
            return setOf(PlannedAction.HIDE_TOP_OVERLAY)
        }

        val emergency = SurfaceKind.EMERGENCY in signals.surfaces
        val shade = settings.blockQuickSettings && SurfaceKind.SHADE in signals.surfaces
        val power = settings.blockPowerMenu && SurfaceKind.POWER_MENU in signals.surfaces && !emergency
        val actions = mutableSetOf<PlannedAction>()

        val overlay = settings.blockQuickSettings &&
            settings.method == BlockMethod.AUTO &&
            signals.interactive
        actions += if (overlay) PlannedAction.SHOW_TOP_OVERLAY else PlannedAction.HIDE_TOP_OVERLAY

        if (shade) {
            actions += PlannedAction.DISMISS_SHADE
        }
        if (power) {
            actions += PlannedAction.PRESS_BACK
        }
        if (!emergency && settings.method == BlockMethod.FORCE_LOCK && signals.adminActive && (shade || power)) {
            actions += PlannedAction.LOCK_NOW
        }
        return actions
    }

    /**
     * Auto used to force-lock when a shade match was still present a moment later. That
     * match fired for the lock screen itself and turned the display off on every wake.
     * There is no delayed force-lock anymore.
     */
    @Suppress("UNUSED_PARAMETER")
    fun shouldEscalateShade(settings: GuardSettings, signals: DeviceSignals): Boolean = false
}
