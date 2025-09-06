package com.arikachmad.pebblerun.domain.usecase

import com.arikachmad.pebblerun.domain.entity.*
import com.arikachmad.pebblerun.domain.repository.WorkoutRepository
import com.arikachmad.pebblerun.util.error.PebbleRunError
import com.arikachmad.pebblerun.util.error.Result
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes

/**
 * Unit tests for use cases with mocked dependencies.
 * Satisfies TEST-004: Use case testing with mocked dependencies.
 */
class UseCaseTest {
    
    private class MockWorkoutRepository : WorkoutRepository {
        private val sessions = mutableListOf<WorkoutSession>()
        var shouldFailOnCreate = false
        var shouldFailOnUpdate = false
        
        override suspend fun createSession(session: WorkoutSession): Result<WorkoutSession> {
            return if (shouldFailOnCreate) {
                Result.Error(PebbleRunError.DatabaseError("Mock creation failed"))
            } else {
                sessions.add(session)
                Result.Success(session)
            }
        }
        
        override suspend fun updateSession(session: WorkoutSession): Result<WorkoutSession> {
            return if (shouldFailOnUpdate) {
                Result.Error(PebbleRunError.DatabaseError("Mock update failed"))
            } else {
                val index = sessions.indexOfFirst { it.id == session.id }
                if (index >= 0) {
                    sessions[index] = session
                    Result.Success(session)
                } else {
                    Result.Error(PebbleRunError.SessionNotFoundError("Session not found"))
                }
            }
        }
        
        override suspend fun getSessionById(id: String): Result<WorkoutSession?> {
            val session = sessions.find { it.id == id }
            return Result.Success(session)
        }
        
        override suspend fun getAllSessions(
            limit: Int?,
            offset: Int,
            status: WorkoutStatus?,
            startDate: kotlinx.datetime.Instant?,
            endDate: kotlinx.datetime.Instant?
        ): Result<List<WorkoutSession>> {
            var filtered = sessions.toList()
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
            return Result.Success(filtered)
        }
        
        override fun observeSessions(): kotlinx.coroutines.flow.Flow<List<WorkoutSession>> {
            return flowOf(sessions.toList())
        }
        
        override fun observeSession(id: String): kotlinx.coroutines.flow.Flow<WorkoutSession?> {
            val session = sessions.find { it.id == id }
            return flowOf(session)
        }
        
        override suspend fun getAllSessions(): Result<kotlinx.coroutines.flow.Flow<List<WorkoutSession>>> {
            return Result.Success(flowOf(sessions.toList()))
        }
        
        override suspend fun getSessionsByStatus(status: WorkoutStatus): Result<List<WorkoutSession>> {
            val filtered = sessions.filter { it.status == status }
            return Result.Success(filtered)
        }
        
        override suspend fun deleteSession(id: String): Result<Boolean> {
            val removed = sessions.removeIf { it.id == id }
            return Result.Success(removed)
        }
        
        override suspend fun getSessionStats(id: String): Result<WorkoutSessionStats?> {
            val session = sessions.find { it.id == id } ?: return Result.Success(null)
            val stats = WorkoutSessionStats(
                sessionId = session.id,
                totalDuration = session.totalDuration,
                totalDistance = session.totalDistance,
                averagePace = session.averagePace,
                averageHeartRate = session.averageHeartRate.toInt(),
                maxHeartRate = session.averageHeartRate.toInt() + 10,
                minHeartRate = session.averageHeartRate.toInt() - 10,
                caloriesBurned = session.calories
            )
            return Result.Success(stats)
        }
        
        override suspend fun exportSessions(sessionIds: List<String>): Result<String> {
            return Result.Success("mock-export-data")
        }
        
        override suspend fun importSessions(data: String): Result<List<WorkoutSession>> {
            return Result.Success(emptyList())
        }
        
        override suspend fun searchSessions(query: String, limit: Int): Result<List<WorkoutSession>> {
            val filtered = sessions.filter { 
                it.notes.contains(query, ignoreCase = true) || it.id.contains(query, ignoreCase = true)
            }.take(limit)
            return Result.Success(filtered)
        }
    }
    
