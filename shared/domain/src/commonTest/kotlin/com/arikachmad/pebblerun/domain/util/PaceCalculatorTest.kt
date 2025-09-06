package com.arikachmad.pebblerun.domain.util

import com.arikachmad.pebblerun.domain.entity.GeoPoint
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Unit tests for pace calculation algorithms.
 * Satisfies TEST-002: Pace calculation algorithm verification with various GPS inputs.
 */
class PaceCalculatorTest {
    
    @Test
    fun `calculatePace returns correct pace for normal running speed`() {
        // Distance of 1 km in 5 minutes (12 km/h = reasonable running pace)
        val distance = 1000.0 // meters
        val time = 300.0 // seconds (5 minutes)
        
        val pace = PaceCalculator.calculatePace(distance, time)
        
        // Expected pace: 300 seconds per 1000 meters = 5 minutes per km
        assertEquals(300.0, pace, 0.1)
    }
    
    @Test
    fun `calculatePace returns correct pace for slow walking`() {
        // Distance of 1 km in 15 minutes (4 km/h = slow walking)
        val distance = 1000.0 // meters
        val time = 900.0 // seconds (15 minutes)
        
        val pace = PaceCalculator.calculatePace(distance, time)
        
        // Expected pace: 900 seconds per 1000 meters = 15 minutes per km
        assertEquals(900.0, pace, 0.1)
    }
    
    @Test
    fun `calculatePace returns zero for zero distance`() {
        val distance = 0.0
        val time = 300.0
        
        val pace = PaceCalculator.calculatePace(distance, time)
        
        assertEquals(0.0, pace)
    }
    
    @Test
    fun `calculatePace returns zero for zero time`() {
        val distance = 1000.0
        val time = 0.0
        
        val pace = PaceCalculator.calculatePace(distance, time)
        
        assertEquals(0.0, pace)
    }
    
    @Test
    fun `calculateDistance returns correct distance for two GPS points`() {
        // Approximate coordinates for 1km distance
        val point1 = GeoPoint(
            latitude = 40.7128, // NYC latitude
            longitude = -74.0060, // NYC longitude
            accuracy = 5.0f,
            timestamp = Clock.System.now()
        )
        
        val point2 = GeoPoint(
            latitude = 40.7218, // ~1km north
            longitude = -74.0060, // same longitude
            accuracy = 5.0f,
            timestamp = Clock.System.now()
        )
        
        val distance = PaceCalculator.calculateDistance(point1, point2)
        
        // Should be approximately 1000 meters (1 degree latitude ≈ 111km)
        assertTrue(distance > 900.0 && distance < 1100.0, "Distance should be ~1000m, got $distance")
    }
    
    @Test
    fun `calculateDistance returns zero for same point`() {
        val point = GeoPoint(
            latitude = 40.7128,
            longitude = -74.0060,
            accuracy = 5.0f,
            timestamp = Clock.System.now()
        )
        
        val distance = PaceCalculator.calculateDistance(point, point)
        
        assertEquals(0.0, distance, 0.001)
    }
    
    @Test
    fun `calculateTotalDistance sums GPS points correctly`() {
        val baseTime = Clock.System.now()
        val points = listOf(
            GeoPoint(40.7128, -74.0060, 5.0f, baseTime),
            GeoPoint(40.7138, -74.0060, 5.0f, baseTime.plus(10.seconds)), // ~111m north
            GeoPoint(40.7148, -74.0060, 5.0f, baseTime.plus(20.seconds)), // another ~111m north
            GeoPoint(40.7158, -74.0060, 5.0f, baseTime.plus(30.seconds))  // another ~111m north
        )
        
        val totalDistance = PaceCalculator.calculateTotalDistance(points)
        
        // Should be approximately 333 meters (3 segments of ~111m each)
        assertTrue(totalDistance > 250.0 && totalDistance < 450.0, 
            "Total distance should be ~333m, got $totalDistance")
    }
    
