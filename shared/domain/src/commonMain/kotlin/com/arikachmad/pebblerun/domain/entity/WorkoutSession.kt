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
        return WorkoutSessionValidator.canTransitionTo(status, newStatus, this).isValid
    }
    
    /**
     * Gets detailed validation result for state transition
     */
    fun validateTransitionTo(newStatus: WorkoutStatus): ValidationResult {
        return WorkoutSessionValidator.canTransitionTo(status, newStatus, this)
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
                totalDuration = timestamp.epochSeconds - startTime.epochSeconds
            )
            else -> copy(status = newStatus)
        }
    }
    
    /**
     * Updates workout session with new HR sample and recalculates statistics
     * Supports REQ-001 (Real-time HR data collection)
     */
    fun withNewHRSample(hrSample: HRSample): WorkoutSession {
        val updatedSamples = hrSamples + hrSample
        val validHRValues = updatedSamples.map { it.heartRate }.filter { it > 0 }
        
        return copy(
            hrSamples = updatedSamples,
            averageHeartRate = if (validHRValues.isNotEmpty()) validHRValues.average().toInt() else 0,
            maxHeartRate = validHRValues.maxOrNull() ?: 0,
            minHeartRate = validHRValues.minOrNull() ?: 0
        )
    }
    
    /**
     * Updates workout session with new GPS point and recalculates pace/distance
     * Supports REQ-002 (GPS-based pace and distance calculation)
     */
    fun withNewGeoPoint(geoPoint: GeoPoint, newPace: Double, newDistance: Double): WorkoutSession {
        return copy(
            geoPoints = geoPoints + geoPoint,
            totalDistance = newDistance,
            averagePace = newPace
        )
    }
    
    /**
     * Updates session duration for real-time display
     * Supports REQ-006 (Real-time data synchronization)
     */
    fun withUpdatedDuration(currentTime: Instant): WorkoutSession {
        val duration = if (status == WorkoutStatus.ACTIVE) {
            currentTime.epochSeconds - startTime.epochSeconds
        } else totalDuration
        
        return copy(totalDuration = duration)
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
