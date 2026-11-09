package com.whstudio.saferlockscreen.guard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockObservationTest {
    @Test
    fun usesOnlyPublicBroadcasts() {
        assertEquals(
            listOf(
                "android.intent.action.SCREEN_ON",
                "android.intent.action.SCREEN_OFF",
                "android.intent.action.USER_PRESENT",
                "android.intent.action.USER_UNLOCKED",
            ),
            LockObservation.BROADCASTS,
        )
    }

    @Test
    fun guardsOnlyWhileProtectionIsOnAndAuthenticationIsStillRequired() {
        assertTrue(LockObservation.shouldGuard(protectionEnabled = true, deviceLocked = true, interactive = true))
        assertFalse(LockObservation.shouldGuard(protectionEnabled = true, deviceLocked = false, interactive = true))
        assertFalse(LockObservation.shouldGuard(protectionEnabled = true, deviceLocked = true, interactive = false))
        assertFalse(LockObservation.shouldGuard(protectionEnabled = false, deviceLocked = true, interactive = true))
    }

    @Test
    fun interactivePollIsARealInterval() {
        assertTrue(LockObservation.INTERACTIVE_POLL_MS in 200L..5_000L)
    }
}
