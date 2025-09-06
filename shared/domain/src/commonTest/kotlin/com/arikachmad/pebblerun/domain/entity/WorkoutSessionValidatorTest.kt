package com.arikachmad.pebblerun.domain.entity

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Unit tests for WorkoutSessionValidator.
 * Satisfies TEST-001: Domain entity validation and business logic testing.
 */
class WorkoutSessionValidatorTest {
    
    private fun createTestSession(
        status: WorkoutStatus = WorkoutStatus.PENDING,
        startTime: Instant = Clock.System.now(),
        endTime: Instant? = null
    ): WorkoutSession {
        return WorkoutSession(
            id = "test-session",
            status = status,
            startTime = startTime,
            endTime = endTime,
            totalDistance = 0.0,
            totalDuration = 0L,
            averageHeartRate = 0.0,
            averagePace = 0.0,
            calories = 0,
            geoPoints = emptyList(),
            hrSamples = emptyList(),
            notes = ""
        )
    }
    
    @Test
    fun `canTransitionTo allows PENDING to ACTIVE transition`() {
        val result = WorkoutSessionValidator.canTransitionTo(
            WorkoutStatus.PENDING,
            WorkoutStatus.ACTIVE
        )
        
        assertTrue(result.isValid, "Should allow PENDING to ACTIVE transition")
    }
    
    @Test
    fun `canTransitionTo allows ACTIVE to PAUSED transition`() {
        val session = createTestSession(
            status = WorkoutStatus.ACTIVE,
            startTime = Clock.System.now().minus(30.seconds)
        )
        
        val result = WorkoutSessionValidator.canTransitionTo(
            WorkoutStatus.ACTIVE,
            WorkoutStatus.PAUSED,
            session
        )
        
        assertTrue(result.isValid, "Should allow ACTIVE to PAUSED transition after 10 seconds")
    }
    
    @Test
    fun `canTransitionTo prevents ACTIVE to PAUSED transition too early`() {
        val session = createTestSession(
            status = WorkoutStatus.ACTIVE,
            startTime = Clock.System.now().minus(5.seconds)
        )
        
        val result = WorkoutSessionValidator.canTransitionTo(
            WorkoutStatus.ACTIVE,
            WorkoutStatus.PAUSED,
            session
        )
        
        assertFalse(result.isValid, "Should prevent ACTIVE to PAUSED transition before 10 seconds")
        assertTrue(result.errorMessage?.contains("10 seconds") == true)
    }
    
    @Test
    fun `canTransitionTo allows PAUSED to ACTIVE transition`() {
        val result = WorkoutSessionValidator.canTransitionTo(
            WorkoutStatus.PAUSED,
            WorkoutStatus.ACTIVE
        )
        
        assertTrue(result.isValid, "Should allow PAUSED to ACTIVE transition")
    }
    
    @Test
    fun `canTransitionTo allows ACTIVE to COMPLETED transition`() {
        val result = WorkoutSessionValidator.canTransitionTo(
            WorkoutStatus.ACTIVE,
            WorkoutStatus.COMPLETED
        )
        
        assertTrue(result.isValid, "Should allow ACTIVE to COMPLETED transition")
    }
    
    @Test
    fun `canTransitionTo prevents COMPLETED to other status transitions`() {
        val result = WorkoutSessionValidator.canTransitionTo(
            WorkoutStatus.COMPLETED,
            WorkoutStatus.ACTIVE
        )
        
        assertFalse(result.isValid, "Should prevent COMPLETED to ACTIVE transition")
        assertTrue(result.errorMessage?.contains("completed session") == true)
    }
    
    @Test
    fun `canTransitionTo allows COMPLETED to COMPLETED transition`() {
        val result = WorkoutSessionValidator.canTransitionTo(
            WorkoutStatus.COMPLETED,
            WorkoutStatus.COMPLETED
        )
        
        assertTrue(result.isValid, "Should allow COMPLETED to COMPLETED transition")
    }
    
    @Test
    fun `canTransitionTo prevents invalid PENDING transitions`() {
        val result = WorkoutSessionValidator.canTransitionTo(
            WorkoutStatus.PENDING,
            WorkoutStatus.PAUSED
        )
        
        assertFalse(result.isValid, "Should prevent PENDING to PAUSED transition")
    }
    
    @Test
    fun `validateSessionCompletion returns warning for short session`() {
        val session = createTestSession(
            status = WorkoutStatus.ACTIVE,
            startTime = Clock.System.now().minus(20.seconds)
        )
        
        val result = WorkoutSessionValidator.validateSessionCompletion(session)
        
        assertTrue(result.hasWarning, "Should warn about short session duration")
        assertTrue(result.errorMessage?.contains("very short") == true)
    }
    
