package com.whstudio.saferlockscreen.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemSettingsTargetsTest {
    @Test
    fun accessibilityButtonOpensThePublicList() {
        assertEquals("android.settings.ACCESSIBILITY_SETTINGS", SystemSettingsTargets.accessibilityAction())
        assertTrue(SystemSettingsTargets.isSafeToStart(SystemSettingsTargets.accessibilityAction()))
    }

    @Test
    fun serviceDetailsScreenIsNotStarted() {
        assertFalse(SystemSettingsTargets.isSafeToStart(SystemSettingsTargets.ACCESSIBILITY_DETAILS))
        assertFalse(SystemSettingsTargets.isSafeToStart("android.settings.ACCESSIBILITY_DETAILS_SETTINGS"))
        assertFalse(SystemSettingsTargets.isSafeToStart(null))
    }
}
