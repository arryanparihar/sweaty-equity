package com.sweatyequity

/**
 * The three workout challenges a user can choose from to unlock their phone.
 */
enum class ChallengeType {
    /** 750 steps detected by the step detector within a 4-minute window. */
    SPRINT,

    /** 100 proximity NEAR-to-FAR transitions (chest-to-floor pushups). */
    PUSHUPS,

    /** 50 upward pitch transitions measured via the accelerometer. */
    CURL_UPS
}
