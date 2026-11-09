package com.whstudio.saferlockscreen.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupWarningTest {
    @Test
    fun warningShowsUntilItHasBeenAcknowledged() {
        assertTrue(shouldShowStartupWarning(acknowledged = false))
        assertFalse(shouldShowStartupWarning(acknowledged = true))
    }
}
