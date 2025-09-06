package com.arikachmad.pebblerun.domain.integration

import com.arikachmad.pebblerun.domain.entity.*
import com.arikachmad.pebblerun.domain.repository.WorkoutRepository
import com.arikachmad.pebblerun.domain.usecase.*
import com.arikachmad.pebblerun.domain.error.DomainResult
import com.arikachmad.pebblerun.domain.error.DomainError
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/**
 * Domain layer integration tests for workout use cases.
 * Tests the interaction between domain entities, use cases and repository interfaces.
 * 
 * IMPORTANT: This test follows clean architecture - it only tests domain logic
 * without dependencies on data layer, bridge implementations, or I/O operations.
 */
class WorkoutDomainIntegrationTest {
    
    // Pure domain mock repository (no external dependencies)
    private class MockWorkoutRepository : WorkoutRepository {
        private val sessions = mutableMapOf<String, WorkoutSession>()
        var simulateFailure = false
        var simulateNotFound = false
        
        override suspend fun createSession(session: WorkoutSession): DomainResult<WorkoutSession> {
            return if (simulateFailure) {
                DomainResult.Error(DomainError.InvalidOperation("create_session", "Mock failure"))
            } else {
                sessions[session.id] = session
                DomainResult.Success(session)
            }
        }
        
        override suspend fun updateSession(session: WorkoutSession): DomainResult<WorkoutSession> {
            return if (simulateFailure) {
                DomainResult.Error(DomainError.InvalidOperation("update_session", "Mock failure"))
            } else if (simulateNotFound || !sessions.containsKey(session.id)) {
                DomainResult.Error(DomainError.NotFound("Session not found"))
            } else {
                sessions[session.id] = session
                DomainResult.Success(session)
            }
        }
        
        override suspend fun getSessionById(id: String): DomainResult<WorkoutSession?> {
            return if (simulateFailure) {
                DomainResult.Error(DomainError.InvalidOperation("get_session", "Mock failure"))
            } else {
                DomainResult.Success(sessions[id])
            }
        }
        
        override suspend fun getAllSessions(
            limit: Int?,
            offset: Int,
            status: WorkoutStatus?,
            startDate: Instant?,
            endDate: Instant?
        ): DomainResult<List<WorkoutSession>> {
            return if (simulateFailure) {
                DomainResult.Error(DomainError.InvalidOperation("get_all_sessions", "Mock failure"))
            } else {
                var filtered = sessions.values.toList()
                if (status != null) {
                    filtered = filtered.filter { it.status == status }
                }
                if (startDate != null) {
                    filtered = filtered.filter { it.startTime >= startDate }
                }
                if (endDate != null) {
                    filtered = filtered.filter { it.endTime != null && it.endTime!! <= endDate }
                }
                if (limit != null) {
                    filtered = filtered.drop(offset).take(limit)
                }
                DomainResult.Success(filtered)
            }
        }
        
        override fun observeSessions(): kotlinx.coroutines.flow.Flow<List<WorkoutSession>> {
            return flowOf(sessions.values.toList())
        }
        
        override fun observeSession(id: String): kotlinx.coroutines.flow.Flow<WorkoutSession?> {
            return flowOf(sessions[id])
        }
        
        override suspend fun getActiveSession(): DomainResult<WorkoutSession?> {
            return if (simulateFailure) {
                DomainResult.Error(DomainError.InvalidOperation("get_active_session", "Mock failure"))
            } else {
                val activeSession = sessions.values.find { it.status == WorkoutStatus.ACTIVE }
                DomainResult.Success(activeSession)
            }
        }
        
        override fun observeActiveSession(): kotlinx.coroutines.flow.Flow<WorkoutSession?> {
            val activeSession = sessions.values.find { it.status == WorkoutStatus.ACTIVE }
            return flowOf(activeSession)
        }
        
