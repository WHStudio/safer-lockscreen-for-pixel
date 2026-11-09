package com.whstudio.saferlockscreen.guard

/**
 * Pure decision for one observation.
 *
 * Protection covers the top-edge swipe and dismisses a panel that is actually open.
 * It never turns the screen off. The lock screen, keyguard, and collapsed shade are not
 * a reason to lock the device. Emergency UI suppresses Back so a call is not torn down.
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

        val overlay = settings.blockQuickSettings && signals.interactive
        actions += if (overlay) PlannedAction.SHOW_TOP_OVERLAY else PlannedAction.HIDE_TOP_OVERLAY
        if (shade) actions += PlannedAction.DISMISS_SHADE
        if (power) actions += PlannedAction.PRESS_BACK
        return actions
    }
}
