package com.whstudio.saferlockscreen.guard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SurfaceClassifierTest {
    @Test
    fun collapsedShadeWindowIsNotAPanel() {
        val window = systemUi(
            title = "NotificationShade",
            className = "com.android.systemui.shade.NotificationShadeWindowView",
            markers = listOf(
                ViewMarker(
                    viewId = "com.android.systemui:id/notification_stack_scroller",
                    className = "android.view.ViewGroup",
                    heightPx = 900,
                ),
            ),
        )
        assertNull(SurfaceClassifier.identify(window))
    }

    @Test
    fun quickSettingsPaneTitleIsShade() {
        val window = systemUi(paneTitles = listOf("Quick settings"))
        assertEquals(SurfaceKind.SHADE, SurfaceClassifier.identify(window))
    }

    @Test
    fun chineseQuickSettingsTitleIsShade() {
        val window = systemUi(title = "快捷设置")
        assertEquals(SurfaceKind.SHADE, SurfaceClassifier.identify(window))
    }

    @Test
    fun controlCenterTitleIsShade() {
        val window = systemUi(title = "Control center")
        assertEquals(SurfaceKind.SHADE, SurfaceClassifier.identify(window))
    }

    @Test
    fun visibleQsFrameIsShade() {
        val window = systemUi(
            markers = listOf(ViewMarker("com.android.systemui:id/qs_frame", "android.widget.FrameLayout", 720)),
        )
        assertEquals(SurfaceKind.SHADE, SurfaceClassifier.identify(window))
    }

    @Test
    fun tinyQsFrameIsIgnored() {
        val window = systemUi(
            markers = listOf(ViewMarker("com.android.systemui:id/qs_frame", "android.widget.FrameLayout", 10)),
        )
        assertNull(SurfaceClassifier.identify(window))
    }

    @Test
    fun quickSettingsSceneIsShadeWhenTall() {
        val window = systemUi(
            screenHeightPx = 2400,
            markers = listOf(
                ViewMarker("", "com.android.systemui.scene.QuickSettingsScene", 800),
            ),
        )
        assertEquals(SurfaceKind.SHADE, SurfaceClassifier.identify(window))
    }

    @Test
    fun shadeSceneShorterThanThresholdIsIgnored() {
        val window = systemUi(
            screenHeightPx = 2400,
            markers = listOf(ViewMarker("", "com.android.systemui.scene.ShadeScene", 100)),
        )
        assertNull(SurfaceClassifier.identify(window))
    }

    @Test
    fun notificationShadeAnnouncementOnTheEventIsShade() {
        val window = systemUi(eventTexts = listOf("Notification shade"))
        assertEquals(SurfaceKind.SHADE, SurfaceClassifier.identify(window))
    }

    @Test
    fun standingNotificationShadePhraseIsNotEnough() {
        val window = systemUi(title = "Notification shade")
        assertNull(SurfaceClassifier.identify(window))
    }

    @Test
    fun lockScreenTitleIsIgnored() {
        val window = systemUi(title = "Lock screen", className = "com.android.systemui.keyguard.KeyguardRootView")
        assertNull(SurfaceClassifier.identify(window))
    }

    @Test
    fun otherPackagesAreNotTheShade() {
        val window = WindowSnapshot(
            packageName = "com.google.android.googlequicksearchbox",
            className = "android.widget.FrameLayout",
            title = "Quick settings",
        )
        assertNull(SurfaceClassifier.identify(window))
    }

    @Test
    fun globalActionsClassIsPowerMenu() {
        val window = systemUi(
            className = "com.android.systemui.globalactions.GlobalActionsDialog\$ActionsDialog",
            eventClassName = "com.android.systemui.globalactions.GlobalActionsDialogLite",
        )
        assertEquals(SurfaceKind.POWER_MENU, SurfaceClassifier.identify(window))
    }

    @Test
    fun powerOffTitleIsPowerMenu() {
        val window = systemUi(title = "Power off")
        assertEquals(SurfaceKind.POWER_MENU, SurfaceClassifier.identify(window))
    }

    @Test
    fun chineseShutdownTitleIsPowerMenu() {
        val window = systemUi(eventTexts = listOf("关机"))
        assertEquals(SurfaceKind.POWER_MENU, SurfaceClassifier.identify(window))
    }

    @Test
    fun globalActionsViewIdIsPowerMenu() {
        val window = systemUi(
            markers = listOf(ViewMarker("com.android.systemui:id/global_actions_view", "android.view.View", 400)),
        )
        assertEquals(SurfaceKind.POWER_MENU, SurfaceClassifier.identify(window))
    }

    @Test
    fun powerMenuWinsOverEmergencyLabel() {
        val window = systemUi(
            className = "com.android.systemui.globalactions.GlobalActionsDialogLite",
            eventTexts = listOf("Emergency"),
        )
        assertEquals(SurfaceKind.POWER_MENU, SurfaceClassifier.identify(window))
    }

    @Test
    fun emergencyDialerPackageIsEmergency() {
        val window = WindowSnapshot(
            packageName = "com.android.phone",
            className = "com.android.phone.EmergencyDialer",
            title = "Emergency call",
        )
        assertEquals(SurfaceKind.EMERGENCY, SurfaceClassifier.identify(window))
    }

    @Test
    fun personalSafetyPackageIsEmergency() {
        val window = WindowSnapshot(
            packageName = "com.google.android.apps.safetyhub",
            className = "android.app.Activity",
            title = "",
        )
        assertEquals(SurfaceKind.EMERGENCY, SurfaceClassifier.identify(window))
    }

    @Test
    fun classifyKeepsShadeAndEmergencyTogether() {
        val kinds = SurfaceClassifier.classify(
            listOf(
                systemUi(paneTitles = listOf("Quick settings")),
                WindowSnapshot("com.android.phone", "com.android.phone.EmergencyDialer", "Emergency"),
            ),
        )
        assertEquals(setOf(SurfaceKind.SHADE, SurfaceKind.EMERGENCY), kinds)
    }

    @Test
    fun candidateFilterMatchesQsIdsOnly() {
        assertTrue(SurfaceClassifier.isCandidate("com.android.systemui:id/qs_frame", "android.view.View"))
        assertFalse(
            SurfaceClassifier.isCandidate(
                "com.android.systemui:id/notification_stack_scroller",
                "android.view.ViewGroup",
            ),
        )
        assertTrue(SurfaceClassifier.shouldWalk("com.android.systemui"))
        assertFalse(SurfaceClassifier.shouldWalk("com.google.android.apps.messaging"))
    }

    private fun systemUi(
        title: String = "NotificationShade",
        className: String = "com.android.systemui.shade.NotificationShadeWindowView",
        paneTitles: List<String> = emptyList(),
        eventTexts: List<String> = emptyList(),
        eventClassName: String = "",
        markers: List<ViewMarker> = emptyList(),
        screenHeightPx: Int = 2400,
    ): WindowSnapshot {
        return WindowSnapshot(
            packageName = SurfaceClassifier.SYSTEM_UI_PACKAGE,
            className = className,
            title = title,
            paneTitles = paneTitles,
            eventTexts = eventTexts,
            eventClassName = eventClassName,
            markers = markers,
            screenHeightPx = screenHeightPx,
        )
    }
}
