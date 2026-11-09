package com.whstudio.saferlockscreen.guard

/**
 * Pure decision for one observation. The service performs the actions and, for [BlockMethod.AUTO],
 * schedules [shouldEscalateShade] if the shade is still up after the dismiss attempt.
 *
 * Emergency UI suppresses back and force-lock so an emergency call is not torn down. The top-edge
 * overlay and shade dismiss can still run, because those do not close the dialer.
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
            if (!emergency && settings.method == BlockMethod.FORCE_LOCK && signals.adminActive) {
                actions += PlannedAction.LOCK_NOW
            }
        }
        if (power) {
            actions += PlannedAction.PRESS_BACK
            if (settings.method != BlockMethod.DISMISS && signals.adminActive) {
                actions += PlannedAction.LOCK_NOW
            }
        }
        return actions
    }

    /** Auto waits one beat so a successful dismiss does not also blank the screen. */
    fun shouldEscalateShade(settings: GuardSettings, signals: DeviceSignals): Boolean {
        if (!settings.protectionEnabled || !signals.deviceLocked || !signals.adminActive) return false
        if (settings.method != BlockMethod.AUTO || !settings.blockQuickSettings) return false
        if (SurfaceKind.EMERGENCY in signals.surfaces) return false
        return SurfaceKind.SHADE in signals.surfaces
    }
}
