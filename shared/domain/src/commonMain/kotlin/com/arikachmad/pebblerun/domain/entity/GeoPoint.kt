package com.arikachmad.pebblerun.domain.entity

import kotlinx.datetime.Instant
import kotlin.math.pow

/**
 * GPS coordinate data class for location tracking during workouts.
 * Satisfies REQ-002 (GPS-based pace and distance calculation) and SEC-002 (Privacy-compliant location data).
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null, // Altitude in meters above sea level
    val accuracy: Float, // Horizontal accuracy in meters
    val timestamp: Instant,
    val speed: Float? = null, // Speed in meters per second from GPS
    val bearing: Float? = null // Direction of travel in degrees (0-359)
) {
    init {
        require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90 degrees" }
        require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180 degrees" }
        require(accuracy >= 0) { "Accuracy must be non-negative" }
        bearing?.let { require(it in 0f..359f) { "Bearing must be between 0 and 359 degrees" } }
        speed?.let { require(it >= 0) { "Speed must be non-negative" } }
    }
    
    /**
     * Calculates distance to another GeoPoint using Haversine formula
     * Supports REQ-002 (GPS-based distance calculation)
     * @param other The other GeoPoint to calculate distance to
     * @return Distance in meters
     */
    fun distanceTo(other: GeoPoint): Double {
        val earthRadius = 6371000.0 // Earth radius in meters
        
        val lat1Rad = kotlin.math.PI * latitude / 180.0
        val lat2Rad = kotlin.math.PI * other.latitude / 180.0
        val deltaLatRad = kotlin.math.PI * (other.latitude - latitude) / 180.0
        val deltaLonRad = kotlin.math.PI * (other.longitude - longitude) / 180.0
        
        val a = kotlin.math.sin(deltaLatRad / 2) * kotlin.math.sin(deltaLatRad / 2) +
                kotlin.math.cos(lat1Rad) * kotlin.math.cos(lat2Rad) *
                kotlin.math.sin(deltaLonRad / 2) * kotlin.math.sin(deltaLonRad / 2)
        
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        
        return earthRadius * c
    }
    
    /**
     * Calculates instantaneous pace between this point and another
     * Supports REQ-002 (GPS-based pace calculation)
     * @param other The previous GeoPoint
     * @return Pace in seconds per kilometer, or null if calculation is invalid
     */
    fun paceTo(other: GeoPoint): Double? {
        val distance = distanceTo(other) // meters
        val timeDiff = timestamp.epochSeconds - other.timestamp.epochSeconds // seconds
        
        if (distance <= 0 || timeDiff <= 0) return null
        
        // Convert to pace in seconds per kilometer
        val paceSecondsPerMeter = timeDiff.toDouble() / distance
        return paceSecondsPerMeter * 1000.0 // seconds per kilometer
    }
    
    /**
     * Checks if this GPS point has acceptable accuracy for tracking
     * Supports CON-004 (Graceful handling of GPS variations)
     */
    fun hasAcceptableAccuracy(maxAccuracy: Float = 50f): Boolean {
        return accuracy <= maxAccuracy
    }
    
    /**
     * Creates a simplified version without sensitive precision for privacy
     * Supports SEC-002 (Privacy-compliant location data handling)
     */
    fun anonymized(precision: Int = 4): GeoPoint {
        val factor = 10.0.pow(precision.toDouble())
        return copy(
            latitude = kotlin.math.round(latitude * factor) / factor,
            longitude = kotlin.math.round(longitude * factor) / factor,
            altitude = null,
            speed = null,
            bearing = null
        )
    }
}
