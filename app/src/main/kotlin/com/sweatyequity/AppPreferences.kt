package com.sweatyequity

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppPreferences {
    private const val PREFS_NAME = "sweaty_equity_prefs"
    private const val KEY_UNLOCK_TIME = "unlock_timestamp"
    private const val KEY_BLOCKED_APPS = "blocked_apps"
    private const val KEY_PUSHUP_GOAL = "pushup_goal"
    private const val KEY_CURLUP_GOAL = "curlup_goal"
    private const val KEY_SPRINT_GOAL = "sprint_goal"
    private const val KEY_EMERGENCY_PIN = "emergency_pin"
    private const val KEY_WORKOUTS_COMPLETED = "workouts_completed"
    private const val KEY_STREAK_COUNT = "streak_count"
    private const val KEY_LAST_WORKOUT_DAY = "last_workout_day"
    private const val KEY_USAGE_LOG = "usage_log"
    private const val KEY_LAST_WORKOUT_UNLOCK_TIME = "last_workout_unlock_time"
    private const val KEY_BLOCK_COUNT_PREFIX = "block_count_"

    private const val MAX_USAGE_LOG_LINES = 200
    private const val UNLOCK_DURATION_MS = 15L * 60L * 1_000L

    val DEFAULT_BLOCKED_PACKAGES: Set<String> = setOf(
        "com.instagram.android",
        "com.google.android.youtube",
        "com.android.vending",
        "com.twitter.android",
        "com.facebook.katana",
        "com.facebook.lite",
        "com.snapchat.android",
        "com.zhiliaoapp.musically",
        "com.reddit.frontpage",
        "com.netflix.mediaclient",
        "com.linkedin.android",
        "com.pinterest",
        "com.tumblr",
        "com.discord"
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isUnlockWindowActive(context: Context): Boolean {
        val ts = prefs(context).getLong(KEY_UNLOCK_TIME, 0L)
        return System.currentTimeMillis() - ts < UNLOCK_DURATION_MS
    }

    fun markUnlockNow(context: Context) {
        prefs(context).edit().putLong(KEY_UNLOCK_TIME, System.currentTimeMillis()).apply()
    }

    fun markWorkoutUnlockNow(context: Context) {
        val now = System.currentTimeMillis()
        prefs(context).edit()
            .putLong(KEY_UNLOCK_TIME, now)
            .putLong(KEY_LAST_WORKOUT_UNLOCK_TIME, now)
            .apply()
    }

    fun canEditBlockedApps(context: Context): Boolean {
        val ts = prefs(context).getLong(KEY_LAST_WORKOUT_UNLOCK_TIME, 0L)
        return System.currentTimeMillis() - ts < UNLOCK_DURATION_MS
    }

    fun getBlockedApps(context: Context): MutableSet<String> {
        val saved = prefs(context).getStringSet(KEY_BLOCKED_APPS, null)
        return (saved ?: DEFAULT_BLOCKED_PACKAGES).toMutableSet()
    }

    fun setBlockedApps(context: Context, blockedApps: Set<String>) {
        prefs(context).edit().putStringSet(KEY_BLOCKED_APPS, blockedApps).apply()
    }

    fun addBlockedApp(context: Context, packageName: String): Boolean {
        if (!isValidPackageName(packageName)) return false
        val blocked = getBlockedApps(context)
        val added = blocked.add(packageName)
        if (added) setBlockedApps(context, blocked)
        return added
    }

    fun removeBlockedApp(context: Context, packageName: String): Boolean {
        val blocked = getBlockedApps(context)
        val removed = blocked.remove(packageName)
        if (removed) setBlockedApps(context, blocked)
        return removed
    }

    fun getWorkoutGoal(context: Context, challengeType: ChallengeType): Int {
        val key = when (challengeType) {
            ChallengeType.SPRINT -> KEY_SPRINT_GOAL
            ChallengeType.PUSHUPS -> KEY_PUSHUP_GOAL
            ChallengeType.CURL_UPS -> KEY_CURLUP_GOAL
        }
        val fallback = when (challengeType) {
            ChallengeType.SPRINT -> ChallengeManager.SPRINT_STEP_TARGET
            ChallengeType.PUSHUPS -> ChallengeManager.PUSHUP_TARGET
            ChallengeType.CURL_UPS -> ChallengeManager.CURL_UP_TARGET
        }
        return prefs(context).getInt(key, fallback).coerceAtLeast(1)
    }

    fun setWorkoutGoal(context: Context, challengeType: ChallengeType, value: Int) {
        val key = when (challengeType) {
            ChallengeType.SPRINT -> KEY_SPRINT_GOAL
            ChallengeType.PUSHUPS -> KEY_PUSHUP_GOAL
            ChallengeType.CURL_UPS -> KEY_CURLUP_GOAL
        }
        prefs(context).edit().putInt(key, value.coerceAtLeast(1)).apply()
    }

    fun getEmergencyPin(context: Context): String =
        prefs(context).getString(KEY_EMERGENCY_PIN, "") ?: ""

    fun setEmergencyPin(context: Context, pin: String) {
        prefs(context).edit().putString(KEY_EMERGENCY_PIN, pin.trim()).apply()
    }

    fun incrementBlockCount(context: Context, packageName: String) {
        val key = KEY_BLOCK_COUNT_PREFIX + packageName
        val current = prefs(context).getInt(key, 0)
        prefs(context).edit().putInt(key, current + 1).apply()
    }

    fun getBlockCounts(context: Context): Map<String, Int> {
        return prefs(context).all
            .filterKeys { it.startsWith(KEY_BLOCK_COUNT_PREFIX) }
            .mapValues { (_, value) -> (value as? Int) ?: 0 }
            .mapKeys { (k, _) -> k.removePrefix(KEY_BLOCK_COUNT_PREFIX) }
    }

    fun recordWorkoutCompletion(context: Context) {
        val prefs = prefs(context)
        val now = System.currentTimeMillis()
        val day = now / (24L * 60L * 60L * 1000L)
        val lastDay = prefs.getLong(KEY_LAST_WORKOUT_DAY, -1L)
        val currentStreak = prefs.getInt(KEY_STREAK_COUNT, 0)
        val nextStreak = when {
            lastDay == day -> currentStreak
            lastDay == day - 1L -> currentStreak + 1
            else -> 1
        }
        val workouts = prefs.getInt(KEY_WORKOUTS_COMPLETED, 0) + 1
        prefs.edit()
            .putInt(KEY_WORKOUTS_COMPLETED, workouts)
            .putInt(KEY_STREAK_COUNT, nextStreak)
            .putLong(KEY_LAST_WORKOUT_DAY, day)
            .apply()
    }

    fun getWorkoutsCompleted(context: Context): Int =
        prefs(context).getInt(KEY_WORKOUTS_COMPLETED, 0)

    fun getStreakCount(context: Context): Int =
        prefs(context).getInt(KEY_STREAK_COUNT, 0)

    fun appendUsageLog(context: Context, event: String, packageName: String? = null, detail: String? = null) {
        val prefs = prefs(context)
        val current = prefs.getString(KEY_USAGE_LOG, "") ?: ""
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val pkg = packageName ?: "-"
        val extra = detail ?: "-"
        val line = "$timestamp | $event | $pkg | $extra"
        val lines = (current.lines().filter { it.isNotBlank() } + line).takeLast(MAX_USAGE_LOG_LINES)
        prefs.edit().putString(KEY_USAGE_LOG, lines.joinToString("\n")).apply()
    }

    fun getUsageLog(context: Context, limit: Int = 40): List<String> {
        val data = prefs(context).getString(KEY_USAGE_LOG, "") ?: ""
        return data.lines()
            .filter { it.isNotBlank() }
            .takeLast(limit)
            .reversed()
    }

    private fun isValidPackageName(value: String): Boolean {
        if (value.isBlank()) return false
        return value.matches(Regex("^[a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+)+$"))
    }
}
