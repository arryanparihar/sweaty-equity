package com.sweatyequity

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * FocusAccessibilityService — the Enforcer.
 *
 * Listens for TYPE_WINDOW_STATE_CHANGED events. When the foreground app is
 * one of the user-configured blocked apps, it immediately launches
 * WorkoutOverlayActivity so the user must complete a workout to proceed.
 *
 * Performance notes:
 *  • Only TYPE_WINDOW_STATE_CHANGED is requested; no node-tree traversal.
 *  • Allowed packages are short-circuited first to prevent any lag.
 *  • If a 15-minute unlock window is active the service returns immediately.
 */
class FocusAccessibilityService : AccessibilityService() {

    companion object {
        /**
         * Packages that must NEVER trigger the enforcer.
         * Combines the launcher's allowed list with system-UI packages.
         */
        private val SAFE_PACKAGES: Set<String> =
            HomeActivity.ALLOWED_PACKAGES.map { it.first }.toHashSet() + setOf(
                "com.sweatyequity",
                "com.android.systemui",
                "com.google.android.inputmethod.latin",
                "com.samsung.android.honeyboard",
                "android"
            )
    }

    // ─── Service setup ────────────────────────────────────────────────────────

    override fun onServiceConnected() {
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
        }
    }

    // ─── Event handling ───────────────────────────────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return

        // Fast path: ignore safe packages immediately to avoid any lag.
        if (pkg in SAFE_PACKAGES) return

        // If the user already earned a 15-minute unlock, do nothing.
        if (WorkoutOverlayActivity.isUnlockWindowActive(this)) return

        // Block distracting apps.
        if (pkg in AppPreferences.getBlockedApps(this)) {
            AppPreferences.incrementBlockCount(this, pkg)
            AppPreferences.appendUsageLog(this, "blocked", pkg, "workout_required")
            val intent = Intent(this, WorkoutOverlayActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("blocked_package", pkg)
            }
            startActivity(intent)
        }
    }

    override fun onInterrupt() {
        // No-op: required by AccessibilityService contract.
    }
}
