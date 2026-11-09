package com.whstudio.saferlockscreen.service

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * Transparent strip over the status-bar swipe region. Touches that start here are consumed,
 * so the shade gesture never reaches System UI. The rest of the lock screen is uncovered.
 *
 * Window alpha stays at 1. A window alpha of 0 can drop input on some builds. The view itself
 * draws nothing.
 */
internal class TopEdgeOverlay(
    private val service: AccessibilityService,
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var view: View? = null
    private var lastFailureAt = 0L

    val isShowing: Boolean
        get() = view != null

    fun show() {
        if (view != null) {
            updateHeight()
            return
        }
        val now = SystemClock.uptimeMillis()
        if (now - lastFailureAt < FAILURE_BACKOFF_MS) return
        val created = View(service).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            isClickable = true
            setOnTouchListener { _, _ -> true }
        }
        val params = layoutParams(desiredHeight())
        try {
            windowManager.addView(created, params)
            view = created
        } catch (error: RuntimeException) {
            lastFailureAt = now
            Log.w(TAG, "Top-edge overlay was not added: ${error.javaClass.simpleName}")
        }
    }

    fun hide() {
        val current = view ?: return
        view = null
        try {
            windowManager.removeView(current)
        } catch (error: RuntimeException) {
            Log.w(TAG, "Top-edge overlay was not removed: ${error.javaClass.simpleName}")
        }
    }

    private fun updateHeight() {
        val current = view ?: return
        val params = current.layoutParams as? WindowManager.LayoutParams ?: return
        val height = desiredHeight()
        if (params.height == height) return
        params.height = height
        try {
            windowManager.updateViewLayout(current, params)
        } catch (error: RuntimeException) {
            Log.w(TAG, "Top-edge overlay was not updated: ${error.javaClass.simpleName}")
        }
    }

    private fun desiredHeight(): Int {
        val density = service.resources.displayMetrics.density
        val statusId = service.resources.getIdentifier("status_bar_height", "dimen", "android")
        val status = if (statusId > 0) {
            service.resources.getDimensionPixelSize(statusId)
        } else {
            (24f * density).toInt()
        }
        val extra = (32f * density).toInt()
        val min = (48f * density).toInt()
        val max = (96f * density).toInt()
        return (status + extra).coerceIn(min, max)
    }

    private fun layoutParams(height: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
            fitInsetsSides = 0
            title = "SaferLockscreenTopEdge"
        }
    }

    private companion object {
        const val TAG = "SaferLockscreen"
        const val FAILURE_BACKOFF_MS = 2_000L
    }
}
