package com.arikachmad.pebblerun.domain.util

import com.arikachmad.pebblerun.domain.entity.HRSample
import kotlinx.datetime.Instant

/**
 * Heart rate data processing utilities for workout tracking.
 * Satisfies REQ-001 (Real-time HR data collection) and CON-003 (1-second update frequency).
 * Follows PAT-001 (Domain-driven design) by keeping HR logic in domain layer.
 */
object HRProcessor {
    
    // HR validation constants based on physiological limits
    private const val MIN_VALID_HR = 30
    private const val MAX_VALID_HR = 220
    private const val MAX_HR_CHANGE_PER_SECOND = 10 // Max BPM change per second
    
    /**
     * Validates heart rate reading for physiological plausibility
     */
    fun isValidHeartRate(heartRate: Int): Boolean {
        return heartRate in MIN_VALID_HR..MAX_VALID_HR
    }
    
    /**
     * Validates HR sample for sudden changes that might indicate sensor error
     */
    fun isValidHRTransition(previousHR: Int, currentHR: Int, timeDiffSeconds: Long): Boolean {
        if (!isValidHeartRate(previousHR) || !isValidHeartRate(currentHR)) {
            return false
        }
        
        if (timeDiffSeconds <= 0) return false
        
        val maxAllowedChange = MAX_HR_CHANGE_PER_SECOND * timeDiffSeconds
        val actualChange = kotlin.math.abs(currentHR - previousHR)
        
        return actualChange <= maxAllowedChange
    }
    
    /**
     * Filters out invalid HR samples based on validation rules
     */
    fun filterValidSamples(samples: List<HRSample>): List<HRSample> {
        if (samples.isEmpty()) return emptyList()
        
        val validSamples = mutableListOf<HRSample>()
        
        // First sample validation
        if (isValidHeartRate(samples.first().heartRate)) {
            validSamples.add(samples.first())
        }
        
        // Subsequent samples validation
        for (i in 1 until samples.size) {
            val current = samples[i]
            val previous = validSamples.lastOrNull() ?: continue
            
            val timeDiff = current.timestamp.epochSeconds - previous.timestamp.epochSeconds
            
            if (isValidHRTransition(previous.heartRate, current.heartRate, timeDiff)) {
                validSamples.add(current)
            }
        }
        
        return validSamples
    }
    
    /**
     * Calculates average heart rate from a list of samples
     */
    fun calculateAverageHR(samples: List<HRSample>): Int {
        val validSamples = filterValidSamples(samples)
        if (validSamples.isEmpty()) return 0
        
        return validSamples.map { it.heartRate }.average().toInt()
    }
    
    /**
     * Calculates maximum heart rate from a list of samples
     */
    fun calculateMaxHR(samples: List<HRSample>): Int {
        val validSamples = filterValidSamples(samples)
        return validSamples.maxOfOrNull { it.heartRate } ?: 0
    }
    
    /**
     * Calculates minimum heart rate from a list of samples
     */
    fun calculateMinHR(samples: List<HRSample>): Int {
        val validSamples = filterValidSamples(samples)
        return validSamples.minOfOrNull { it.heartRate } ?: 0
    }
    
    /**
     * Calculates moving average HR over a specified time window
     * Useful for smoothing real-time display values
     */
    fun calculateMovingAverageHR(
        samples: List<HRSample>,
        windowSeconds: Long = 30
    ): Int {
        if (samples.isEmpty()) return 0
        
        val latestTimestamp = samples.maxOfOrNull { it.timestamp } ?: return 0
        val cutoffTime = Instant.fromEpochSeconds(latestTimestamp.epochSeconds - windowSeconds)
        
        val recentSamples = samples.filter { it.timestamp >= cutoffTime }
        return calculateAverageHR(recentSamples)
    }
    
    /**
     * Detects HR zones based on common fitness standards
     * Returns zone number (1-5) where 1 is light activity and 5 is maximum effort
     */
    fun getHRZone(currentHR: Int, maxHR: Int): Int {
        if (!isValidHeartRate(currentHR) || maxHR <= 0) return 0
        
        val percentage = (currentHR.toDouble() / maxHR) * 100
        
        return when {
            percentage < 60 -> 1  // Light activity
            percentage < 70 -> 2  // Moderate activity
            percentage < 80 -> 3  // Vigorous activity
            percentage < 90 -> 4  // Hard activity
            else -> 5             // Maximum effort
        }
    }
    
    /**
     * Estimates maximum heart rate based on age (Tanaka formula)
     * More accurate than the traditional 220-age formula
     */
    fun estimateMaxHR(age: Int): Int {
        if (age < 10 || age > 100) return 0
        return (208 - (0.7 * age)).toInt()
    }
    
    /**
     * Calculates HR variability metrics from consecutive samples
     * Returns standard deviation of HR values
     */
    fun calculateHRVariability(samples: List<HRSample>): Double {
        val validSamples = filterValidSamples(samples)
        if (validSamples.size < 2) return 0.0
        
        val heartRates = validSamples.map { it.heartRate.toDouble() }
        val mean = heartRates.average()
        val variance = heartRates.map { (it - mean) * (it - mean) }.average()
        
        return kotlin.math.sqrt(variance)
    }
    
    /**
     * Generates HR summary statistics for a workout session
     */
    data class HRSummary(
        val averageHR: Int,
        val maxHR: Int,
        val minHR: Int,
        val hrVariability: Double,
        val validSampleCount: Int,
        val totalSampleCount: Int,
        val dataQuality: Double // Percentage of valid samples
    )
    
    /**
     * Calculates comprehensive HR summary for a workout session
     */
    fun calculateHRSummary(samples: List<HRSample>): HRSummary {
        val validSamples = filterValidSamples(samples)
        
        return HRSummary(
            averageHR = calculateAverageHR(samples),
            maxHR = calculateMaxHR(samples),
            minHR = calculateMinHR(samples),
            hrVariability = calculateHRVariability(samples),
            validSampleCount = validSamples.size,
            totalSampleCount = samples.size,
            dataQuality = if (samples.isEmpty()) 0.0 else (validSamples.size.toDouble() / samples.size) * 100
        )
    }
}
