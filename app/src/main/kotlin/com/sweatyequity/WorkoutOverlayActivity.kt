package com.sweatyequity

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * WorkoutOverlayActivity — the Sweat Equity Gate.
 *
 * Displayed whenever FocusAccessibilityService detects a distracting app in
 * the foreground. The user must complete one of three sensor-verified workout
 * challenges to earn a 15-minute unlock window.
 *
 * The back button is suppressed so the workout cannot be skipped. The
 * accessibility service re-triggers this screen if the user manages to
 * navigate away to another distracting app via the home button.
 *
 * Window flags ensure the screen turns on and the activity is visible over
 * the lock screen.
 */
class WorkoutOverlayActivity : AppCompatActivity() {

    // ─── Companion ────────────────────────────────────────────────────────────

    companion object {
        private const val PREFS_NAME        = "sweaty_equity_prefs"
        private const val KEY_UNLOCK_TIME   = "unlock_timestamp"

        /** Duration of the post-workout unlock window: 15 minutes. */
        private const val UNLOCK_DURATION_MS = 15L * 60L * 1_000L

        /**
         * Returns true if a completed workout still grants access
         * (i.e., fewer than 15 minutes have elapsed since it was completed).
         */
        fun isUnlockWindowActive(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val ts = prefs.getLong(KEY_UNLOCK_TIME, 0L)
            return System.currentTimeMillis() - ts < UNLOCK_DURATION_MS
        }
    }

    // ─── State ────────────────────────────────────────────────────────────────

    private var challengeManager: ChallengeManager? = null

    // Pending challenge type, held while we wait for a permission grant.
    private var pendingChallengeType: ChallengeType? = null

