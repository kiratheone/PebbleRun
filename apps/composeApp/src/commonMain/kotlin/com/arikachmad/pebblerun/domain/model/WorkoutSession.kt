package com.arikachmad.pebblerun.domain.model

import kotlinx.datetime.Instant

/**
 * Represents a complete workout session with all associated data
 */
data class WorkoutSession(
    val id: String,
    val startTime: Instant,
    val endTime: Instant? = null,
    val status: WorkoutStatus,
    val totalDuration: Long, // milliseconds
    val totalDistance: Double, // meters
    val averagePace: Double, // seconds per kilometer
    val averageHeartRate: Int, // BPM
    val maxHeartRate: Int, // BPM
    val calories: Int,
    val hrSamples: List<HRSample>,
    val geoPoints: List<GeoPoint>,
    val notes: String = ""
)

/**
 * Heart rate sample with timestamp
 */
data class HRSample(
    val timestamp: Instant,
    val bpm: Int
)

/**
 * GPS location point with timestamp
 */
data class GeoPoint(
    val timestamp: Instant,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val accuracy: Float
)

/**
 * Workout session status
 */
enum class WorkoutStatus {
    NOT_STARTED,
    ACTIVE,
    PAUSED,
    COMPLETED
}