    @Test
    fun `validateSessionCompletion returns warning for session without data`() {
        val session = createTestSession(
            status = WorkoutStatus.ACTIVE,
            startTime = Clock.System.now().minus(5.minutes)
        )
        
        val result = WorkoutSessionValidator.validateSessionCompletion(session)
        
        assertTrue(result.hasWarning, "Should warn about session with no tracking data")
        assertTrue(result.errorMessage?.contains("no tracking data") == true)
    }
    
    @Test
    fun `validateSessionCompletion returns valid for good session`() {
        val baseTime = Clock.System.now()
        val session = WorkoutSession(
            id = "test-session",
            status = WorkoutStatus.ACTIVE,
            startTime = baseTime.minus(10.minutes),
            endTime = null,
            totalDistance = 1000.0,
            totalDuration = 600L,
            averageHeartRate = 140.0,
            averagePace = 360.0,
            calories = 100,
            geoPoints = listOf(
                GeoPoint(40.7128, -74.0060, 5.0f, baseTime.minus(10.minutes)),
                GeoPoint(40.7138, -74.0060, 5.0f, baseTime.minus(5.minutes)),
                GeoPoint(40.7148, -74.0060, 5.0f, baseTime)
            ),
            hrSamples = listOf(
                HRSample(140, baseTime.minus(5.minutes), 0.9f),
                HRSample(145, baseTime.minus(2.minutes), 0.9f),
                HRSample(142, baseTime, 0.9f)
            ),
            notes = ""
        )
        
        val result = WorkoutSessionValidator.validateSessionCompletion(session)
        
        assertTrue(result.isValid, "Should be valid for good session")
        assertFalse(result.hasWarning, "Should not have warnings for good session")
    }
    
    @Test
    fun `validateSessionIntegrity detects invalid end time`() {
        val baseTime = Clock.System.now()
        val session = createTestSession(
            startTime = baseTime,
            endTime = baseTime.minus(1.minutes) // End before start
        )
        
        val result = WorkoutSessionValidator.validateSessionIntegrity(session)
        
        assertFalse(result.isValid, "Should detect invalid end time")
        assertTrue(result.errorMessage?.contains("End time must be after start time") == true)
    }
    
    @Test
    fun `validateSessionIntegrity detects negative distance`() {
        val session = createTestSession().copy(totalDistance = -100.0)
        
        val result = WorkoutSessionValidator.validateSessionIntegrity(session)
        
        assertFalse(result.isValid, "Should detect negative distance")
        assertTrue(result.errorMessage?.contains("distance cannot be negative") == true)
    }
    
    @Test
    fun `validateSessionIntegrity detects negative duration`() {
        val session = createTestSession().copy(totalDuration = -60L)
        
        val result = WorkoutSessionValidator.validateSessionIntegrity(session)
        
        assertFalse(result.isValid, "Should detect negative duration")
        assertTrue(result.errorMessage?.contains("duration cannot be negative") == true)
    }
    
    @Test
    fun `validateSessionIntegrity passes for valid session`() {
        val baseTime = Clock.System.now()
        val session = WorkoutSession(
            id = "test-session",
            status = WorkoutStatus.COMPLETED,
            startTime = baseTime.minus(10.minutes),
            endTime = baseTime,
            totalDistance = 1000.0,
            totalDuration = 600L,
            averageHeartRate = 140.0,
            averagePace = 360.0,
            calories = 100,
            geoPoints = listOf(
                GeoPoint(40.7128, -74.0060, 5.0f, baseTime.minus(10.minutes)),
                GeoPoint(40.7138, -74.0060, 5.0f, baseTime.minus(5.minutes))
            ),
            hrSamples = listOf(
                HRSample(140, baseTime.minus(5.minutes), 0.9f),
                HRSample(145, baseTime, 0.9f)
            ),
            notes = "Good workout"
        )
        
        val result = WorkoutSessionValidator.validateSessionIntegrity(session)
        
        assertTrue(result.isValid, "Should pass validation for valid session")
    }
    
    @Test
    fun `ValidationResult isValid property works correctly`() {
        assertTrue(ValidationResult.Valid.isValid)
        assertTrue(ValidationResult.Warning("test warning").isValid)
        assertFalse(ValidationResult.Invalid("test error").isValid)
    }
    
    @Test
    fun `ValidationResult hasWarning property works correctly`() {
        assertFalse(ValidationResult.Valid.hasWarning)
        assertTrue(ValidationResult.Warning("test warning").hasWarning)
        assertFalse(ValidationResult.Invalid("test error").hasWarning)
    }
    
    @Test
    fun `ValidationResult errorMessage property works correctly`() {
        assertEquals(null, ValidationResult.Valid.errorMessage)
        assertEquals("test warning", ValidationResult.Warning("test warning").errorMessage)
        assertEquals("test error", ValidationResult.Invalid("test error").errorMessage)
    }
}
