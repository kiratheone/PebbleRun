package com.arikachmad.pebblerun.domain.optimization

import com.arikachmad.pebblerun.domain.entity.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.minutes

/**
 * Battery optimization manager for PebbleRun app.
 * Implements adaptive strategies to extend battery life during workouts.
 */
class BatteryOptimizationManager {
    
    private val _batteryLevel = MutableStateFlow(100.0)
    val batteryLevel: StateFlow<Double> = _batteryLevel.asStateFlow()
    
    private val _optimizationMode = MutableStateFlow(OptimizationMode.BALANCED)
    val optimizationMode: StateFlow<OptimizationMode> = _optimizationMode.asStateFlow()
    
    private val _powerStats = MutableStateFlow(PowerStats())
    val powerStats: StateFlow<PowerStats> = _powerStats.asStateFlow()
    
    private var lastLocationUpdate = Clock.System.now()
    private var movementState = MovementState.UNKNOWN
    private var hrConfidenceHistory = mutableListOf<Float>()
    
    enum class OptimizationMode {
        POWER_SAVER,    // Maximum battery conservation
        BALANCED,       // Balance between accuracy and battery
        PERFORMANCE,    // Maximum accuracy and responsiveness
        ADAPTIVE        // Automatically adjust based on conditions
    }
    
    enum class MovementState {
        STATIONARY,     // Not moving significantly
        WALKING,        // Slow movement
        RUNNING,        // Fast movement
        UNKNOWN         // Cannot determine
    }
    
    data class PowerStats(
        val bluetoothUsage: Double = 0.0,     // % per hour
        val gpsUsage: Double = 0.0,           // % per hour
        val hrMonitoringUsage: Double = 0.0,  // % per hour
        val backgroundUsage: Double = 0.0,    // % per hour
        val totalUsage: Double = 0.0,         // % per hour
        val estimatedRemainingTime: Double = 0.0 // hours
    )
    
    data class OptimizationStrategy(
        val gpsUpdateInterval: kotlin.time.Duration,
        val hrSamplingInterval: kotlin.time.Duration,
        val bluetoothConnectionInterval: kotlin.time.Duration,
        val backgroundProcessingEnabled: Boolean,
        val locationAccuracyMode: LocationAccuracyMode,
        val hrConfidenceThreshold: Float
    )
    
    enum class LocationAccuracyMode {
        HIGH_ACCURACY,      // GPS + Network + Passive
        BALANCED_POWER,     // GPS when needed, Network otherwise
        POWER_SAVER,        // Network and Passive only
        GPS_ONLY           // GPS only for maximum accuracy
    }
    
    /**
     * Update battery level and recalculate optimization strategies.
     */
    fun updateBatteryLevel(level: Double) {
        _batteryLevel.value = level.coerceIn(0.0, 100.0)
        
        // Automatically switch to power saver mode when battery is low
        if (level < 20.0 && _optimizationMode.value != OptimizationMode.POWER_SAVER) {
            setOptimizationMode(OptimizationMode.POWER_SAVER)
        } else if (level > 50.0 && _optimizationMode.value == OptimizationMode.POWER_SAVER) {
            setOptimizationMode(OptimizationMode.ADAPTIVE)
        }
        
        updatePowerStats()
    }
    
    /**
     * Set optimization mode manually.
     */
    fun setOptimizationMode(mode: OptimizationMode) {
        _optimizationMode.value = mode
        updatePowerStats()
    }
    
    /**
     * Analyze movement patterns to optimize GPS usage.
     */
    fun analyzeMovement(geoPoints: List<GeoPoint>): MovementState {
        if (geoPoints.size < 3) return MovementState.UNKNOWN
        
        val recentPoints = geoPoints.takeLast(5)
        val distances = mutableListOf<Double>()
        
        for (i in 1 until recentPoints.size) {
            val distance = calculateDistance(
                recentPoints[i - 1].latitude, recentPoints[i - 1].longitude,
                recentPoints[i].latitude, recentPoints[i].longitude
            )
            distances.add(distance)
        }
        
        val averageDistance = distances.average()
        val totalTime = (recentPoints.last().timestamp.epochSeconds - 
                        recentPoints.first().timestamp.epochSeconds).toDouble()
        
        if (totalTime > 0) {
            val speed = (distances.sum() / totalTime) * 3.6 // Convert to km/h
            
            movementState = when {
                speed < 1.0 -> MovementState.STATIONARY
                speed < 6.0 -> MovementState.WALKING
                else -> MovementState.RUNNING
            }
        }
        
        return movementState
    }
    
