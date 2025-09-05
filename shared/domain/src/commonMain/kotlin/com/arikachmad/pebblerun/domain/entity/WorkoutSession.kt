package com.arikachmad.pebblerun.domain.entity

import kotlinx.datetime.Instant

/**
 * Core workout session entity representing a complete workout tracking session.
 * Satisfies REQ-005 (Local storage of workout data) and PAT-001 (Domain-driven design).
 */
data class WorkoutSession(
    val id: String,
    val startTime: Instant,
    val endTime: Instant? = null,
    val status: WorkoutStatus,
    val totalDuration: Long = 0, // Duration in seconds
    val totalDistance: Double = 0.0, // Distance in meters
    val averagePace: Double = 0.0, // Average pace in seconds per kilometer
    val averageHeartRate: Int = 0, // Average HR in BPM
    val maxHeartRate: Int = 0, // Maximum HR in BPM
    val minHeartRate: Int = 0, // Minimum HR in BPM
    val calories: Int = 0, // Estimated calories burned
    val geoPoints: List<GeoPoint> = emptyList(),
    val hrSamples: List<HRSample> = emptyList(),
    val notes: String = ""
) {
    /**
     * Calculates if the session is currently active based on status
     * Supports REQ-004 (Background tracking capability)
     */
    val isActive: Boolean
        get() = status == WorkoutStatus.ACTIVE || status == WorkoutStatus.PAUSED
    
    /**
     * Validates session state transitions according to business rules
     * Supports requirement for session state transition validation
     */
    fun canTransitionTo(newStatus: WorkoutStatus): Boolean {
        return when (status) {
            WorkoutStatus.PENDING -> newStatus == WorkoutStatus.ACTIVE
            WorkoutStatus.ACTIVE -> newStatus == WorkoutStatus.PAUSED || newStatus == WorkoutStatus.COMPLETED
            WorkoutStatus.PAUSED -> newStatus == WorkoutStatus.ACTIVE || newStatus == WorkoutStatus.COMPLETED
            WorkoutStatus.COMPLETED -> false // No transitions from completed
        }
    }
    
    /**
     * Creates a new session with updated status
     * Ensures immutability of domain entities
     */
    fun withStatus(newStatus: WorkoutStatus, timestamp: Instant = startTime): WorkoutSession {
        require(canTransitionTo(newStatus)) { "Invalid status transition from $status to $newStatus" }
        
        return when (newStatus) {
            WorkoutStatus.COMPLETED -> copy(
                status = newStatus,
                endTime = timestamp,
                totalDuration = if (endTime != null) endTime.epochSeconds - startTime.epochSeconds else 0
            )
            else -> copy(status = newStatus)
        }
    }
}

/**
 * Workout session status enumeration
 * Supports REQ-004 (Background tracking) and session lifecycle management
 */
enum class WorkoutStatus {
    PENDING,    // Session created but not started
    ACTIVE,     // Currently tracking workout
    PAUSED,     // Temporarily paused
    COMPLETED   // Session finished and saved
}