    @Test
    fun `calculateTotalDistance returns zero for single point`() {
        val points = listOf(
            GeoPoint(40.7128, -74.0060, 5.0f, Clock.System.now())
        )
        
        val totalDistance = PaceCalculator.calculateTotalDistance(points)
        
        assertEquals(0.0, totalDistance)
    }
    
    @Test
    fun `calculateTotalDistance returns zero for empty list`() {
        val totalDistance = PaceCalculator.calculateTotalDistance(emptyList())
        
        assertEquals(0.0, totalDistance)
    }
    
    @Test
    fun `calculateAveragePace returns correct average from multiple segments`() {
        val segments = listOf(
            PaceCalculator.PaceSegment(distance = 1000.0, time = 300.0), // 5 min/km
            PaceCalculator.PaceSegment(distance = 1000.0, time = 400.0), // 6:40 min/km
            PaceCalculator.PaceSegment(distance = 1000.0, time = 350.0)  // 5:50 min/km
        )
        
        val averagePace = PaceCalculator.calculateAveragePace(segments)
        
        // Total: 3000m in 1050s = 350 seconds per 1000m
        assertEquals(350.0, averagePace, 0.1)
    }
    
    @Test
    fun `calculateAveragePace handles zero distance segments`() {
        val segments = listOf(
            PaceCalculator.PaceSegment(distance = 1000.0, time = 300.0),
            PaceCalculator.PaceSegment(distance = 0.0, time = 100.0), // Stationary
            PaceCalculator.PaceSegment(distance = 1000.0, time = 400.0)
        )
        
        val averagePace = PaceCalculator.calculateAveragePace(segments)
        
        // Should ignore zero distance segment: 2000m in 700s = 350 seconds per 1000m
        assertEquals(350.0, averagePace, 0.1)
    }
    
    @Test
    fun `formatPaceAsMinutesSeconds returns correct format`() {
        val paceInSeconds = 350.0 // 5:50 per km
        
        val formatted = PaceCalculator.formatPaceAsMinutesSeconds(paceInSeconds)
        
        assertEquals("5:50", formatted)
    }
    
    @Test
    fun `formatPaceAsMinutesSeconds handles hours correctly`() {
        val paceInSeconds = 3900.0 // 65 minutes = 1:05:00 per km
        
        val formatted = PaceCalculator.formatPaceAsMinutesSeconds(paceInSeconds)
        
        assertEquals("65:00", formatted) // Over an hour but still shows as minutes
    }
    
    @Test
    fun `formatPaceAsMinutesSeconds handles zero pace`() {
        val formatted = PaceCalculator.formatPaceAsMinutesSeconds(0.0)
        
        assertEquals("0:00", formatted)
    }
    
    @Test
    fun `calculateCurrentPace returns recent pace from GPS points`() {
        val baseTime = Clock.System.now()
        val points = listOf(
            GeoPoint(40.7128, -74.0060, 5.0f, baseTime.minus(60.seconds)),
            GeoPoint(40.7138, -74.0060, 5.0f, baseTime.minus(50.seconds)),
            GeoPoint(40.7148, -74.0060, 5.0f, baseTime.minus(40.seconds)),
            GeoPoint(40.7158, -74.0060, 5.0f, baseTime.minus(30.seconds)),
            GeoPoint(40.7168, -74.0060, 5.0f, baseTime.minus(20.seconds)),
            GeoPoint(40.7178, -74.0060, 5.0f, baseTime.minus(10.seconds)),
            GeoPoint(40.7188, -74.0060, 5.0f, baseTime) // Most recent
        )
        
        val currentPace = PaceCalculator.calculateCurrentPace(points, windowSeconds = 30)
        
        // Should calculate pace from last 30 seconds of data
        assertTrue(currentPace > 0.0, "Current pace should be positive, got $currentPace")
    }
    
    @Test
    fun `calculateCurrentPace returns zero for insufficient data`() {
        val baseTime = Clock.System.now()
        val points = listOf(
            GeoPoint(40.7128, -74.0060, 5.0f, baseTime)
        )
        
        val currentPace = PaceCalculator.calculateCurrentPace(points, windowSeconds = 30)
        
        assertEquals(0.0, currentPace)
    }
}
