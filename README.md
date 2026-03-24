# Sweaty Equity

Sweaty Equity is an Android focus-enforcement launcher + accessibility blocker.
Distracting apps stay blocked until you complete a workout challenge.

## What it does

- Blocks user-configured distracting apps with an overlay challenge screen.
- Unlocks blocked apps for 15 minutes after a successful workout.
- Supports 3 challenge types:
  - **Sprint** (step detector)
  - **Pushups** (proximity transitions)
  - **Curl-ups** (accelerometer pitch transitions)
- Adds anti-cheat plausibility constraints:
  - Pushups require a **minimum 1.5s interval** between counted reps.
  - Curl-ups require a **minimum 1.5s interval** and minimum movement delta.
- Includes a **Settings** screen to:
  - add/remove blocked apps
  - set workout target numbers (pushups, curl-ups, steps)
  - configure emergency bypass PIN
  - view usage log and stats
- Tracks:
  - workouts completed
  - current streak (daily)
  - per-app block counts
  - usage events (blocked, workout completed, bypass used, etc.)
- Adds an **emergency PIN bypass**:
  - grants temporary unlock if PIN is correct
  - logs each bypass use/failure

## Important behavior

- User can only edit blocked apps **after completing a workout** (within the workout unlock window).
- Emergency bypass can unlock apps when a workout cannot be completed, and every bypass attempt is logged.

## Project structure

- `HomeActivity`: minimalist launcher (allowed apps + settings entry)
- `FocusAccessibilityService`: foreground app monitor + blocker
- `WorkoutOverlayActivity`: workout gate overlay and challenge flow
- `ChallengeManager`: sensor rep counting and timing logic
- `SettingsActivity`: blocked list management, goals, stats, and usage logs
- `EmergencyBypassActivity`: PIN-verified emergency unlock
- `AppPreferences`: SharedPreferences persistence layer

## Build notes

This repository currently does not include a Gradle wrapper (`./gradlew`), so use local Gradle:

```bash
cd <project-root>
gradle assembleDebug
```

If Android Gradle Plugin dependencies are unavailable in your environment, builds may fail until network/repository access is available.
