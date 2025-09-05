package com.arikachmad.pebblerun.bridge.pebble.model

import kotlinx.datetime.Instant

/**
 * Connection state for Pebble device.
 * Supports REQ-001 (Real-time HR data collection) by tracking device connectivity.
 */
enum class PebbleConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

/**
 * Workout command to send to Pebble watchapp.
 * Satisfies REQ-003 (Auto-launch PebbleRun watchapp) and REQ-006 (Real-time data sync).
 */
enum class WorkoutCommand {
    START,
    STOP,
    PAUSE,
    RESUME
}

/**
 * Data to send to Pebble during workout.
 * Supports REQ-006 (Mobile → Pebble: Pace/Duration data sync).
 */
data class WorkoutDataToPebble(
    val pace: Float, // Seconds per kilometer
    val duration: Int, // Workout duration in seconds
    val distance: Float // Distance in meters
)

/**
 * HR data received from Pebble.
 * Satisfies REQ-001 (Pebble → Mobile: HR data) and CON-003 (1-second update frequency).
 */
data class HRDataFromPebble(
    val heartRate: Int, // BPM
    val quality: Int, // 0=bad, 1=ok, 2=good
    val timestamp: Instant
) {
    val isValid: Boolean
        get() = heartRate in 30..220 && quality > 0
}

/**
 * Pebble transport result for error handling.
 * Supports CON-004 (Graceful handling of Pebble disconnections).
 */
sealed class PebbleResult<out T> {
    data class Success<T>(val data: T) : PebbleResult<T>()
    data class Error(val message: String, val throwable: Throwable? = null) : PebbleResult<Nothing>()
    object Disconnected : PebbleResult<Nothing>()
}