    // Runtime permission launcher for ACTIVITY_RECOGNITION (API 29+).
    private val activityRecognitionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pending = pendingChallengeType ?: return@registerForActivityResult
        pendingChallengeType = null
        if (granted) beginChallenge(pending)
    }

    // Late-init views updated by sensor callbacks
    private lateinit var counterText: TextView
    private lateinit var timerText:   TextView
    private lateinit var progressBar: ProgressBar

    // Top-level layout sections
    private lateinit var selectionLayout: LinearLayout
    private lateinit var activeLayout:    LinearLayout

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If the user already completed a workout recently, let them through.
        if (isUnlockWindowActive(this)) {
            finish()
            return
        }

        applyWindowFlags()
        buildUI()
    }

    override fun onResume() {
        super.onResume()
        if (isUnlockWindowActive(this)) {
            finish()
            return
        }
        challengeManager?.register()
    }

    override fun onPause() {
        super.onPause()
        // Unregister sensors to preserve battery while the screen is off.
        challengeManager?.unregister()
    }

    override fun onDestroy() {
        super.onDestroy()
        challengeManager?.unregister()
    }

    /** Suppress back navigation — the only exit is completing the workout. */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        // Intentionally blocked.
    }

    // ─── Window configuration ─────────────────────────────────────────────────

    private fun applyWindowFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
    }

    // ─── UI construction ──────────────────────────────────────────────────────

    private fun buildUI() {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.BLACK) }
        val root = vstack(paddingPx = 56)

        // Header
        root.addView(label("SWEATY EQUITY", 22f, Color.WHITE).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8)
        })
        root.addView(label("Pay your dues to unlock your phone.", 13f, Color.GRAY).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 56)
        })

        // ── Challenge selection ───────────────────────────────────────────────
        selectionLayout = vstack()

        selectionLayout.addView(
            label("CHOOSE YOUR CHALLENGE:", 13f, Color.WHITE).apply {
                setPadding(0, 0, 0, 24)
            }
        )

        addChallengeCard(
            parent      = selectionLayout,
            emoji       = "🏃",
            title       = "THE SPRINT",
            description = "750 steps  ·  4-minute limit",
            type        = ChallengeType.SPRINT
        )
        addChallengeCard(
            parent      = selectionLayout,
            emoji       = "💪",
            title       = "PUSHUPS",
            description = "100 reps  ·  chest to the floor",
            type        = ChallengeType.PUSHUPS
        )
        addChallengeCard(
            parent      = selectionLayout,
            emoji       = "🔄",
            title       = "CURL-UPS",
            description = "50 reps  ·  phone held to chest",
            type        = ChallengeType.CURL_UPS
        )

        root.addView(selectionLayout)

        // ── Active challenge display (hidden until a challenge starts) ─────────
        activeLayout = vstack().apply { visibility = View.GONE }

        activeLayout.addView(label("KEEP GOING!", 16f, Color.WHITE).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        })

        counterText = label("0 / 0", 52f, Color.WHITE).apply {
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 16)
        }
        activeLayout.addView(counterText)

        progressBar = ProgressBar(
            this, null, android.R.attr.progressBarStyleHorizontal
        ).apply {
            max = 100
            progress = 0
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 32
            ).also { it.bottomMargin = 16 }
        }
        activeLayout.addView(progressBar)

        timerText = label("", 14f, Color.YELLOW).apply {
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 0)
        }
        activeLayout.addView(timerText)

        root.addView(activeLayout)

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun addChallengeCard(
        parent:      LinearLayout,
        emoji:       String,
        title:       String,
        description: String,
        type:        ChallengeType
    ) {
        val card = vstack(paddingPx = 32).apply {
            setBackgroundColor(Color.parseColor("#111111"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = 16 }
            setOnClickListener { beginChallenge(type) }
        }
        card.addView(label("$emoji  $title", 16f, Color.WHITE))
        card.addView(label(description, 12f, Color.GRAY).apply { setPadding(0, 4, 0, 0) })
        parent.addView(card)
    }

    // ─── Challenge control ────────────────────────────────────────────────────

    private fun beginChallenge(type: ChallengeType) {
        // The sprint uses TYPE_STEP_DETECTOR which requires ACTIVITY_RECOGNITION
        // on API 29+. Request it before proceeding if not yet granted.
        if (type == ChallengeType.SPRINT &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pendingChallengeType = type
            activityRecognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            return
        }

        // Swap layouts
        selectionLayout.visibility = View.GONE
        activeLayout.visibility    = View.VISIBLE

        challengeManager = ChallengeManager(
            context          = this,
            challengeType    = type,
            onProgressUpdate = { current, tgt ->
                runOnUiThread {
                    counterText.text   = "$current / $tgt"
                    progressBar.progress = ((current.toFloat() / tgt) * 100).toInt()
                }
            },
            onCompleted      = { runOnUiThread { completeChallenge() } },
            onTimerTick      = { secsLeft ->
                runOnUiThread {
                    val m = secsLeft / 60
                    val s = secsLeft % 60
                    timerText.text = "Time remaining: %02d:%02d".format(m, s)
                }
            },
            onTimerExpired   = {
                runOnUiThread {
                    counterText.text   = "0 / ${challengeManager?.target ?: 0}"
                    progressBar.progress = 0
                    timerText.text     = "⏰  Time's up — starting over!"
                }
            }
        )
        // Initialise the counter display before the first sensor event arrives.
        challengeManager!!.let { mgr ->
            counterText.text = "0 / ${mgr.target}"
            mgr.register()
        }
    }

    private fun completeChallenge() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_UNLOCK_TIME, System.currentTimeMillis())
            .apply()

        challengeManager?.unregister()
        challengeManager = null
        finish()
    }

    // ─── View helpers ─────────────────────────────────────────────────────────

    private fun vstack(paddingPx: Int = 0) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        if (paddingPx > 0) setPadding(paddingPx, paddingPx * 2, paddingPx, paddingPx * 2)
    }

    private fun label(text: String, sizeSp: Float, color: Int) = TextView(this).apply {
        this.text = text
        textSize  = sizeSp
        setTextColor(color)
        typeface  = Typeface.MONOSPACE
    }
}