    /**
     * Analyze HR confidence to optimize sampling rate.
     */
    fun analyzeHRConfidence(hrSamples: List<HRSample>) {
        if (hrSamples.isNotEmpty()) {
            val recentConfidences = hrSamples.takeLast(10).map { it.confidence }
            hrConfidenceHistory.addAll(recentConfidences)
            
            // Keep only recent history
            if (hrConfidenceHistory.size > 50) {
                hrConfidenceHistory = hrConfidenceHistory.takeLast(30).toMutableList()
            }
        }
    }
    
    /**
     * Get current optimization strategy based on conditions.
     */
    fun getCurrentStrategy(): OptimizationStrategy {
        return when (_optimizationMode.value) {
            OptimizationMode.POWER_SAVER -> getPowerSaverStrategy()
            OptimizationMode.BALANCED -> getBalancedStrategy()
            OptimizationMode.PERFORMANCE -> getPerformanceStrategy()
            OptimizationMode.ADAPTIVE -> getAdaptiveStrategy()
        }
    }
    
    private fun getPowerSaverStrategy(): OptimizationStrategy {
        return OptimizationStrategy(
            gpsUpdateInterval = 30.seconds,
            hrSamplingInterval = 10.seconds,
            bluetoothConnectionInterval = 60.seconds,
            backgroundProcessingEnabled = false,
            locationAccuracyMode = LocationAccuracyMode.POWER_SAVER,
            hrConfidenceThreshold = 0.6f
        )
    }
    
    private fun getBalancedStrategy(): OptimizationStrategy {
        return OptimizationStrategy(
            gpsUpdateInterval = 10.seconds,
            hrSamplingInterval = 5.seconds,
            bluetoothConnectionInterval = 30.seconds,
            backgroundProcessingEnabled = true,
            locationAccuracyMode = LocationAccuracyMode.BALANCED_POWER,
            hrConfidenceThreshold = 0.4f
        )
    }
    
    private fun getPerformanceStrategy(): OptimizationStrategy {
        return OptimizationStrategy(
            gpsUpdateInterval = 2.seconds,
            hrSamplingInterval = 1.seconds,
            bluetoothConnectionInterval = 10.seconds,
            backgroundProcessingEnabled = true,
            locationAccuracyMode = LocationAccuracyMode.HIGH_ACCURACY,
            hrConfidenceThreshold = 0.2f
        )
    }
    
    private fun getAdaptiveStrategy(): OptimizationStrategy {
        // Adapt based on current conditions
        val batteryLevel = _batteryLevel.value
        val avgHRConfidence = hrConfidenceHistory.takeLastWhile { it > 0 }.average().takeIf { !it.isNaN() } ?: 0.5f
        
        val gpsInterval = when {
            batteryLevel < 30 && movementState == MovementState.STATIONARY -> 60.seconds
            batteryLevel < 30 -> 20.seconds
            movementState == MovementState.STATIONARY -> 15.seconds
            movementState == MovementState.RUNNING -> 5.seconds
            else -> 10.seconds
        }
        
        val hrInterval = when {
            batteryLevel < 20 -> 15.seconds
            avgHRConfidence < 0.3f -> 10.seconds
            avgHRConfidence > 0.8f -> 3.seconds
            else -> 5.seconds
        }
        
        val locationMode = when {
            batteryLevel < 30 -> LocationAccuracyMode.POWER_SAVER
            batteryLevel < 60 -> LocationAccuracyMode.BALANCED_POWER
            else -> LocationAccuracyMode.HIGH_ACCURACY
        }
        
        return OptimizationStrategy(
            gpsUpdateInterval = gpsInterval,
            hrSamplingInterval = hrInterval,
            bluetoothConnectionInterval = 30.seconds,
            backgroundProcessingEnabled = batteryLevel > 20,
            locationAccuracyMode = locationMode,
            hrConfidenceThreshold = if (batteryLevel < 30) 0.6f else 0.3f
        )
    }
    
    /**
     * Calculate estimated workout duration based on current usage.
     */
    fun estimateWorkoutDuration(): Double {
        val currentStats = _powerStats.value
        return if (currentStats.totalUsage > 0) {
            _batteryLevel.value / currentStats.totalUsage
        } else {
            Double.MAX_VALUE
        }
    }
    
