package com.arikachmad.pebblerun.domain.entity

import kotlinx.datetime.Instant

/**
 * Heart rate sample data class for storing HR measurements from Pebble 2 HR device.
 * Satisfies REQ-001 (Real-time HR data collection) and CON-003 (1-second update frequency).
 */
data class HRSample(
    val heartRate: Int, // Heart rate in beats per minute (BPM)
    val timestamp: Instant,
    val quality: HRQuality = HRQuality.GOOD,
    val sessionId: String // Reference to the workout session
) {
    init {
        require(heartRate in 30..220) { "Heart rate must be between 30 and 220 BPM" }
    }
    
    /**
     * Checks if this HR sample is valid for calculations
     * Supports quality control for HR data processing
     */
    val isValid: Boolean
        get() = quality != HRQuality.INVALID && heartRate in 40..200
    
    /**
     * Checks if HR indicates high intensity exercise
     * Useful for workout analysis and zone detection
     */
    fun isHighIntensity(maxHR: Int = 190): Boolean {
        return heartRate > (maxHR * 0.8) // 80% of max HR threshold
    }
    
    /**
     * Calculates HR zone based on age-estimated max HR
     * Supports workout intensity analysis
     */
    fun getHRZone(age: Int): HRZone {
        val maxHR = 220 - age
        val percentage = heartRate.toDouble() / maxHR
        
        return when {
            percentage < 0.5 -> HRZone.RESTING
            percentage < 0.6 -> HRZone.WARM_UP
            percentage < 0.7 -> HRZone.FAT_BURN
            percentage < 0.8 -> HRZone.AEROBIC
            percentage < 0.9 -> HRZone.ANAEROBIC
            else -> HRZone.MAXIMUM
        }
    }
    
    /**
     * Checks if this sample should be included in averages
     * Supports HR averaging and validation logic (TASK-010)
     */
    fun shouldIncludeInAverage(): Boolean {
        return isValid && quality != HRQuality.POOR
    }
}

/**
 * Heart rate quality indicators from Pebble sensor
 * Supports REQ-001 (Real-time HR data collection with quality assessment)
 */
enum class HRQuality {
    EXCELLENT,  // Very reliable reading
    GOOD,       // Normal quality reading
    FAIR,       // Acceptable but not ideal
    POOR,       // Low quality, use with caution
    INVALID     // Should not be used in calculations
}

/**
 * Heart rate training zones
 * Useful for workout analysis and user feedback
 */
enum class HRZone {
    RESTING,    // < 50% max HR
    WARM_UP,    // 50-60% max HR
    FAT_BURN,   // 60-70% max HR
    AEROBIC,    // 70-80% max HR
    ANAEROBIC,  // 80-90% max HR
    MAXIMUM     // > 90% max HR
}

/**
 * Utility class for HR data processing and analysis
 * Supports TASK-010 (HR averaging and validation logic)
 */
object HRProcessor {
    
    /**
     * Calculates average HR from a list of samples, filtering by quality
     * Supports CON-003 (1-second update frequency) with quality control
     */
    fun calculateAverage(samples: List<HRSample>): Int? {
        val validSamples = samples.filter { it.shouldIncludeInAverage() }
        return if (validSamples.isEmpty()) null else validSamples.map { it.heartRate }.average().toInt()
    }
    
    /**
     * Finds maximum HR from valid samples
     */
    fun findMaximum(samples: List<HRSample>): Int? {
        val validSamples = samples.filter { it.isValid }
        return validSamples.maxOfOrNull { it.heartRate }
    }
    
    /**
     * Finds minimum HR from valid samples
     */
    fun findMinimum(samples: List<HRSample>): Int? {
        val validSamples = samples.filter { it.isValid }
        return validSamples.minOfOrNull { it.heartRate }
    }
    
    /**
     * Filters out obvious outliers from HR data
     * Supports data quality improvement for REQ-001
     */
    fun filterOutliers(samples: List<HRSample>, threshold: Double = 2.0): List<HRSample> {
        if (samples.size < 3) return samples
        
        val validSamples = samples.filter { it.isValid }
        if (validSamples.isEmpty()) return emptyList()
        
        val mean = validSamples.map { it.heartRate }.average()
        val stdDev = kotlin.math.sqrt(
            validSamples.map { (it.heartRate - mean) * (it.heartRate - mean) }.average()
        )
        
        return validSamples.filter {
            kotlin.math.abs(it.heartRate - mean) <= threshold * stdDev
        }
    }
}
