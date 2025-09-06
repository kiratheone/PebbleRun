package com.arikachmad.pebblerun.domain.util

import com.arikachmad.pebblerun.domain.entity.HRSample
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Unit tests for heart rate averaging and validation logic.
 * Satisfies TEST-003: HR averaging and validation logic testing.
 */
class HRValidatorTest {
    
    @Test
    fun `isValidHeartRate accepts normal resting heart rate`() {
        assertTrue(HRValidator.isValidHeartRate(65))
        assertTrue(HRValidator.isValidHeartRate(75))
        assertTrue(HRValidator.isValidHeartRate(85))
    }
    
    @Test
    fun `isValidHeartRate accepts exercise heart rate`() {
        assertTrue(HRValidator.isValidHeartRate(120))
        assertTrue(HRValidator.isValidHeartRate(150))
        assertTrue(HRValidator.isValidHeartRate(180))
    }
    
    @Test
    fun `isValidHeartRate rejects too low heart rate`() {
        assertFalse(HRValidator.isValidHeartRate(30))
        assertFalse(HRValidator.isValidHeartRate(39))
    }
    
    @Test
    fun `isValidHeartRate rejects too high heart rate`() {
        assertFalse(HRValidator.isValidHeartRate(221))
        assertFalse(HRValidator.isValidHeartRate(250))
    }
    
    @Test
    fun `isValidHeartRate handles boundary values`() {
        assertTrue(HRValidator.isValidHeartRate(40))  // Min valid
        assertTrue(HRValidator.isValidHeartRate(220)) // Max valid
        assertFalse(HRValidator.isValidHeartRate(39)) // Just below min
        assertFalse(HRValidator.isValidHeartRate(221)) // Just above max
    }
    
    @Test
    fun `calculateAverageHeartRate returns correct average`() {
        val baseTime = Clock.System.now()
        val samples = listOf(
            HRSample(heartRate = 80, timestamp = baseTime.minus(30.seconds), confidence = 0.9f),
            HRSample(heartRate = 85, timestamp = baseTime.minus(20.seconds), confidence = 0.8f),
            HRSample(heartRate = 90, timestamp = baseTime.minus(10.seconds), confidence = 0.95f),
            HRSample(heartRate = 75, timestamp = baseTime, confidence = 0.85f)
        )
        
        val average = HRValidator.calculateAverageHeartRate(samples)
        
        // (80 + 85 + 90 + 75) / 4 = 82.5
        assertEquals(82.5, average, 0.1)
    }
    
    @Test
    fun `calculateAverageHeartRate returns zero for empty list`() {
        val average = HRValidator.calculateAverageHeartRate(emptyList())
        
        assertEquals(0.0, average)
    }
    
    @Test
    fun `calculateAverageHeartRate filters invalid readings`() {
        val baseTime = Clock.System.now()
        val samples = listOf(
            HRSample(heartRate = 80, timestamp = baseTime.minus(30.seconds), confidence = 0.9f),
            HRSample(heartRate = 250, timestamp = baseTime.minus(20.seconds), confidence = 0.8f), // Invalid
            HRSample(heartRate = 90, timestamp = baseTime.minus(10.seconds), confidence = 0.95f),
            HRSample(heartRate = 30, timestamp = baseTime, confidence = 0.85f) // Invalid
        )
        
        val average = HRValidator.calculateAverageHeartRate(samples)
        
        // Only valid readings: (80 + 90) / 2 = 85
        assertEquals(85.0, average, 0.1)
    }
    
    @Test
    fun `calculateWeightedAverageHeartRate considers confidence`() {
        val baseTime = Clock.System.now()
        val samples = listOf(
            HRSample(heartRate = 80, timestamp = baseTime.minus(30.seconds), confidence = 1.0f),  // High confidence
            HRSample(heartRate = 100, timestamp = baseTime.minus(20.seconds), confidence = 0.1f), // Low confidence
            HRSample(heartRate = 90, timestamp = baseTime.minus(10.seconds), confidence = 0.9f),  // High confidence
        )
        
        val weightedAverage = HRValidator.calculateWeightedAverageHeartRate(samples)
        
        // Should be closer to 80 and 90 than to 100 due to confidence weighting
        assertTrue(weightedAverage < 90.0, "Weighted average should be less than 90, got $weightedAverage")
        assertTrue(weightedAverage > 80.0, "Weighted average should be greater than 80, got $weightedAverage")
    }
    
    @Test
    fun `calculateMovingAverageHeartRate returns recent values`() {
        val baseTime = Clock.System.now()
        val samples = listOf(
            HRSample(heartRate = 70, timestamp = baseTime.minus(90.seconds), confidence = 0.9f), // Too old
            HRSample(heartRate = 80, timestamp = baseTime.minus(50.seconds), confidence = 0.9f),
            HRSample(heartRate = 85, timestamp = baseTime.minus(30.seconds), confidence = 0.8f),
            HRSample(heartRate = 90, timestamp = baseTime.minus(10.seconds), confidence = 0.95f),
            HRSample(heartRate = 75, timestamp = baseTime, confidence = 0.85f)
        )
        
        val movingAverage = HRValidator.calculateMovingAverageHeartRate(samples, windowSeconds = 60)
        
        // Should only include samples from last 60 seconds: (80 + 85 + 90 + 75) / 4 = 82.5
        assertEquals(82.5, movingAverage, 0.1)
    }
    
