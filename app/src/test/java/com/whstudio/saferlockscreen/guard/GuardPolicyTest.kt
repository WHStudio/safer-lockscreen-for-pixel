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
        method = BlockMethod.AUTO,
    )

    @Test
    fun disabledOrUnlockedHidesOverlay() {
        val disabled = GuardPolicy.plan(armed.copy(protectionEnabled = false), locked())
        val unlocked = GuardPolicy.plan(armed, locked(deviceLocked = false))
        assertEquals(setOf(PlannedAction.HIDE_TOP_OVERLAY), disabled)
        assertEquals(setOf(PlannedAction.HIDE_TOP_OVERLAY), unlocked)
    }

    @Test
    fun autoShowsOverlayOnlyWhileInteractive() {
        assertTrue(PlannedAction.SHOW_TOP_OVERLAY in GuardPolicy.plan(armed, locked()))
        assertTrue(
            PlannedAction.HIDE_TOP_OVERLAY in GuardPolicy.plan(armed, locked(interactive = false)),
        )
        assertFalse(
            PlannedAction.SHOW_TOP_OVERLAY in GuardPolicy.plan(
                armed.copy(method = BlockMethod.DISMISS),
                locked(),
            ),
        )
    }

    @Test
    fun dismissMethodClosesShadeWithoutLocking() {
        val actions = GuardPolicy.plan(
            armed.copy(method = BlockMethod.DISMISS),
            locked(surfaces = setOf(SurfaceKind.SHADE)),
        )
        assertEquals(setOf(PlannedAction.HIDE_TOP_OVERLAY, PlannedAction.DISMISS_SHADE), actions)
        assertFalse(
            GuardPolicy.shouldEscalateShade(
                armed.copy(method = BlockMethod.DISMISS),
                locked(surfaces = setOf(SurfaceKind.SHADE)),
            ),
        )
    }

    @Test
    fun forceLockBlanksImmediatelyWhenAdminIsActive() {
        val actions = GuardPolicy.plan(
            armed.copy(method = BlockMethod.FORCE_LOCK),
            locked(surfaces = setOf(SurfaceKind.SHADE)),
        )
        assertEquals(
            setOf(PlannedAction.HIDE_TOP_OVERLAY, PlannedAction.DISMISS_SHADE, PlannedAction.LOCK_NOW),
            actions,
        )
    }

    @Test
    fun forceLockWithoutAdminOnlyDismisses() {
        val actions = GuardPolicy.plan(
            armed.copy(method = BlockMethod.FORCE_LOCK),
            locked(adminActive = false, surfaces = setOf(SurfaceKind.SHADE)),
        )
        assertFalse(PlannedAction.LOCK_NOW in actions)
        assertTrue(PlannedAction.DISMISS_SHADE in actions)
    }

    @Test
    fun autoDismissesThenEscalatesUnlessEmergencyIsOpen() {
        val shade = locked(surfaces = setOf(SurfaceKind.SHADE))
        val actions = GuardPolicy.plan(armed, shade)
        assertTrue(PlannedAction.DISMISS_SHADE in actions)
        assertFalse(PlannedAction.LOCK_NOW in actions)
        assertTrue(GuardPolicy.shouldEscalateShade(armed, shade))

        val duringCall = locked(surfaces = setOf(SurfaceKind.SHADE, SurfaceKind.EMERGENCY))
        val emergencyActions = GuardPolicy.plan(armed, duringCall)
        assertTrue(PlannedAction.DISMISS_SHADE in emergencyActions)
        assertFalse(PlannedAction.LOCK_NOW in emergencyActions)
        assertFalse(PlannedAction.PRESS_BACK in emergencyActions)
        assertFalse(GuardPolicy.shouldEscalateShade(armed, duringCall))
    }

    @Test
    fun powerMenuGoesBackAndLocksExceptDismissMethodAndEmergency() {
        val power = locked(surfaces = setOf(SurfaceKind.POWER_MENU))
        assertEquals(
            setOf(PlannedAction.SHOW_TOP_OVERLAY, PlannedAction.PRESS_BACK, PlannedAction.LOCK_NOW),
            GuardPolicy.plan(armed, power),
        )
        assertEquals(
            setOf(PlannedAction.HIDE_TOP_OVERLAY, PlannedAction.PRESS_BACK),
            GuardPolicy.plan(armed.copy(method = BlockMethod.DISMISS), power),
        )
        val emergency = locked(surfaces = setOf(SurfaceKind.POWER_MENU, SurfaceKind.EMERGENCY))
        val actions = GuardPolicy.plan(armed, emergency)
        assertFalse(PlannedAction.PRESS_BACK in actions)
        assertFalse(PlannedAction.LOCK_NOW in actions)
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
        adminActive: Boolean = true,
        surfaces: Set<SurfaceKind> = emptySet(),
    ): DeviceSignals {
        return DeviceSignals(
            deviceLocked = deviceLocked,
            interactive = interactive,
            adminActive = adminActive,
            surfaces = surfaces,
        )
    }
}
