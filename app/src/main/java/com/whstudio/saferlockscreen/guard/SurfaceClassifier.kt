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
        return titles(window, includeEventText = true).any { it in POWER_TITLES }
    }

    private fun isExpandedShade(window: WindowSnapshot): Boolean {
        if (!isSystemUi(window) || isPowerMenu(window)) return false
        // Standing window title "NotificationShade" is ignored. User-facing Quick Settings
        // titles and pane titles are only expected once that scene is actually showing.
        if (paneAndWindowTitles(window).any { it in QS_TITLES }) return true
        if (window.eventTexts.map(::normalize).any { it in QS_TITLES || it in EVENT_SHADE_TITLES }) {
            return true
        }
        return window.markers.any { markerIndicatesShade(it, window.screenHeightPx) }
    }

    private fun markerIndicatesShade(marker: ViewMarker, screenHeightPx: Int): Boolean {
        val id = marker.viewId.lowercase()
        if (QS_VIEW_ID_PARTS.any { id.contains(it) }) {
            return marker.heightPx >= QS_ID_MIN_HEIGHT_PX
        }
        val cls = marker.className
        if (QS_CLASS_EXCLUDES.any { cls.contains(it, ignoreCase = true) }) return false
        if (QS_CLASS_PARTS.none { cls.contains(it) }) return false
        val minHeight = if (screenHeightPx > 0) {
            maxOf(QS_CLASS_MIN_HEIGHT_PX, (screenHeightPx * 0.18f).toInt())
        } else {
            QS_CLASS_MIN_HEIGHT_PX
        }
        return marker.heightPx >= minHeight
    }

    private fun isSystemUi(window: WindowSnapshot): Boolean {
        return window.packageName == SYSTEM_UI_PACKAGE
    }

    private fun paneAndWindowTitles(window: WindowSnapshot): List<String> {
        return titles(window, includeEventText = false)
    }

    private fun titles(window: WindowSnapshot, includeEventText: Boolean): List<String> {
        val raw = buildList {
            add(window.title)
            addAll(window.paneTitles)
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

    /** Announced when the shade opens. Not trusted as a permanent window title. */
    private val EVENT_SHADE_TITLES = setOf(
        "notification shade",
        "通知栏",
        "通知栏面板",
    )

    private val POWER_TITLES = setOf(
        "power menu",
        "power off",
        "shutdown",
        "restart",
        "reboot",
        "power options",
        "电源菜单",
        "关机",
        "重新启动",
        "关闭电源",
        "电源选项",
        "重启",
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
        "ShadeScene",
        "NotificationsQuickSettings",
        "QuickSettingsShade",
        "QuickSettingsContainer",
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
        "shadescene",
        "quicksettingscontainer",
        "notificationsquicksettings",
        "quicksettingsshade",
        "globalactions",
        "powermenu",
        "shutdownui",
    )

    private const val QS_ID_MIN_HEIGHT_PX = 48
    private const val QS_CLASS_MIN_HEIGHT_PX = 160
}
