package com.whstudio.saferlockscreen.service

import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.whstudio.saferlockscreen.guard.PaneSight
import com.whstudio.saferlockscreen.guard.SurfaceClassifier
import com.whstudio.saferlockscreen.guard.ViewMarker
import com.whstudio.saferlockscreen.guard.WindowSnapshot

/**
 * Reads window identity only: package, class, title, pane title, and view id.
 * Node text and content descriptions are notification and tile contents, so they are never read.
 */
internal object WindowSnapshots {
    private const val MAX_DEPTH = 14
    private const val MAX_NODES = 500
    private const val MAX_EVENT_TEXT = 40

    fun capture(
        windows: List<AccessibilityWindowInfo>,
        event: AccessibilityEvent?,
        screenHeightPx: Int,
    ): List<WindowSnapshot> {
        val hints = eventHints(event)
        return windows.map { window -> captureWindow(window, hints, screenHeightPx) }
    }

    private fun captureWindow(
        window: AccessibilityWindowInfo,
        hints: EventHints?,
        screenHeightPx: Int,
    ): WindowSnapshot {
        val title = window.title?.toString().orEmpty()
        val root = window.root
        if (root == null) {
            return WindowSnapshot(packageName = "", className = "", title = title, screenHeightPx = screenHeightPx)
        }
        val packageName = root.packageName?.toString().orEmpty()
        val className = root.className?.toString().orEmpty()
        val panes = mutableListOf<PaneSight>()
        val markers = mutableListOf<ViewMarker>()
        if (SurfaceClassifier.shouldWalk(packageName)) {
            val state = WalkState()
            walk(root, depth = 0, state = state, panes = panes, markers = markers)
        }
        root.releaseNode()
        val forThisWindow = hints != null && hints.windowId == window.id
        return WindowSnapshot(
            packageName = packageName,
            className = className,
            title = title,
            visiblePanes = panes,
            eventTexts = if (forThisWindow) hints.texts else emptyList(),
            eventClassName = if (forThisWindow) hints.className else "",
            markers = markers,
            screenHeightPx = screenHeightPx,
        )
    }

    private fun walk(
        node: AccessibilityNodeInfo,
        depth: Int,
        state: WalkState,
        panes: MutableList<PaneSight>,
        markers: MutableList<ViewMarker>,
    ) {
        if (depth > MAX_DEPTH || state.count >= MAX_NODES) return
        state.count++
        val viewId = node.viewIdResourceName.orEmpty()
        val className = node.className?.toString().orEmpty()
        // Hidden scenes stay in the shade window while the keyguard is showing. A pane
        // title counts only when that node is actually on screen.
        if (node.isVisibleToUser) {
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            val paneTitle = node.paneTitle?.toString()
            if (!paneTitle.isNullOrBlank()) {
                panes += PaneSight(title = paneTitle, heightPx = bounds.height())
            }
            if (SurfaceClassifier.isCandidate(viewId, className)) {
                markers += ViewMarker(viewId = viewId, className = className, heightPx = bounds.height())
            }
        }
        if (depth == MAX_DEPTH || state.count >= MAX_NODES) return
        for (index in 0 until node.childCount) {
            if (state.count >= MAX_NODES) return
            val child = node.getChild(index) ?: continue
            walk(child, depth + 1, state, panes, markers)
            child.releaseNode()
        }
    }

    private fun eventHints(event: AccessibilityEvent?): EventHints? {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return null
        }
        val texts = event.text.mapNotNull { it?.toString() }
            .filter { it.length in 1..MAX_EVENT_TEXT && '\n' !in it }
        return EventHints(
            windowId = event.windowId,
            className = event.className?.toString().orEmpty(),
            texts = texts,
        )
    }

    private fun AccessibilityNodeInfo.releaseNode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            @Suppress("DEPRECATION")
            recycle()
        }
    }

    private class WalkState {
        var count: Int = 0
    }

    private data class EventHints(
        val windowId: Int,
        val className: String,
        val texts: List<String>,
    )
}
