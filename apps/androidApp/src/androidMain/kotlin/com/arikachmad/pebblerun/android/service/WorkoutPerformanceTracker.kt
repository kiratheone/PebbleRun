package com.arikachmad.pebblerun.android.service

import com.arikachmad.pebblerun.domain.entity.WorkoutSession
import com.arikachmad.pebblerun.domain.entity.GeoPoint
import com.arikachmad.pebblerun.domain.entity.HRSample
import kotlin.math.*

/**
 * Advanced workout performance tracking and metrics calculation.
 * Implements enhanced tracking capabilities for TASK-031.
 */
class WorkoutPerformanceTracker {
    
    data class WorkoutMetrics(
        val avgPace: String,
        val currentPace: String,
        val avgHeartRate: Int,
        val currentHeartRate: Int,
        val estimatedCalories: Int,
        val elevationGain: Double,
        val maxSpeed: Double,
        val avgSpeed: Double,
        val heartRateZone: HeartRateZone,
        val efficiencyScore: Double
    )
    
    enum class HeartRateZone {
        RESTING, FAT_BURN, AEROBIC, ANAEROBIC, MAXIMUM
    }
    
    /**
     * Calculates comprehensive workout metrics
     */
    fun calculateMetrics(session: WorkoutSession): WorkoutMetrics {
        val gpsTrack = session.gpsTrack
        val hrSamples = session.hrSamples
        
        return WorkoutMetrics(
            avgPace = calculateAveragePace(gpsTrack, session.duration),
            currentPace = calculateCurrentPace(gpsTrack),
            avgHeartRate = calculateAverageHeartRate(hrSamples),
            currentHeartRate = getCurrentHeartRate(hrSamples),
            estimatedCalories = estimateCaloriesBurned(session, hrSamples),
            elevationGain = calculateElevationGain(gpsTrack),
            maxSpeed = calculateMaxSpeed(gpsTrack),
            avgSpeed = calculateAverageSpeed(gpsTrack, session.duration),
            heartRateZone = determineHeartRateZone(getCurrentHeartRate(hrSamples)),
            efficiencyScore = calculateEfficiencyScore(gpsTrack, hrSamples)
        )
    }
    
    /**
     * Calculates average pace from GPS track
     */
    private fun calculateAveragePace(gpsTrack: List<GeoPoint>, durationSeconds: Long): String {
        if (gpsTrack.size < 2 || durationSeconds <= 0) return "0:00"
        
        val totalDistance = calculateTotalDistance(gpsTrack)
        if (totalDistance <= 0) return "0:00"
        
        val paceSecondsPerKm = (durationSeconds / (totalDistance / 1000.0)).toInt()
        return formatPace(paceSecondsPerKm)
    }
    
    /**
     * Calculates current pace from recent GPS points
     */
    private fun calculateCurrentPace(gpsTrack: List<GeoPoint>): String {
        if (gpsTrack.size < 10) return "0:00"
        
        // Use last 10 points for current pace calculation
        val recentPoints = gpsTrack.takeLast(10)
        val timeDiff = (recentPoints.last().timestamp - recentPoints.first().timestamp) / 1000.0
        val distance = calculateTotalDistance(recentPoints)
        
        if (timeDiff <= 0 || distance <= 0) return "0:00"
        
        val paceSecondsPerKm = (timeDiff / (distance / 1000.0)).toInt()
        return formatPace(paceSecondsPerKm)
    }
    
    /**
     * Calculates total distance from GPS track
     */
    private fun calculateTotalDistance(gpsTrack: List<GeoPoint>): Double {
        if (gpsTrack.size < 2) return 0.0
        
        var totalDistance = 0.0
        for (i in 1 until gpsTrack.size) {
            totalDistance += calculateDistanceBetween(gpsTrack[i-1], gpsTrack[i])
        }
        return totalDistance
    }
    
