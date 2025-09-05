package com.arikachmad.pebblerun.bridge.location.model

import kotlinx.datetime.Instant

/**
 * Location accuracy levels for GPS tracking.
 * Supports SEC-002 (Privacy-compliant location data handling) and battery optimization.
 */
enum class LocationAccuracy {
    HIGH,       // Best accuracy, highest battery usage
    BALANCED,   // Good accuracy, moderate battery usage  
    LOW,        // Coarse accuracy, lowest battery usage
    PASSIVE     // Use existing location requests only
}

/**
 * Location permission states.
 * Handles permission requirements per bridge instructions.
 */
enum class LocationPermission {
    GRANTED,
    DENIED,
    NOT_REQUESTED,
    DENIED_FOREVER
}

/**
 * GPS position data with timestamp.
 * Satisfies REQ-002 (GPS-based pace and distance calculation).
 * Maps to domain GeoPoint but focuses on raw GPS data.
 */
data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,        // Altitude in meters above sea level
    val accuracy: Float,                 // Horizontal accuracy in meters
    val verticalAccuracy: Float? = null, // Vertical accuracy in meters
    val speed: Float? = null,            // Speed in meters per second
    val bearing: Float? = null,          // Direction of travel in degrees
    val timestamp: Instant,              // When location was captured
    val provider: String? = null         // GPS, Network, Passive, etc.
) {
    /**
     * Checks if this location has acceptable accuracy for fitness tracking.
     * Supports quality control for pace/distance calculations.
     */
    val isAccurate: Boolean
        get() = accuracy <= 50f // Accept locations within 50 meters
    
    /**
     * Checks if location is recent enough for real-time tracking.
     * Supports CON-003 (1-second update frequency) requirements.
     */
    fun isRecent(maxAgeSeconds: Int = 10): Boolean {
        val now = kotlinx.datetime.Clock.System.now()
        val ageSeconds = (now - timestamp).inWholeSeconds
        return ageSeconds <= maxAgeSeconds
    }
}

/**
 * Location service result for error handling.
 * Supports graceful handling of location service issues.
 */
sealed class LocationResult<out T> {
    data class Success<T>(val data: T) : LocationResult<T>()
    data class Error(val message: String, val throwable: Throwable? = null) : LocationResult<Nothing>()
    object PermissionDenied : LocationResult<Nothing>()
    object ServiceDisabled : LocationResult<Nothing>()
}
