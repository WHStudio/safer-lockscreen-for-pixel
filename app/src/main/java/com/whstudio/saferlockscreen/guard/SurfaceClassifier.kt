package com.whstudio.saferlockscreen.guard

/**
 * Decides whether a window is an expanded lock-screen shade, the power menu, or an
 * emergency UI.
 *
 * The System UI shade window titled "NotificationShade" exists for the whole time the
 * keyguard is showing, including while it is collapsed. Its mere presence is not "open".
 * Lock-screen notifications also use the same window, so a visible notification list is
 * not treated as Quick Settings.
 */
internal object SurfaceClassifier {
    const val SYSTEM_UI_PACKAGE = "com.android.systemui"

    val EMERGENCY_PACKAGES = setOf(
        "com.android.phone",
        "com.android.emergency",
        "com.google.android.apps.safetyhub",
    )

    fun shouldWalk(packageName: String): Boolean {
        return packageName == SYSTEM_UI_PACKAGE || packageName in EMERGENCY_PACKAGES
    }

    fun isCandidate(viewId: String, className: String): Boolean {
        val id = viewId.lowercase()
        val cls = className.lowercase()
        return INTERESTING_ID_PARTS.any { id.contains(it) } ||
            INTERESTING_CLASS_PARTS.any { cls.contains(it) }
    }

    fun classify(windows: List<WindowSnapshot>): Set<SurfaceKind> {
        return windows.mapNotNull(::identify).toSet()
    }

    fun identify(window: WindowSnapshot): SurfaceKind? {
        val power = isPowerMenu(window)
        if (isEmergency(window) && !power) return SurfaceKind.EMERGENCY
        if (power) return SurfaceKind.POWER_MENU
        if (isExpandedShade(window)) return SurfaceKind.SHADE
        return null
    }

    private fun isEmergency(window: WindowSnapshot): Boolean {
        val pkg = window.packageName.lowercase()
        if (pkg in EMERGENCY_PACKAGES) return true
        val classNames = listOf(window.className, window.eventClassName).map(::normalize)
        if (classNames.any { name -> EMERGENCY_CLASS_PARTS.any { name.contains(it) } }) {
            return true
        }
        return titles(window, includeEventText = true).any { it in EMERGENCY_TITLES }
    }

    private fun isPowerMenu(window: WindowSnapshot): Boolean {
        if (!isSystemUi(window)) return false
        val classNames = listOf(window.className, window.eventClassName).map(::normalize)
        if (classNames.any { name -> POWER_CLASS_PARTS.any { name.contains(it) } }) {
            return true
        }
        if (window.markers.any { marker ->
                val id = marker.viewId.lowercase()
                val cls = marker.className.lowercase()
                POWER_ID_PARTS.any { id.contains(it) } || POWER_CLASS_PARTS.any { cls.contains(it) }
            }
        ) {
            return true
        }
        // Button labels ("Power off", "Restart", "关机") are not enough. They can be event
        // text on the lock screen and must not be treated as the power menu.
        return normalize(window.title) in POWER_MENU_TITLES
    }

    private fun isExpandedShade(window: WindowSnapshot): Boolean {
        if (!isSystemUi(window) || isPowerMenu(window)) return false
        // The shade window, its "Notification shade" announcement, and ShadeScene stay up
        // for the whole time the keyguard is showing. None of those mean the panel is open.
        val minHeight = expandedMinHeight(window.screenHeightPx)
        if (window.visiblePanes.any { pane ->
                pane.heightPx >= minHeight && normalize(pane.title) in QS_TITLES
            }
        ) {
            return true
        }
        return window.markers.any { markerIndicatesShade(it, minHeight) }
    }

    private fun markerIndicatesShade(marker: ViewMarker, minHeight: Int): Boolean {
        if (marker.heightPx < minHeight) return false
        val id = marker.viewId.lowercase()
        if (QS_VIEW_ID_PARTS.any { id.contains(it) }) return true
        val cls = marker.className
        if (QS_CLASS_EXCLUDES.any { cls.contains(it, ignoreCase = true) }) return false
        if (AMBIGUOUS_SHADE_CLASSES.any { cls.contains(it) }) return false
        return QS_CLASS_PARTS.any { cls.contains(it) }
    }