    /**
     * Calculates distance between two GPS points using Haversine formula
     */
    private fun calculateDistanceBetween(point1: GeoPoint, point2: GeoPoint): Double {
        val R = 6371000.0 // Earth's radius in meters
        val lat1Rad = Math.toRadians(point1.latitude)
        val lat2Rad = Math.toRadians(point2.latitude)
        val deltaLatRad = Math.toRadians(point2.latitude - point1.latitude)
        val deltaLonRad = Math.toRadians(point2.longitude - point1.longitude)
        
        val a = sin(deltaLatRad / 2).pow(2) + 
                cos(lat1Rad) * cos(lat2Rad) * sin(deltaLonRad / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return R * c
    }
    
    /**
     * Calculates average heart rate
     */
    private fun calculateAverageHeartRate(hrSamples: List<HRSample>): Int {
        if (hrSamples.isEmpty()) return 0
        return hrSamples.map { it.bpm }.average().toInt()
    }
    
    /**
     * Gets current heart rate from most recent sample
     */
    private fun getCurrentHeartRate(hrSamples: List<HRSample>): Int {
        return hrSamples.lastOrNull()?.bpm ?: 0
    }
    
    /**
     * Estimates calories burned based on heart rate and duration
     */
    private fun estimateCaloriesBurned(session: WorkoutSession, hrSamples: List<HRSample>): Int {
        if (hrSamples.isEmpty()) return 0
        
        val avgHR = calculateAverageHeartRate(hrSamples)
        val durationMinutes = session.duration / 60.0
        val weight = 70.0 // Default weight in kg, should be from user profile
        
        // Simplified calorie calculation based on heart rate
        val caloriesPerMinute = when (avgHR) {
            in 50..100 -> 3.5 * weight / 60
            in 101..140 -> 7.0 * weight / 60
            in 141..160 -> 10.0 * weight / 60
            in 161..180 -> 12.0 * weight / 60
            else -> 5.0 * weight / 60
        }
        
        return (caloriesPerMinute * durationMinutes).toInt()
    }
    
    /**
     * Calculates elevation gain from GPS track
     */
    private fun calculateElevationGain(gpsTrack: List<GeoPoint>): Double {
        if (gpsTrack.size < 2) return 0.0
        
        var elevationGain = 0.0
        for (i in 1 until gpsTrack.size) {
            val altitudeDiff = gpsTrack[i].altitude - gpsTrack[i-1].altitude
            if (altitudeDiff > 0) {
                elevationGain += altitudeDiff
            }
        }
        return elevationGain
    }
    
    /**
     * Calculates maximum speed from GPS track
     */
    private fun calculateMaxSpeed(gpsTrack: List<GeoPoint>): Double {
        if (gpsTrack.size < 2) return 0.0
        
        var maxSpeed = 0.0
        for (i in 1 until gpsTrack.size) {
            val distance = calculateDistanceBetween(gpsTrack[i-1], gpsTrack[i])
            val timeDiff = (gpsTrack[i].timestamp - gpsTrack[i-1].timestamp) / 1000.0
            
            if (timeDiff > 0) {
                val speed = distance / timeDiff // m/s
                maxSpeed = maxOf(maxSpeed, speed)
            }
        }
        return maxSpeed
    }
    
    /**
     * Calculates average speed
     */
    private fun calculateAverageSpeed(gpsTrack: List<GeoPoint>, durationSeconds: Long): Double {
        if (gpsTrack.size < 2 || durationSeconds <= 0) return 0.0
        
        val totalDistance = calculateTotalDistance(gpsTrack)
        return totalDistance / durationSeconds // m/s
    }
    
    /**
     * Determines heart rate zone
     */
    private fun determineHeartRateZone(heartRate: Int): HeartRateZone {
        val maxHR = 190 // Should be calculated based on age: 220 - age
        val hrPercent = (heartRate.toDouble() / maxHR) * 100
        
        return when {
            hrPercent < 50 -> HeartRateZone.RESTING
            hrPercent < 70 -> HeartRateZone.FAT_BURN
            hrPercent < 85 -> HeartRateZone.AEROBIC
            hrPercent < 95 -> HeartRateZone.ANAEROBIC
            else -> HeartRateZone.MAXIMUM
        }
    }
    
    /**
     * Calculates workout efficiency score
     */
    private fun calculateEfficiencyScore(gpsTrack: List<GeoPoint>, hrSamples: List<HRSample>): Double {
        if (gpsTrack.size < 2 || hrSamples.isEmpty()) return 0.0
        
        val avgSpeed = calculateAverageSpeed(gpsTrack, hrSamples.size.toLong())
        val avgHR = calculateAverageHeartRate(hrSamples)
        
        // Simple efficiency calculation: speed/heart_rate ratio
        return if (avgHR > 0) (avgSpeed * 100) / avgHR else 0.0
    }
    
    /**
     * Formats pace in mm:ss format
     */
    private fun formatPace(paceSecondsPerKm: Int): String {
        val minutes = paceSecondsPerKm / 60
        val seconds = paceSecondsPerKm % 60
        return String.format("%d:%02d", minutes, seconds)
    }
}
