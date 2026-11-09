package com.whstudio.saferlockscreen.guard

/**
 * How a locked device reacts when Quick Settings, the shade, or the power menu appears.
 *
 * [AUTO] is the Pixel default: stop the swipe, dismiss anything that still opens, then
 * force-lock if the panel stays up. [DISMISS] only closes the panel. [FORCE_LOCK] blanks
 * the screen with [android.app.admin.DevicePolicyManager.lockNow].
 */
enum class BlockMethod(val id: String) {
    AUTO("auto"),
    DISMISS("dismiss"),
    FORCE_LOCK("force_lock");

    companion object {
        fun fromId(id: String?): BlockMethod = entries.firstOrNull { it.id == id } ?: AUTO
    }
}

internal data class GuardSettings(
    val protectionEnabled: Boolean,
    val blockQuickSettings: Boolean,
    val blockPowerMenu: Boolean,
    val method: BlockMethod,
) {
    companion object {
        val DEFAULT = GuardSettings(
            protectionEnabled = false,
            blockQuickSettings = true,
            blockPowerMenu = true,
            method = BlockMethod.AUTO,
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

/**
 * Identity of one window. Callers must not put notification body text in here.
 * [eventTexts] is only the short window-state title from a state-changed event.
 */
internal data class WindowSnapshot(
    val packageName: String,
    val className: String,
    val title: String,
    val paneTitles: List<String> = emptyList(),
    val eventTexts: List<String> = emptyList(),
    val eventClassName: String = "",
    val markers: List<ViewMarker> = emptyList(),
    val screenHeightPx: Int = 0,
)

internal data class DeviceSignals(
    val deviceLocked: Boolean,
    val interactive: Boolean,
    val adminActive: Boolean,
    val surfaces: Set<SurfaceKind>,
)

internal enum class PlannedAction {
    SHOW_TOP_OVERLAY,
    HIDE_TOP_OVERLAY,
    DISMISS_SHADE,
    PRESS_BACK,
    LOCK_NOW,
}
