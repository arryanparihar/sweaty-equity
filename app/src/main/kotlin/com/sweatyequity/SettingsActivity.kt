package com.sweatyequity

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        buildUi()
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.BLACK) }
        val root = vstack(48)

        root.addView(label("SWEATY SETTINGS", 20f, Color.WHITE).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        })

        val workouts = AppPreferences.getWorkoutsCompleted(this)
        val streak = AppPreferences.getStreakCount(this)
        root.addView(label("Workouts completed: $workouts", 14f, Color.WHITE))
        root.addView(label("Current streak: $streak day(s)", 14f, Color.WHITE).apply { setPadding(0, 8, 0, 24) })

        val blockCounts = AppPreferences.getBlockCounts(this)
        root.addView(label("Blocked app counts:", 14f, Color.GRAY).apply { setPadding(0, 8, 0, 8) })
        if (blockCounts.isEmpty()) {
            root.addView(label("No blocked app attempts logged yet.", 12f, Color.DKGRAY).apply { setPadding(0, 0, 0, 16) })
        } else {
            blockCounts.toList().sortedByDescending { it.second }.forEach { (pkg, count) ->
                root.addView(label("• $pkg → $count", 12f, Color.WHITE))
            }
            root.addView(space(16))
        }

        addGoalEditor(root, "Pushup goal", ChallengeType.PUSHUPS)
        addGoalEditor(root, "Curl-up goal", ChallengeType.CURL_UPS)
        addGoalEditor(root, "Sprint goal (steps)", ChallengeType.SPRINT)

        root.addView(label("Emergency PIN:", 14f, Color.GRAY).apply { setPadding(0, 24, 0, 8) })
        root.addView(actionRow("Set / Update emergency PIN") { showPinDialog() })

        root.addView(label("Blocked apps:", 14f, Color.GRAY).apply { setPadding(0, 24, 0, 8) })
        root.addView(actionRow("Add blocked app package") { gateBlockedListEdit { showAddBlockedDialog() } })
        val blocked = AppPreferences.getBlockedApps(this).toList().sorted()
        blocked.forEach { pkg ->
            root.addView(actionRow("Remove: $pkg") { gateBlockedListEdit { removeBlockedApp(pkg) } })
        }

        root.addView(label("Usage log:", 14f, Color.GRAY).apply { setPadding(0, 24, 0, 8) })
        val logs = AppPreferences.getUsageLog(this, limit = 60)
        if (logs.isEmpty()) {
            root.addView(label("No usage events yet.", 12f, Color.DKGRAY))
        } else {
            logs.forEach { line ->
                root.addView(label(line, 11f, Color.LTGRAY).apply { setPadding(0, 2, 0, 2) })
            }
        }

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun gateBlockedListEdit(action: () -> Unit) {
        if (AppPreferences.canEditBlockedApps(this)) {
            action()
            return
        }
        Toast.makeText(this, "Complete a workout first to edit blocked apps.", Toast.LENGTH_LONG).show()
    }

    private fun addGoalEditor(parent: LinearLayout, title: String, type: ChallengeType) {
        val goal = AppPreferences.getWorkoutGoal(this, type)
        parent.addView(actionRow("$title: $goal (tap to edit)") { showGoalDialog(title, type, goal) })
    }

    private fun showGoalDialog(title: String, type: ChallengeType, current: Int) {
        val input = EditText(this).apply {
            setText(current.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setBackgroundColor(Color.parseColor("#222222"))
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("Enter target rep/step count")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val value = input.text.toString().toIntOrNull()
                if (value == null || value <= 0) {
                    Toast.makeText(this, "Enter a valid positive number.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                AppPreferences.setWorkoutGoal(this, type, value)
                AppPreferences.appendUsageLog(this, "goal_updated", detail = "${type.name}=$value")
                buildUi()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPinDialog() {
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setText(AppPreferences.getEmergencyPin(this@SettingsActivity))
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setBackgroundColor(Color.parseColor("#222222"))
            hint = "Enter numeric PIN"
        }
        AlertDialog.Builder(this)
            .setTitle("Emergency PIN")
            .setMessage("Set a PIN for emergency bypass.")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val pin = input.text.toString().trim()
                if (!AppPreferences.isValidEmergencyPin(pin)) {
                    Toast.makeText(
                        this,
                        "PIN must be ${AppPreferences.EMERGENCY_PIN_MIN_LENGTH} to ${AppPreferences.EMERGENCY_PIN_MAX_LENGTH} digits.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }
                AppPreferences.setEmergencyPin(this, pin)
                AppPreferences.appendUsageLog(this, "pin_updated", detail = "length=${pin.length}")
                Toast.makeText(this, "Emergency PIN updated.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddBlockedDialog() {
        val input = EditText(this).apply {
            hint = "com.example.app"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setBackgroundColor(Color.parseColor("#222222"))
        }
        AlertDialog.Builder(this)
            .setTitle("Add blocked app")
            .setMessage("Enter package name to block.")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val pkg = input.text.toString().trim()
                if (AppPreferences.addBlockedApp(this, pkg)) {
                    AppPreferences.appendUsageLog(this, "blocked_app_added", pkg)
                    Toast.makeText(this, "Added $pkg to blocked list.", Toast.LENGTH_SHORT).show()
                    buildUi()
                } else {
                    Toast.makeText(this, "Invalid or already blocked package.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun removeBlockedApp(packageName: String) {
        if (AppPreferences.removeBlockedApp(this, packageName)) {
            AppPreferences.appendUsageLog(this, "blocked_app_removed", packageName)
            Toast.makeText(this, "Removed $packageName", Toast.LENGTH_SHORT).show()
            buildUi()
        }
    }

    private fun actionRow(text: String, onClick: () -> Unit): View =
        label("→  $text", 14f, Color.WHITE).apply {
            setPadding(0, 18, 0, 18)
            setOnClickListener { onClick() }
        }

    private fun label(text: String, size: Float, color: Int): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            typeface = Typeface.MONOSPACE
        }

    private fun vstack(paddingPx: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(paddingPx, paddingPx * 2, paddingPx, paddingPx * 2)
    }

    private fun space(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            height
        )
    }
}