    @Test
    fun `StartWorkoutUseCase creates new session successfully`() = runTest {
        val mockRepository = MockWorkoutRepository()
        val useCase = StartWorkoutUseCase(mockRepository)
        
        val result = useCase.execute()
        
        assertTrue(result.isSuccess(), "Should create session successfully")
        val session = result.getOrNull()
        assertNotNull(session, "Session should not be null")
        assertEquals(WorkoutStatus.ACTIVE, session.status)
        assertTrue(session.id.isNotEmpty(), "Session should have an ID")
    }
    
    @Test
    fun `StartWorkoutUseCase fails when repository throws error`() = runTest {
        val mockRepository = MockWorkoutRepository().apply { shouldFailOnCreate = true }
        val useCase = StartWorkoutUseCase(mockRepository)
        
        val result = useCase.execute()
        
        assertTrue(result.isError(), "Should fail when repository fails")
        assertTrue(result.exceptionOrNull() is PebbleRunError.DatabaseError)
    }
    
    @Test
    fun `StartWorkoutUseCase prevents multiple active sessions`() = runTest {
        val mockRepository = MockWorkoutRepository()
        val useCase = StartWorkoutUseCase(mockRepository)
        
        // Create first session
        val firstResult = useCase.execute()
        assertTrue(firstResult.isSuccess(), "First session should be created")
        
        // Try to create second session
        val secondResult = useCase.execute()
        assertTrue(secondResult.isError(), "Second session should fail")
        assertTrue(secondResult.exceptionOrNull() is PebbleRunError.SessionAlreadyActiveError)
    }
    
    @Test
    fun `StopWorkoutUseCase completes active session successfully`() = runTest {
        val mockRepository = MockWorkoutRepository()
        val startUseCase = StartWorkoutUseCase(mockRepository)
        val stopUseCase = StopWorkoutUseCase(mockRepository)
        
        // Start a session first
        val startResult = startUseCase.execute()
        assertTrue(startResult.isSuccess(), "Should start session")
        
        // Stop the session
        val stopResult = stopUseCase.execute()
        assertTrue(stopResult.isSuccess(), "Should stop session successfully")
        
        val stoppedSession = stopResult.getOrNull()
        assertNotNull(stoppedSession, "Stopped session should not be null")
        assertEquals(WorkoutStatus.COMPLETED, stoppedSession.status)
        assertNotNull(stoppedSession.endTime, "Should have end time")
    }
    
    @Test
    fun `StopWorkoutUseCase fails when no active session exists`() = runTest {
        val mockRepository = MockWorkoutRepository()
        val stopUseCase = StopWorkoutUseCase(mockRepository)
        
        val result = stopUseCase.execute()
        
        assertTrue(result.isError(), "Should fail when no active session")
        assertTrue(result.exceptionOrNull() is PebbleRunError.SessionNotFoundError)
    }
    
    @Test
    fun `StopWorkoutUseCase calculates session statistics correctly`() = runTest {
        val mockRepository = MockWorkoutRepository()
        val startUseCase = StartWorkoutUseCase(mockRepository)
        val updateUseCase = UpdateWorkoutDataUseCase(mockRepository)
        val stopUseCase = StopWorkoutUseCase(mockRepository)
        
        // Start session
        val startResult = startUseCase.execute()
        assertTrue(startResult.isSuccess())
        
        // Add some data
        val updateResult = updateUseCase.execute(
            heartRate = 140,
            latitude = 40.7128,
            longitude = -74.0060,
            accuracy = 5.0f
        )
        assertTrue(updateResult.isSuccess())
        
        // Stop session
        val stopResult = stopUseCase.execute()
        assertTrue(stopResult.isSuccess())
        
        val stoppedSession = stopResult.getOrNull()!!
        assertTrue(stoppedSession.totalDuration > 0, "Should have positive duration")
        assertTrue(stoppedSession.geoPoints.isNotEmpty(), "Should have GPS data")
        assertTrue(stoppedSession.hrSamples.isNotEmpty(), "Should have HR data")
    }
    