    /**
     * Get recommendations for extending battery life.
     */
    fun getBatteryOptimizationRecommendations(): List<String> {
        val recommendations = mutableListOf<String>()
        val batteryLevel = _batteryLevel.value
        val currentStats = _powerStats.value
        
        if (batteryLevel < 50) {
            recommendations.add("Switch to Power Saver mode to extend workout time")
        }
        
        if (currentStats.gpsUsage > 8.0) {
            recommendations.add("GPS usage is high - consider reducing location accuracy")
        }
        
        if (currentStats.bluetoothUsage > 3.0) {
            recommendations.add("Bluetooth usage is high - check Pebble connection stability")
        }
        
        if (movementState == MovementState.STATIONARY) {
            recommendations.add("You're stationary - GPS frequency has been reduced automatically")
        }
        
        val avgHRConfidence = hrConfidenceHistory.takeLastWhile { it > 0 }.average()
        if (!avgHRConfidence.isNaN() && avgHRConfidence < 0.4f) {
            recommendations.add("HR sensor confidence is low - consider adjusting watch position")
        }
        
        if (recommendations.isEmpty()) {
            recommendations.add("Battery optimization is working well - no changes needed")
        }
        
        return recommendations
    }
    
    private fun updatePowerStats() {
        val strategy = getCurrentStrategy()
        val batteryLevel = _batteryLevel.value
        
        // Calculate power usage based on strategy
        val gpsUsage = when (strategy.locationAccuracyMode) {
            LocationAccuracyMode.HIGH_ACCURACY -> 8.0
            LocationAccuracyMode.BALANCED_POWER -> 5.0
            LocationAccuracyMode.POWER_SAVER -> 2.0
            LocationAccuracyMode.GPS_ONLY -> 10.0
        }
        
        val bluetoothUsage = when (strategy.bluetoothConnectionInterval.inWholeSeconds) {
            in 0..15 -> 3.0
            in 16..30 -> 2.0
            else -> 1.0
        }
        
        val hrUsage = when (strategy.hrSamplingInterval.inWholeSeconds) {
            1L -> 2.0
            in 2..5 -> 1.5
            else -> 1.0
        }
        
        val backgroundUsage = if (strategy.backgroundProcessingEnabled) 1.0 else 0.5
        
        val totalUsage = gpsUsage + bluetoothUsage + hrUsage + backgroundUsage
        val estimatedTime = if (totalUsage > 0) batteryLevel / totalUsage else Double.MAX_VALUE
        
        _powerStats.value = PowerStats(
            bluetoothUsage = bluetoothUsage,
            gpsUsage = gpsUsage,
            hrMonitoringUsage = hrUsage,
            backgroundUsage = backgroundUsage,
            totalUsage = totalUsage,
            estimatedRemainingTime = estimatedTime
        )
    }
    
    /**
     * Calculate distance between two GPS coordinates (Haversine formula).
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat/2) * kotlin.math.sin(dLat/2) +
                kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLon/2) * kotlin.math.sin(dLon/2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1-a))
        return R * c
    }
    
    /**
     * Reset optimization state for new workout.
     */
    fun reset() {
        movementState = MovementState.UNKNOWN
        hrConfidenceHistory.clear()
        lastLocationUpdate = Clock.System.now()
        updatePowerStats()
    }
    
    /**
     * Get detailed power analysis report.
     */
    fun generatePowerReport(): String {
        val stats = _powerStats.value
        val batteryLevel = _batteryLevel.value
        val strategy = getCurrentStrategy()
        
        return buildString {
            appendLine("Battery Optimization Report")
            appendLine("===========================")
            appendLine("Current Battery Level: ${batteryLevel.toInt()}%")
            appendLine("Optimization Mode: ${_optimizationMode.value}")
            appendLine("Movement State: $movementState")
            appendLine()
            appendLine("Power Usage (% per hour):")
            appendLine("  GPS: ${stats.gpsUsage}")
            appendLine("  Bluetooth: ${stats.bluetoothUsage}")
            appendLine("  HR Monitoring: ${stats.hrMonitoringUsage}")
            appendLine("  Background: ${stats.backgroundUsage}")
            appendLine("  Total: ${stats.totalUsage}")
            appendLine()
            appendLine("Estimated Remaining Time: ${stats.estimatedRemainingTime.toInt()} hours")
            appendLine()
            appendLine("Current Strategy:")
            appendLine("  GPS Update Interval: ${strategy.gpsUpdateInterval}")
            appendLine("  HR Sampling Interval: ${strategy.hrSamplingInterval}")
            appendLine("  Location Accuracy: ${strategy.locationAccuracyMode}")
            appendLine("  Background Processing: ${strategy.backgroundProcessingEnabled}")
            appendLine()
            appendLine("Recommendations:")
            getBatteryOptimizationRecommendations().forEach { rec ->
                appendLine("  • $rec")
            }
        }
    }
}
