package com.whstudio.saferlockscreen.guard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardPolicyTest {
    private val armed = GuardSettings(
        protectionEnabled = true,
        blockQuickSettings = true,
        blockPowerMenu = true,
    )

    @Test
    fun disabledOrUnlockedHidesOverlay() {
        val disabled = GuardPolicy.plan(armed.copy(protectionEnabled = false), locked())
        val unlocked = GuardPolicy.plan(armed, locked(deviceLocked = false))
        assertEquals(setOf(PlannedAction.HIDE_TOP_OVERLAY), disabled)
        assertEquals(setOf(PlannedAction.HIDE_TOP_OVERLAY), unlocked)
    }

    @Test
    fun showsOverlayOnlyWhileInteractive() {
        assertTrue(PlannedAction.SHOW_TOP_OVERLAY in GuardPolicy.plan(armed, locked()))
        assertTrue(
            PlannedAction.HIDE_TOP_OVERLAY in GuardPolicy.plan(armed, locked(interactive = false)),
        )
    }

    @Test
    fun openShadeIsDismissedAndDoesNotBlankTheScreen() {
        val actions = GuardPolicy.plan(armed, locked(surfaces = setOf(SurfaceKind.SHADE)))
        assertEquals(
            setOf(PlannedAction.SHOW_TOP_OVERLAY, PlannedAction.DISMISS_SHADE),
            actions,
        )
    }

    @Test
    fun idleLockScreenOnlyCoversTheTopEdge() {
        assertEquals(setOf(PlannedAction.SHOW_TOP_OVERLAY), GuardPolicy.plan(armed, locked()))
    }

    @Test
    fun powerMenuPressesBackAndDoesNotBlankTheScreen() {
        val actions = GuardPolicy.plan(armed, locked(surfaces = setOf(SurfaceKind.POWER_MENU)))
        assertEquals(
            setOf(PlannedAction.SHOW_TOP_OVERLAY, PlannedAction.PRESS_BACK),
            actions,
        )
    }

    @Test
    fun emergencyCallIsNotDismissed() {
        val duringCall = locked(surfaces = setOf(SurfaceKind.SHADE, SurfaceKind.EMERGENCY))
        val actions = GuardPolicy.plan(armed, duringCall)
        assertTrue(PlannedAction.DISMISS_SHADE in actions)
        assertFalse(PlannedAction.PRESS_BACK in actions)
        val powerDuringCall = locked(surfaces = setOf(SurfaceKind.POWER_MENU, SurfaceKind.EMERGENCY))
        assertFalse(PlannedAction.PRESS_BACK in GuardPolicy.plan(armed, powerDuringCall))
    }

    @Test
    fun togglesGateEachSurface() {
        val both = locked(surfaces = setOf(SurfaceKind.SHADE, SurfaceKind.POWER_MENU))
        val qsOff = GuardPolicy.plan(armed.copy(blockQuickSettings = false), both)
        assertFalse(PlannedAction.DISMISS_SHADE in qsOff)
        assertFalse(PlannedAction.SHOW_TOP_OVERLAY in qsOff)
        assertTrue(PlannedAction.PRESS_BACK in qsOff)

        val powerOff = GuardPolicy.plan(armed.copy(blockPowerMenu = false), both)
        assertFalse(PlannedAction.PRESS_BACK in powerOff)
        assertTrue(PlannedAction.DISMISS_SHADE in powerOff)
    }

    private fun locked(
        deviceLocked: Boolean = true,
        interactive: Boolean = true,
        surfaces: Set<SurfaceKind> = emptySet(),
    ): DeviceSignals {
        return DeviceSignals(
            deviceLocked = deviceLocked,
            interactive = interactive,
            surfaces = surfaces,
        )
    }
}
