package com.sweatyequity

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class EmergencyBypassActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 96)
            setBackgroundColor(Color.BLACK)
        }

        root.addView(label("EMERGENCY BYPASS", 20f, Color.WHITE).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        })
        root.addView(label("Use only when workout cannot be completed.", 12f, Color.GRAY).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        })

        val pinInput = EditText(this).apply {
            hint = "Enter emergency PIN"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setBackgroundColor(Color.parseColor("#222222"))
            setPadding(24, 20, 24, 20)
        }
        root.addView(pinInput)

        root.addView(label("→  Unlock for 15 minutes", 16f, Color.WHITE).apply {
            setPadding(0, 36, 0, 0)
            setOnClickListener {
                val expected = AppPreferences.getEmergencyPin(this@EmergencyBypassActivity)
                val entered = pinInput.text.toString().trim()
                if (expected.isBlank()) {
                    Toast.makeText(this@EmergencyBypassActivity, "Set an emergency PIN in Settings first.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                if (entered == expected) {
                    AppPreferences.markEmergencyUnlockNow(this@EmergencyBypassActivity)
                    AppPreferences.appendUsageLog(
                        context = this@EmergencyBypassActivity,
                        event = "emergency_bypass_used",
                        detail = "pin_verified"
                    )
                    Toast.makeText(this@EmergencyBypassActivity, "Emergency bypass granted.", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    AppPreferences.appendUsageLog(
                        context = this@EmergencyBypassActivity,
                        event = "emergency_bypass_failed",
                        detail = "invalid_pin"
                    )
                    Toast.makeText(this@EmergencyBypassActivity, "Invalid PIN.", Toast.LENGTH_SHORT).show()
                }
            }
        })

        setContentView(root)
    }

    private fun label(text: String, sizeSp: Float, color: Int): TextView = TextView(this).apply {
        this.text = text
        textSize = sizeSp
        setTextColor(color)
        typeface = Typeface.MONOSPACE
    }
}