        override suspend fun deleteSession(id: String): DomainResult<Unit> {
            return if (simulateFailure) {
                DomainResult.Error(DomainError.InvalidOperation("delete_session", "Mock failure"))
            } else if (sessions.remove(id) != null) {
                DomainResult.Success(Unit)
            } else {
                DomainResult.Error(DomainError.NotFound("Session not found"))
            }
        }
        
        override suspend fun completeSession(
            id: String, 
            endTime: Instant, 
            finalStats: WorkoutSessionStats
        ): DomainResult<WorkoutSession> {
            return if (simulateFailure) {
                DomainResult.Error(DomainError.InvalidOperation("complete_session", "Mock failure"))
            } else {
                val session = sessions[id]
                if (session != null) {
                    val completed = session.copy(
                        endTime = endTime,
                        status = WorkoutStatus.COMPLETED,
                        totalDuration = finalStats.totalDuration,
                        totalDistance = finalStats.totalDistance,
                        averageHeartRate = finalStats.averageHeartRate,
                        maxHeartRate = finalStats.maxHeartRate,
                        calories = finalStats.calories
                    )
                    sessions[id] = completed
                    DomainResult.Success(completed)
                } else {
                    DomainResult.Error(DomainError.NotFound("Session not found"))
                }
            }
        }
        
        fun getAllStoredSessions(): List<WorkoutSession> = sessions.values.toList()
        fun clearAll() = sessions.clear()
        fun getSessionCount() = sessions.size
    }
    
    private lateinit var repository: MockWorkoutRepository
    private lateinit var startWorkoutUseCase: StartWorkoutUseCase
    private lateinit var stopWorkoutUseCase: StopWorkoutUseCase
    private lateinit var updateWorkoutDataUseCase: UpdateWorkoutDataUseCase
    
    @BeforeTest
    fun setup() {
        repository = MockWorkoutRepository()
        startWorkoutUseCase = StartWorkoutUseCase(repository)
        stopWorkoutUseCase = StopWorkoutUseCase(repository)
        updateWorkoutDataUseCase = UpdateWorkoutDataUseCase(repository)
    }
    
    @AfterTest
    fun cleanup() {
        repository.clearAll()
    }
    
    @Test
    fun `complete workout flow from start to finish`() = runTest {
        // Phase 1: Start workout
        val startResult = startWorkoutUseCase.invoke(StartWorkoutUseCase.Params())
        assertTrue(startResult is DomainResult.Success, "Should start workout successfully")
        
        val workoutSession = startResult.value
        assertEquals(WorkoutStatus.ACTIVE, workoutSession.status)
        assertEquals(1, repository.getSessionCount(), "Should have one session")
        
        // Phase 2: Update workout with data (simulating real-time updates)
        val updates = listOf(
            Triple(120, "6:00", 500.0),
            Triple(135, "5:45", 1000.0),
            Triple(142, "5:30", 1500.0),
            Triple(148, "5:20", 2000.0),
            Triple(150, "5:15", 2500.0)
        )
        
        var currentSession = workoutSession
        updates.forEach { (hr, pace, distance) ->
            val updateResult = updateWorkoutDataUseCase.invoke(
                UpdateWorkoutDataUseCase.Params(
                    heartRate = hr,
                    pace = pace,
                    distance = distance
                )
            )
            
            assertTrue(updateResult is DomainResult.Success, "Should update workout data successfully")
            currentSession = updateResult.value
            assertEquals(hr, currentSession.averageHeartRate, "Should update heart rate")
        }
        
        // Phase 3: Stop workout
        val stopResult = stopWorkoutUseCase.invoke(StopWorkoutUseCase.Params(currentSession.id))
        assertTrue(stopResult is DomainResult.Success, "Should stop workout successfully")
        
        val finalSession = stopResult.value
        assertEquals(WorkoutStatus.COMPLETED, finalSession.status)
        assertNotNull(finalSession.endTime, "Should have end time")
        assertTrue(finalSession.totalDuration > 0, "Should have positive duration")
        assertEquals(150, finalSession.averageHeartRate, "Should preserve final heart rate")
    }
    