    @Test
    fun `UpdateWorkoutDataUseCase adds heart rate data successfully`() = runTest {
        val mockRepository = MockWorkoutRepository()
        val startUseCase = StartWorkoutUseCase(mockRepository)
        val updateUseCase = UpdateWorkoutDataUseCase(mockRepository)
        
        // Start session first
        startUseCase.execute()
        
        val result = updateUseCase.execute(heartRate = 145)
        
        assertTrue(result.isSuccess(), "Should update HR data successfully")
        val updatedSession = result.getOrNull()!!
        assertTrue(updatedSession.hrSamples.isNotEmpty(), "Should have HR samples")
        assertEquals(145, updatedSession.hrSamples.last().heartRate)
    }
    
    @Test
    fun `UpdateWorkoutDataUseCase adds GPS data successfully`() = runTest {
        val mockRepository = MockWorkoutRepository()
        val startUseCase = StartWorkoutUseCase(mockRepository)
        val updateUseCase = UpdateWorkoutDataUseCase(mockRepository)
        
        // Start session first
        startUseCase.execute()
        
        val result = updateUseCase.execute(
            latitude = 40.7128,
            longitude = -74.0060,
            accuracy = 5.0f
        )
        
        assertTrue(result.isSuccess(), "Should update GPS data successfully")
        val updatedSession = result.getOrNull()!!
        assertTrue(updatedSession.geoPoints.isNotEmpty(), "Should have GPS points")
        assertEquals(40.7128, updatedSession.geoPoints.last().latitude, 0.0001)
        assertEquals(-74.0060, updatedSession.geoPoints.last().longitude, 0.0001)
    }
    
    @Test
    fun `UpdateWorkoutDataUseCase rejects invalid heart rate`() = runTest {
        val mockRepository = MockWorkoutRepository()
        val startUseCase = StartWorkoutUseCase(mockRepository)
        val updateUseCase = UpdateWorkoutDataUseCase(mockRepository)
        
        // Start session first
        startUseCase.execute()
        
        val result = updateUseCase.execute(heartRate = 300) // Invalid HR
        
        assertTrue(result.isError(), "Should reject invalid heart rate")
        assertTrue(result.exceptionOrNull() is PebbleRunError.ValidationError)
    }
    
    @Test
    fun `UpdateWorkoutDataUseCase fails when no active session`() = runTest {
        val mockRepository = MockWorkoutRepository()
        val updateUseCase = UpdateWorkoutDataUseCase(mockRepository)
        
        val result = updateUseCase.execute(heartRate = 140)
        
        assertTrue(result.isError(), "Should fail when no active session")
        assertTrue(result.exceptionOrNull() is PebbleRunError.SessionNotFoundError)
    }
    
    @Test
    fun `UpdateWorkoutDataUseCase updates session statistics correctly`() = runTest {
        val mockRepository = MockWorkoutRepository()
        val startUseCase = StartWorkoutUseCase(mockRepository)
        val updateUseCase = UpdateWorkoutDataUseCase(mockRepository)
        
        // Start session
        startUseCase.execute()
        
        // Add multiple data points
        updateUseCase.execute(heartRate = 140, latitude = 40.7128, longitude = -74.0060)
        updateUseCase.execute(heartRate = 145, latitude = 40.7138, longitude = -74.0060)
        val finalResult = updateUseCase.execute(heartRate = 142, latitude = 40.7148, longitude = -74.0060)
        
        assertTrue(finalResult.isSuccess())
        val session = finalResult.getOrNull()!!
        
        assertTrue(session.totalDistance > 0, "Should calculate distance")
        assertTrue(session.averageHeartRate > 0, "Should calculate average HR")
        assertEquals(3, session.hrSamples.size, "Should have 3 HR samples")
        assertEquals(3, session.geoPoints.size, "Should have 3 GPS points")
    }
    
    @Test
    fun `UpdateWorkoutDataUseCase handles repository failure gracefully`() = runTest {
        val mockRepository = MockWorkoutRepository().apply { shouldFailOnUpdate = true }
        val startUseCase = StartWorkoutUseCase(mockRepository)
        val updateUseCase = UpdateWorkoutDataUseCase(mockRepository)
        
        // Start session first
        startUseCase.execute()
        
        // Repository is set to fail updates
        val result = updateUseCase.execute(heartRate = 140)
        
        assertTrue(result.isError(), "Should handle repository failure")
        assertTrue(result.exceptionOrNull() is PebbleRunError.DatabaseError)
    }
}