    @Test
    fun `detectHeartRateSpike identifies sudden increases`() {
        val baseTime = Clock.System.now()
        val samples = listOf(
            HRSample(heartRate = 80, timestamp = baseTime.minus(40.seconds), confidence = 0.9f),
            HRSample(heartRate = 82, timestamp = baseTime.minus(30.seconds), confidence = 0.9f),
            HRSample(heartRate = 85, timestamp = baseTime.minus(20.seconds), confidence = 0.9f),
            HRSample(heartRate = 150, timestamp = baseTime.minus(10.seconds), confidence = 0.9f), // Spike
            HRSample(heartRate = 88, timestamp = baseTime, confidence = 0.9f)
        )
        
        val spikeDetected = HRValidator.detectHeartRateSpike(samples, thresholdBpm = 30)
        
        assertTrue(spikeDetected, "Should detect heart rate spike")
    }
    
    @Test
    fun `detectHeartRateSpike ignores gradual increases`() {
        val baseTime = Clock.System.now()
        val samples = listOf(
            HRSample(heartRate = 80, timestamp = baseTime.minus(40.seconds), confidence = 0.9f),
            HRSample(heartRate = 90, timestamp = baseTime.minus(30.seconds), confidence = 0.9f),
            HRSample(heartRate = 100, timestamp = baseTime.minus(20.seconds), confidence = 0.9f),
            HRSample(heartRate = 110, timestamp = baseTime.minus(10.seconds), confidence = 0.9f),
            HRSample(heartRate = 120, timestamp = baseTime, confidence = 0.9f)
        )
        
        val spikeDetected = HRValidator.detectHeartRateSpike(samples, thresholdBpm = 30)
        
        assertFalse(spikeDetected, "Should not detect spike for gradual increase")
    }
    
    @Test
    fun `calculateHeartRateZone returns correct zone for age`() {
        val age = 30
        val heartRate = 150
        
        val zone = HRValidator.calculateHeartRateZone(heartRate, age)
        
        // For 30-year-old: Max HR ≈ 190, so 150 should be in Zone 3 (70-80% of max)
        assertEquals(HRValidator.HeartRateZone.AEROBIC, zone)
    }
    
    @Test
    fun `calculateHeartRateZone handles different zones correctly`() {
        val age = 40 // Max HR ≈ 180
        
        assertEquals(HRValidator.HeartRateZone.RESTING, HRValidator.calculateHeartRateZone(90, age))   // 50% of max
        assertEquals(HRValidator.HeartRateZone.WARMUP, HRValidator.calculateHeartRateZone(108, age))  // 60% of max
        assertEquals(HRValidator.HeartRateZone.AEROBIC, HRValidator.calculateHeartRateZone(135, age)) // 75% of max
        assertEquals(HRValidator.HeartRateZone.THRESHOLD, HRValidator.calculateHeartRateZone(153, age)) // 85% of max
        assertEquals(HRValidator.HeartRateZone.MAXIMUM, HRValidator.calculateHeartRateZone(171, age)) // 95% of max
    }
    
    @Test
    fun `smoothHeartRateData removes outliers`() {
        val baseTime = Clock.System.now()
        val samples = listOf(
            HRSample(heartRate = 80, timestamp = baseTime.minus(50.seconds), confidence = 0.9f),
            HRSample(heartRate = 82, timestamp = baseTime.minus(40.seconds), confidence = 0.9f),
            HRSample(heartRate = 200, timestamp = baseTime.minus(30.seconds), confidence = 0.3f), // Outlier
            HRSample(heartRate = 85, timestamp = baseTime.minus(20.seconds), confidence = 0.9f),
            HRSample(heartRate = 30, timestamp = baseTime.minus(10.seconds), confidence = 0.2f),  // Outlier
            HRSample(heartRate = 88, timestamp = baseTime, confidence = 0.9f)
        )
        
        val smoothed = HRValidator.smoothHeartRateData(samples)
        
        // Should remove the outliers
        assertTrue(smoothed.size < samples.size, "Smoothed data should have fewer samples")
        assertTrue(smoothed.all { it.heartRate in 40..220 }, "All smoothed values should be valid")
        assertTrue(smoothed.all { it.confidence >= 0.5f }, "All smoothed values should have good confidence")
    }
    
    @Test
    fun `calculateHeartRateVariability returns positive value for varied data`() {
        val baseTime = Clock.System.now()
        val samples = listOf(
            HRSample(heartRate = 75, timestamp = baseTime.minus(50.seconds), confidence = 0.9f),
            HRSample(heartRate = 80, timestamp = baseTime.minus(40.seconds), confidence = 0.9f),
            HRSample(heartRate = 85, timestamp = baseTime.minus(30.seconds), confidence = 0.9f),
            HRSample(heartRate = 78, timestamp = baseTime.minus(20.seconds), confidence = 0.9f),
            HRSample(heartRate = 82, timestamp = baseTime.minus(10.seconds), confidence = 0.9f),
        )
        
        val hrv = HRValidator.calculateHeartRateVariability(samples)
        
        assertTrue(hrv > 0.0, "HRV should be positive for varied data, got $hrv")
    }
    
    @Test
    fun `calculateHeartRateVariability returns zero for constant data`() {
        val baseTime = Clock.System.now()
        val samples = listOf(
            HRSample(heartRate = 80, timestamp = baseTime.minus(40.seconds), confidence = 0.9f),
            HRSample(heartRate = 80, timestamp = baseTime.minus(30.seconds), confidence = 0.9f),
            HRSample(heartRate = 80, timestamp = baseTime.minus(20.seconds), confidence = 0.9f),
            HRSample(heartRate = 80, timestamp = baseTime.minus(10.seconds), confidence = 0.9f),
        )
        
        val hrv = HRValidator.calculateHeartRateVariability(samples)
        
        assertEquals(0.0, hrv, 0.001)
    }
}
