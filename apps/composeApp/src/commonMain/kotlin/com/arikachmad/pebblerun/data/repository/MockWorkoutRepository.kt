package com.arikachmad.pebblerun.data.repository

import com.arikachmad.pebblerun.domain.model.*
import com.arikachmad.pebblerun.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Mock implementation of WorkoutRepository for testing UI
 */
class MockWorkoutRepository : WorkoutRepository {
    private val _workoutSessions = MutableStateFlow(generateMockWorkouts())
    
    override fun getAllWorkoutSessions(): Flow<List<WorkoutSession>> {
        return _workoutSessions.asStateFlow()
    }
    
    override suspend fun getWorkoutSession(id: String): WorkoutSession? {
        return _workoutSessions.value.find { it.id == id }
    }
    
    override suspend fun saveWorkoutSession(session: WorkoutSession) {
        val current = _workoutSessions.value.toMutableList()
        current.add(session)
        _workoutSessions.value = current
    }
    
    override suspend fun updateWorkoutSession(session: WorkoutSession) {
        val current = _workoutSessions.value.toMutableList()
        val index = current.indexOfFirst { it.id == session.id }
        if (index != -1) {
            current[index] = session
            _workoutSessions.value = current
        }
    }
    
    override suspend fun deleteWorkoutSession(id: String) {
        val current = _workoutSessions.value.toMutableList()
        current.removeAll { it.id == id }
        _workoutSessions.value = current
    }
    
    private fun generateMockWorkouts(): List<WorkoutSession> {
        val now = Clock.System.now()
        
        return listOf(
            WorkoutSession(
                id = "workout_1",
                startTime = now.minus(2.minutes),
                endTime = now.minus(1.minutes),
                status = WorkoutStatus.COMPLETED,
                totalDuration = 60000, // 1 minute
                totalDistance = 500.0, // 500 meters
                averagePace = 120.0, // 2 min/km (very fast for testing)
                averageHeartRate = 140,
                maxHeartRate = 165,
                calories = 50,
                hrSamples = generateMockHRSamples(now.minus(2.minutes), 60),
                geoPoints = generateMockGeoPoints(now.minus(2.minutes), 60),
                notes = "Quick test run"
            ),
            WorkoutSession(
                id = "workout_2",
                startTime = now.minus(1.minutes),
                endTime = null,
                status = WorkoutStatus.ACTIVE,
                totalDuration = 30000, // 30 seconds
                totalDistance = 200.0, // 200 meters
                averagePace = 150.0, // 2.5 min/km
                averageHeartRate = 130,
                maxHeartRate = 145,
                calories = 25,
                hrSamples = generateMockHRSamples(now.minus(1.minutes), 30),
                geoPoints = generateMockGeoPoints(now.minus(1.minutes), 30),
                notes = "Current workout in progress"
            )
        )
    }
    
    private fun generateMockHRSamples(startTime: kotlinx.datetime.Instant, durationSeconds: Int): List<HRSample> {
        val samples = mutableListOf<HRSample>()
        var baseHR = 120
        
        for (i in 0 until durationSeconds) {
            val timestamp = startTime.plus(i.seconds)
            val variation = Random.nextInt(-10, 11)
            val hr = (baseHR + variation).coerceIn(100, 180)
            samples.add(HRSample(timestamp, hr))
            
            // Gradually increase HR over time (simulate exercise)
            if (i % 10 == 0) baseHR = (baseHR + Random.nextInt(0, 3)).coerceAtMost(170)
        }
        
        return samples
    }
    
    private fun generateMockGeoPoints(startTime: kotlinx.datetime.Instant, durationSeconds: Int): List<GeoPoint> {
        val points = mutableListOf<GeoPoint>()
        var lat = 37.7749 // San Francisco starting point
        var lon = -122.4194
        
        for (i in 0 until durationSeconds step 5) { // GPS point every 5 seconds
            val timestamp = startTime.plus(i.seconds)
            
            // Simulate movement (very small increments)
            lat += Random.nextDouble(-0.0001, 0.0001)
            lon += Random.nextDouble(-0.0001, 0.0001)
            
            points.add(
                GeoPoint(
                    timestamp = timestamp,
                    latitude = lat,
                    longitude = lon,
                    altitude = Random.nextDouble(10.0, 50.0),
                    accuracy = Random.nextFloat() * 5f + 2f
                )
            )
        }
        
        return points
    }
}
