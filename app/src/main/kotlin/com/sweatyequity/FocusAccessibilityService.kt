package com.sweatyequity

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * FocusAccessibilityService — the Enforcer.
 *
 * Listens for TYPE_WINDOW_STATE_CHANGED events. When the foreground app is
 * one of the DISTRACTING_PACKAGES, it immediately launches
 * WorkoutOverlayActivity so the user must complete a workout to proceed.
 *
 * Performance notes:
 *  • Only TYPE_WINDOW_STATE_CHANGED is requested; no node-tree traversal.
 *  • Allowed packages are short-circuited first to prevent any lag.
 *  • If a 15-minute unlock window is active the service returns immediately.
 */
class FocusAccessibilityService : AccessibilityService() {

    companion object {
        /** Apps that require a workout to access. */
        val DISTRACTING_PACKAGES: Set<String> = setOf(
            "com.instagram.android",
            "com.google.android.youtube",
            "com.android.vending",          // Play Store
            "com.twitter.android",
            "com.facebook.katana",
            "com.facebook.lite",
            "com.snapchat.android",
            "com.zhiliaoapp.musically",     // TikTok
            "com.reddit.frontpage",
            "com.netflix.mediaclient",
            "com.linkedin.android",
            "com.pinterest",
            "com.tumblr",
            "com.discord"
        )

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
        if (pkg in DISTRACTING_PACKAGES) {
            val intent = Intent(this, WorkoutOverlayActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(intent)
        }
    }

    override fun onInterrupt() {
        // No-op: required by AccessibilityService contract.
    }
}
