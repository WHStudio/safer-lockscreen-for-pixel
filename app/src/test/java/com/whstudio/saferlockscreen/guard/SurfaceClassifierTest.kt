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
    fun expandedQuickSettingsPaneIsShade() {
        val window = systemUi(visiblePanes = listOf(PaneSight("Quick settings", 1600)))
        assertEquals(SurfaceKind.SHADE, SurfaceClassifier.identify(window))
    }

    @Test
    fun quickSettingsTitleWithoutAnExpandedPaneIsNotShade() {
        val window = systemUi(title = "快捷设置", visiblePanes = listOf(PaneSight("Quick settings", 120)))
        assertNull(SurfaceClassifier.identify(window))
    }

    @Test
    fun expandedControlCenterPaneIsShade() {
        val window = systemUi(visiblePanes = listOf(PaneSight("Control center", 1500)))
        assertEquals(SurfaceKind.SHADE, SurfaceClassifier.identify(window))
    }

    @Test
    fun expandedQsFrameIsShade() {
        val window = systemUi(
            markers = listOf(ViewMarker("com.android.systemui:id/qs_frame", "android.widget.FrameLayout", 1600)),
        )
        assertEquals(SurfaceKind.SHADE, SurfaceClassifier.identify(window))
    }

    @Test
    fun qsFrameShorterThanTheExpandedPanelIsIgnored() {
        val window = systemUi(
            markers = listOf(ViewMarker("com.android.systemui:id/qs_frame", "android.widget.FrameLayout", 720)),
        )
        assertNull(SurfaceClassifier.identify(window))
    }

    @Test
    fun quickSettingsSceneIsShadeWhenItCoversTheScreen() {
        val window = systemUi(
            screenHeightPx = 2400,
            markers = listOf(
                ViewMarker("", "com.android.systemui.scene.QuickSettingsScene", 1600),
            ),
        )
        assertEquals(SurfaceKind.SHADE, SurfaceClassifier.identify(window))
    }

    @Test
    fun shadeSceneIsTheLockScreenContainerAndIsNotAPanel() {
        val window = systemUi(
            screenHeightPx = 2400,
            markers = listOf(ViewMarker("", "com.android.systemui.scene.ShadeScene", 2200)),
        )
        assertNull(SurfaceClassifier.identify(window))
    }

    @Test
    fun notificationShadeAnnouncementOnWakeIsNotAnOpenPanel() {
        val window = systemUi(eventTexts = listOf("Notification shade"))
        assertNull(SurfaceClassifier.identify(window))
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
    fun powerButtonLabelsOnTheLockScreenAreNotThePowerMenu() {
        val window = systemUi(title = "Lock screen", eventTexts = listOf("Power off", "Restart", "关机"))
        assertNull(SurfaceClassifier.identify(window))
    }

    @Test
    fun powerMenuWindowTitleIsPowerMenu() {
        val window = systemUi(title = "Power menu")
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
                systemUi(visiblePanes = listOf(PaneSight("Quick settings", 1600))),
                WindowSnapshot("com.android.phone", "com.android.phone.EmergencyDialer", "Emergency"),
            ),
        )
        assertEquals(setOf(SurfaceKind.SHADE, SurfaceKind.EMERGENCY), kinds)
    }

    @Test
    fun lockScreenAppearanceDoesNotForceLock() {
        val window = systemUi(
            title = "Lock screen",
            className = "com.android.systemui.shade.NotificationShadeWindowView",
            eventTexts = listOf("Notification shade", "Power off", "关机"),
            markers = listOf(
                ViewMarker("", "com.android.systemui.scene.ShadeScene", 2200),
                ViewMarker("com.android.systemui:id/qs_frame", "android.widget.FrameLayout", 200),
                ViewMarker("com.android.systemui:id/notification_stack_scroller", "android.view.ViewGroup", 900),
            ),
            visiblePanes = listOf(PaneSight("Lock screen", 2200), PaneSight("Quick settings", 80)),
        )
        assertNull(SurfaceClassifier.identify(window))
        val settings = GuardSettings(
            protectionEnabled = true,
            blockQuickSettings = true,
            blockPowerMenu = true,
            method = BlockMethod.AUTO,
        )
        val signals = DeviceSignals(
            deviceLocked = true,
            interactive = true,
            adminActive = true,
            surfaces = SurfaceClassifier.classify(listOf(window)),
        )
        val actions = GuardPolicy.plan(settings, signals)
        assertFalse(PlannedAction.LOCK_NOW in actions)
        assertFalse(PlannedAction.DISMISS_SHADE in actions)
        assertFalse(PlannedAction.PRESS_BACK in actions)
        assertFalse(GuardPolicy.shouldEscalateShade(settings, signals))
        assertTrue(PlannedAction.SHOW_TOP_OVERLAY in actions)
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
        visiblePanes: List<PaneSight> = emptyList(),
        eventTexts: List<String> = emptyList(),
        eventClassName: String = "",
        markers: List<ViewMarker> = emptyList(),
        screenHeightPx: Int = 2400,
    ): WindowSnapshot {
        return WindowSnapshot(
            packageName = SurfaceClassifier.SYSTEM_UI_PACKAGE,
            className = className,
            title = title,
            visiblePanes = visiblePanes,
            eventTexts = eventTexts,
            eventClassName = eventClassName,
            markers = markers,
            screenHeightPx = screenHeightPx,
        )
    }
}
