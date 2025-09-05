package com.arikachmad.pebblerun.domain.util

import com.arikachmad.pebblerun.domain.entity.GeoPoint
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pace calculation utilities for workout tracking.
 * Satisfies REQ-002 (GPS-based pace and distance calculation).
 * Follows PAT-001 (Domain-driven design) by keeping calculations in domain layer.
 */
object PaceCalculator {
    
    private const val EARTH_RADIUS_KM = 6371.0
    private const val SECONDS_PER_MINUTE = 60.0
    private const val METERS_PER_KM = 1000.0
    
    /**
     * Calculates distance between two GPS coordinates using Haversine formula
     * Returns distance in meters
     */
    fun calculateDistance(point1: GeoPoint, point2: GeoPoint): Double {
        val lat1Rad = point1.latitude * kotlin.math.PI / 180.0
        val lat2Rad = point2.latitude * kotlin.math.PI / 180.0
        val deltaLatRad = (point2.latitude - point1.latitude) * kotlin.math.PI / 180.0
        val deltaLonRad = (point2.longitude - point1.longitude) * kotlin.math.PI / 180.0
        
        val a = sin(deltaLatRad / 2) * sin(deltaLatRad / 2) +
                cos(lat1Rad) * cos(lat2Rad) *
                sin(deltaLonRad / 2) * sin(deltaLonRad / 2)
        
        val c = 2 * acos(sqrt(1 - a))
        
        return EARTH_RADIUS_KM * c * METERS_PER_KM
    }
    
    /**
     * Calculates total distance from a list of GPS coordinates
     * Returns cumulative distance in meters
     */
    fun calculateTotalDistance(geoPoints: List<GeoPoint>): Double {
        if (geoPoints.size < 2) return 0.0
        
        var totalDistance = 0.0
        for (i in 1 until geoPoints.size) {
            totalDistance += calculateDistance(geoPoints[i - 1], geoPoints[i])
        }
        
        return totalDistance
    }
    
    /**
     * Calculates current pace based on distance and time
     * Returns pace in seconds per kilometer
     */
    fun calculatePace(distanceMeters: Double, timeSeconds: Long): Double {
        if (distanceMeters <= 0.0 || timeSeconds <= 0) return 0.0
        
        val distanceKm = distanceMeters / METERS_PER_KM
        return timeSeconds / distanceKm
    }
    
    /**
     * Calculates instantaneous pace between two GPS points
     * Returns pace in seconds per kilometer
     */
    fun calculateInstantaneousPace(
        point1: GeoPoint, 
        point2: GeoPoint
    ): Double {
        val distance = calculateDistance(point1, point2)
        val timeDiff = point2.timestamp.epochSeconds - point1.timestamp.epochSeconds
        
        return calculatePace(distance, timeDiff)
    }
    
    /**
     * Calculates average pace with smoothing to reduce GPS noise
     * Uses moving average over last N points to smooth out GPS variations
     */
    fun calculateSmoothedPace(
        geoPoints: List<GeoPoint>,
        smoothingWindow: Int = 5
    ): Double {
        if (geoPoints.size < 2) return 0.0
        
        val recentPoints = geoPoints.takeLast(minOf(smoothingWindow, geoPoints.size))
        if (recentPoints.size < 2) return 0.0
        
        val totalDistance = calculateTotalDistance(recentPoints)
        val timeDiff = recentPoints.last().timestamp.epochSeconds - recentPoints.first().timestamp.epochSeconds
        
        return calculatePace(totalDistance, timeDiff)
    }
    
    /**
     * Formats pace from seconds per kilometer to human-readable format (MM:SS per km)
     */
    fun formatPace(paceSecondsPerKm: Double): String {
        if (paceSecondsPerKm <= 0.0) return "--:--"
        
        val minutes = (paceSecondsPerKm / SECONDS_PER_MINUTE).toInt()
        val seconds = (paceSecondsPerKm % SECONDS_PER_MINUTE).toInt()
        
        return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }
    
    /**
     * Calculates speed in kilometers per hour from pace
     */
    fun paceToSpeed(paceSecondsPerKm: Double): Double {
        if (paceSecondsPerKm <= 0.0) return 0.0
        return 3600.0 / paceSecondsPerKm // 3600 seconds in an hour
    }
    
    /**
     * Validates GPS point for pace calculation
     * Filters out invalid or stationary points
     */
    fun isValidForPaceCalculation(
        point1: GeoPoint,
        point2: GeoPoint,
        minDistance: Double = 5.0, // minimum 5 meters
        maxTimeDiff: Long = 300 // maximum 5 minutes between points
    ): Boolean {
        val distance = calculateDistance(point1, point2)
        val timeDiff = kotlin.math.abs(point2.timestamp.epochSeconds - point1.timestamp.epochSeconds)
        
        return distance >= minDistance && 
               timeDiff > 0 && 
               timeDiff <= maxTimeDiff &&
               point1.accuracy <= 20.0 && // GPS accuracy within 20 meters
               point2.accuracy <= 20.0
    }
}
