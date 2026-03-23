package com.sweatyequity

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity

/**
 * HomeActivity — the minimalist launcher.
 *
 * Pure-black screen that lists only the hardcoded set of "Allowed" apps.
 * No icons, no wallpaper. On first launch it prompts the user to exempt
 * the app from battery optimisation so background services stay alive.
 */
class HomeActivity : AppCompatActivity() {

    companion object {
        /**
         * Packages the user is always permitted to open without a workout.
         * The list is intentionally small and curated.
         */
        val ALLOWED_PACKAGES: List<Pair<String, String>> = listOf(
            "com.google.android.dialer"            to "Phone",
            "com.android.dialer"                   to "Phone",
            "com.google.android.apps.messaging"    to "Messages",
            "com.android.mms"                      to "Messages",
            "com.google.android.apps.maps"         to "Maps",
            "com.google.android.calculator"        to "Calculator",
            "com.android.calculator2"              to "Calculator",
            "com.android.settings"                 to "Settings",
            "com.google.android.deskclock"         to "Clock",
            "com.android.deskclock"                to "Clock",
            "com.google.android.calendar"          to "Calendar",
            "com.android.calendar"                 to "Calendar",
            "com.google.android.contacts"          to "Contacts",
            "com.android.contacts"                 to "Contacts",
            "com.google.android.apps.photos"       to "Photos",
            "com.android.camera2"                  to "Camera",
            "com.google.android.GoogleCamera"      to "Camera"
        )
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestIgnoreBatteryOptimizations()
        buildUI()
    }

    override fun onResume() {
        super.onResume()
        // Rebuild the list every time we return so the installed state is fresh.
        buildUI()
    }

    // ─── UI ───────────────────────────────────────────────────────────────────

    private fun buildUI() {
        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 96, 56, 96)
        }

        // App title
        layout.addView(makeText("SWEATY EQUITY", 20f, Color.WHITE).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8)
        })
        layout.addView(makeText("no more reels lol", 12f, Color.DKGRAY).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 56)
        })
        layout.addView(makeText("→  Sweaty Settings", 15f, ContextCompat.getColor(this, android.R.color.holo_blue_light)).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 40)
            setOnClickListener {
                startActivity(Intent(this@HomeActivity, SettingsActivity::class.java))
            }
        })

        // Divider
        layout.addView(View(this).apply {
            setBackgroundColor(Color.DKGRAY)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).also { it.bottomMargin = 40 }
        })

        // Deduplicate by display label — show only the first installed package
        val pm = packageManager
        val seen = mutableSetOf<String>()
        ALLOWED_PACKAGES.forEach { (pkg, label) ->
            if (!seen.contains(label) && isInstalled(pkg, pm)) {
                seen.add(label)
                layout.addView(makeAppRow(label, pkg))
            }
        }

        scrollView.addView(layout)
        setContentView(scrollView)
    }

    private fun makeAppRow(label: String, packageName: String): View =
        makeText("→  $label", 16f, Color.WHITE).apply {
            setPadding(0, 28, 0, 28)
            setOnClickListener { launchApp(packageName) }
        }

    private fun makeText(text: String, sizeSp: Float, color: Int): TextView =
        TextView(this).apply {
            this.text = text
            setTextColor(color)
            textSize = sizeSp
            typeface = Typeface.MONOSPACE
        }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun isInstalled(packageName: String, pm: PackageManager): Boolean =
        try { pm.getPackageInfo(packageName, 0); true }
        catch (_: PackageManager.NameNotFoundException) { false }

    private fun launchApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        startActivity(intent)
    }

    /**
     * Prompt the user to whitelist this app from Doze / battery optimisation
     * so that the FocusAccessibilityService remains alive indefinitely.
     */
    private fun requestIgnoreBatteryOptimizations() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }
}