    @Test
    fun `workout data validation and consistency`() = runTest {
        // Start workout
        val startResult = startWorkoutUseCase.invoke(StartWorkoutUseCase.Params())
        val session = startResult.value
        
        // Test data validation
        val validUpdate = updateWorkoutDataUseCase.invoke(
            UpdateWorkoutDataUseCase.Params(
                heartRate = 150,
                pace = "5:30",
                distance = 1000.0
            )
        )
        assertTrue(validUpdate is DomainResult.Success, "Should accept valid data")
        
        // Test heart rate bounds
        val highHRUpdate = updateWorkoutDataUseCase.invoke(
            UpdateWorkoutDataUseCase.Params(
                heartRate = 250, // Too high
                pace = "5:30",
                distance = 1000.0
            )
        )
        // Note: Actual validation logic would be in the use case implementation
        
        // Test distance progression (should not decrease)
        val currentSession = validUpdate.value
        val regressiveUpdate = updateWorkoutDataUseCase.invoke(
            UpdateWorkoutDataUseCase.Params(
                heartRate = 140,
                pace = "6:00",
                distance = 500.0 // Less than previous 1000.0
            )
        )
        // Note: Business rules for distance regression would be in use case
        
        // Stop and verify final state
        val stopResult = stopWorkoutUseCase.invoke(StopWorkoutUseCase.Params(session.id))
        val finalSession = stopResult.value
        
        assertTrue(finalSession.totalDistance >= 0, "Distance should be non-negative")
        assertTrue(finalSession.totalDuration >= 0, "Duration should be non-negative")
        assertTrue(finalSession.averageHeartRate >= 0, "Average HR should be non-negative")
    }
    
    @Test
    fun `multiple workout sessions do not interfere`() = runTest {
        // Start first workout
        val session1Result = startWorkoutUseCase.invoke(StartWorkoutUseCase.Params())
        val session1 = session1Result.value
        
        // Update first session
        val update1Result = updateWorkoutDataUseCase.invoke(
            UpdateWorkoutDataUseCase.Params(
                heartRate = 140,
                pace = "6:00",
                distance = 1000.0
            )
        )
        val updatedSession1 = update1Result.value
        
        // Stop first session
        val stop1Result = stopWorkoutUseCase.invoke(StopWorkoutUseCase.Params(session1.id))
        val completedSession1 = stop1Result.value
        
        // Start second workout
        val session2Result = startWorkoutUseCase.invoke(StartWorkoutUseCase.Params())
        val session2 = session2Result.value
        
        // Verify sessions are independent
        assertNotEquals(session1.id, session2.id, "Sessions should have different IDs")
        assertEquals(WorkoutStatus.COMPLETED, completedSession1.status)
        assertEquals(WorkoutStatus.ACTIVE, session2.status)
        assertEquals(2, repository.getSessionCount(), "Should have two sessions")
        
        // Update and stop second session
        val update2Result = updateWorkoutDataUseCase.invoke(
            UpdateWorkoutDataUseCase.Params(
                heartRate = 160,
                pace = "5:00",
                distance = 2000.0
            )
        )
        val stop2Result = stopWorkoutUseCase.invoke(StopWorkoutUseCase.Params(session2.id))
        val completedSession2 = stop2Result.value
        
        // Verify both sessions are preserved correctly
        assertEquals(140, completedSession1.averageHeartRate)
        assertEquals(160, completedSession2.averageHeartRate)
        assertEquals(WorkoutStatus.COMPLETED, completedSession1.status)
        assertEquals(WorkoutStatus.COMPLETED, completedSession2.status)
    }
    