    private fun expandedMinHeight(screenHeightPx: Int): Int {
        return if (screenHeightPx > 0) {
            maxOf(ABSOLUTE_MIN_EXPANDED_PX, (screenHeightPx * EXPANDED_HEIGHT_FRACTION).toInt())
        } else {
            ABSOLUTE_MIN_EXPANDED_PX
        }
    }

    private fun isSystemUi(window: WindowSnapshot): Boolean {
        return window.packageName == SYSTEM_UI_PACKAGE
    }

    private fun titles(window: WindowSnapshot, includeEventText: Boolean): List<String> {
        val raw = buildList {
            add(window.title)
            addAll(window.visiblePanes.map { it.title })
            if (includeEventText) addAll(window.eventTexts)
        }
        return raw.map(::normalize).filter { it.isNotEmpty() && it.length <= 48 }
    }

    private fun normalize(value: String): String {
        return value.trim().lowercase().replace(Regex("\\s+"), " ")
    }

    private val QS_TITLES = setOf(
        "quick settings",
        "quick_settings",
        "quicksettings",
        "control center",
        "quick panel",
        "快捷设置",
        "控制中心",
        "快速设置",
    )

    /** Window title of the global-actions dialog itself, not the labels of its buttons. */
    private val POWER_MENU_TITLES = setOf(
        "power menu",
        "电源菜单",
    )

    private val EMERGENCY_TITLES = setOf(
        "emergency",
        "emergency call",
        "emergency calling",
        "sos",
        "紧急呼叫",
        "紧急呼救",
        "紧急求救",
    )

    private val POWER_CLASS_PARTS = listOf(
        "globalactions",
        "global_actions",
        "powermenu",
        "power_menu",
        "shutdownui",
    )

    private val POWER_ID_PARTS = listOf(
        "global_actions",
        "globalactions",
        "power_menu",
        "powermenu",
    )

    private val EMERGENCY_CLASS_PARTS = listOf(
        "emergencydialer",
        "emergency_dialer",
        "emergencyinfo",
        "emergencycall",
        "sosactivity",
    )

    /**
     * Ids that belong to the expanded Quick Settings hierarchy and are not part of the
     * idle keyguard. The notification list id is intentionally absent: the lock screen
     * shows notifications without opening Quick Settings.
     */
    private val QS_VIEW_ID_PARTS = listOf(
        "qs_frame",
        "quick_settings_panel",
        "expanded_qs_scroll_view",
        "qs_pager",
        "quick_qs_panel",
        "qqs_tile_layout",
        "qs_container",
        "quick_settings_container",
        "qs_footer_actions",
        "quick_settings_shade",
    )

    private val QS_CLASS_PARTS = listOf(
        "QSContainerImpl",
        "QuickQSPanel",
        "QuickSettingsScene",
        "NotificationsQuickSettings",
        "QuickSettingsShade",
        "QuickSettingsContainer",
    )

    /**
     * These class names stay in the System UI window while the lock screen is showing,
     * including when the shade is collapsed. Matching them blanked the screen on wake.
     */
    private val AMBIGUOUS_SHADE_CLASSES = listOf(
        "ShadeScene",
        "NotificationShadeWindow",
        "SceneWindowRoot",
        "Keyguard",
        "Bouncer",
    )

    private val QS_CLASS_EXCLUDES = listOf(
        "NotificationShadeWindow",
        "SceneWindowRoot",
        "Keyguard",
        "StatusBar",
        "Bouncer",
    )

    private val INTERESTING_ID_PARTS = QS_VIEW_ID_PARTS + POWER_ID_PARTS

    private val INTERESTING_CLASS_PARTS = listOf(
        "qscontainer",
        "quickqspanel",
        "quicksettingsscene",
        "quicksettingscontainer",
        "notificationsquicksettings",
        "quicksettingsshade",
        "globalactions",
        "powermenu",
        "shutdownui",
    )

    private const val ABSOLUTE_MIN_EXPANDED_PX = 640
    private const val EXPANDED_HEIGHT_FRACTION = 0.45f
}
