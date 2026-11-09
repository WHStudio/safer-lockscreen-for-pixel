package com.whstudio.saferlockscreen.guard

/**
 * Protection settings. There is one behavior: cover the top-edge swipe and dismiss a panel
 * that is actually open. The app does not turn the screen off.
 */
internal data class GuardSettings(
    val protectionEnabled: Boolean,
    val blockQuickSettings: Boolean,
    val blockPowerMenu: Boolean,
) {
    companion object {
        val DEFAULT = GuardSettings(
            protectionEnabled = false,
            blockQuickSettings = true,
            blockPowerMenu = true,
        )
    }
}

internal enum class SurfaceKind {
    SHADE,
    POWER_MENU,
    EMERGENCY,
}

internal data class ViewMarker(
    val viewId: String,
    val className: String,
    val heightPx: Int,
)

/** A pane title that was visible, with the height of that node on screen. */
internal data class PaneSight(
    val title: String,
    val heightPx: Int,
)

/**
 * Identity of one window. Callers must not put notification body text in here.
 * [eventTexts] is only the short window-state title from a state-changed event.
 */
internal data class WindowSnapshot(
    val packageName: String,
    val className: String,
    val title: String,
    val visiblePanes: List<PaneSight> = emptyList(),
    val eventTexts: List<String> = emptyList(),
    val eventClassName: String = "",
    val markers: List<ViewMarker> = emptyList(),
    val screenHeightPx: Int = 0,
)

internal data class DeviceSignals(
    val deviceLocked: Boolean,
    val interactive: Boolean,
    val surfaces: Set<SurfaceKind>,
)

internal enum class PlannedAction {
    SHOW_TOP_OVERLAY,
    HIDE_TOP_OVERLAY,
    DISMISS_SHADE,
    PRESS_BACK,
}