    @Test
    fun `error handling throughout workout lifecycle`() = runTest {
        // Test repository failure during start
        repository.simulateFailure = true
        val failedStartResult = startWorkoutUseCase.invoke(StartWorkoutUseCase.Params())
        assertTrue(failedStartResult is DomainResult.Error, "Should handle start failure")
        
        // Reset and start successfully
        repository.simulateFailure = false
        val startResult = startWorkoutUseCase.invoke(StartWorkoutUseCase.Params())
        val session = startResult.value
        
        // Test repository failure during update
        repository.simulateFailure = true
        val failedUpdateResult = updateWorkoutDataUseCase.invoke(
            UpdateWorkoutDataUseCase.Params(
                heartRate = 150,
                pace = "5:30",
                distance = 1000.0
            )
        )
        assertTrue(failedUpdateResult is DomainResult.Error, "Should handle update failure")
        
        // Test update with non-existent session
        repository.simulateFailure = false
        repository.simulateNotFound = true
        val notFoundUpdateResult = updateWorkoutDataUseCase.invoke(
            UpdateWorkoutDataUseCase.Params(
                heartRate = 150,
                pace = "5:30", 
                distance = 1000.0
            )
        )
        assertTrue(notFoundUpdateResult is DomainResult.Error, "Should handle not found error")
        
        // Reset and test successful stop
        repository.simulateNotFound = false
        val stopResult = stopWorkoutUseCase.invoke(StopWorkoutUseCase.Params(session.id))
        assertTrue(stopResult is DomainResult.Success, "Should stop successfully when conditions are normal")
    }
    
    @Test
    fun `workout session state transitions are valid`() = runTest {
        // Start workout - should be ACTIVE
        val startResult = startWorkoutUseCase.invoke(StartWorkoutUseCase.Params())
        val activeSession = startResult.value
        assertEquals(WorkoutStatus.ACTIVE, activeSession.status)
        
        // Update during ACTIVE state - should remain ACTIVE
        val updateResult = updateWorkoutDataUseCase.invoke(
            UpdateWorkoutDataUseCase.Params(
                heartRate = 150,
                pace = "5:30",
                distance = 1000.0
            )
        )
        val updatedSession = updateResult.value
        assertEquals(WorkoutStatus.ACTIVE, updatedSession.status)
        
        // Stop workout - should be COMPLETED
        val stopResult = stopWorkoutUseCase.invoke(StopWorkoutUseCase.Params(activeSession.id))
        val completedSession = stopResult.value
        assertEquals(WorkoutStatus.COMPLETED, completedSession.status)
        
        // Verify time progression
        assertTrue(activeSession.startTime <= updatedSession.startTime, "Start time should be consistent")
        assertNotNull(completedSession.endTime, "Completed session should have end time")
        if (completedSession.endTime != null) {
            assertTrue(completedSession.startTime <= completedSession.endTime!!, 
                "End time should be after start time")
        }
    }
    
    @Test
    fun `repository operations maintain data consistency`() = runTest {
        // Create multiple sessions
        val session1 = startWorkoutUseCase.invoke(StartWorkoutUseCase.Params()).value
        val session2 = startWorkoutUseCase.invoke(StartWorkoutUseCase.Params()).value
        val session3 = startWorkoutUseCase.invoke(StartWorkoutUseCase.Params()).value
        
        // Stop session 2 only
        stopWorkoutUseCase.invoke(StopWorkoutUseCase.Params(session2.id))
        
        // Verify repository state
        val allSessions = repository.getAllStoredSessions()
        assertEquals(3, allSessions.size, "Should have all three sessions")
        
        val activeCount = allSessions.count { it.status == WorkoutStatus.ACTIVE }
        val completedCount = allSessions.count { it.status == WorkoutStatus.COMPLETED }
        
        assertEquals(2, activeCount, "Should have two active sessions")
        assertEquals(1, completedCount, "Should have one completed session")
        
        // Test getting active session
        val activeSessionResult = repository.getActiveSession()
        assertTrue(activeSessionResult is DomainResult.Success)
        assertNotNull(activeSessionResult.value, "Should have an active session")
        assertTrue(activeSessionResult.value!!.status == WorkoutStatus.ACTIVE)
    }
}
