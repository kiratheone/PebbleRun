package com.arikachmad.pebblerun.domain.usecase

import com.arikachmad.pebblerun.domain.entity.*
import com.arikachmad.pebblerun.domain.repository.WorkoutRepository
import com.arikachmad.pebblerun.domain.error.DomainResult
import com.arikachmad.pebblerun.domain.error.DomainError
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.*

/**
 * Unit tests for use cases with mocked dependencies.
 * Domain layer implementation following clean architecture principles.
 */
class UseCaseTest {
    
    private class MockWorkoutRepository : WorkoutRepository {
        private val sessions = mutableListOf<WorkoutSession>()
        var shouldFailOnCreate = false
        var shouldFailOnUpdate = false
        
        override suspend fun createSession(session: WorkoutSession): DomainResult<WorkoutSession> {
            return if (shouldFailOnCreate) {
                DomainResult.Error(DomainError.InvalidOperation("create_session", "Mock creation failed"))
            } else {
                sessions.add(session)
                DomainResult.Success(session)
            }
        }
        
        override suspend fun updateSession(session: WorkoutSession): DomainResult<WorkoutSession> {
            return if (shouldFailOnUpdate) {
                DomainResult.Error(DomainError.InvalidOperation("update_session", "Mock update failed"))
            } else {
                val index = sessions.indexOfFirst { it.id == session.id }
                if (index >= 0) {
                    sessions[index] = session
                    DomainResult.Success(session)
                } else {
                    DomainResult.Error(DomainError.NotFound("Session not found"))
                }
            }
        }
        
        override suspend fun getSessionById(id: String): DomainResult<WorkoutSession?> {
            val session = sessions.find { it.id == id }
            return DomainResult.Success(session)
        }
        
        override suspend fun getAllSessions(
            limit: Int?,
            offset: Int,
            status: WorkoutStatus?,
            startDate: Instant?,
            endDate: Instant?
        ): DomainResult<List<WorkoutSession>> {
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
            return DomainResult.Success(filtered)
        }
        
        override fun observeSessions(): kotlinx.coroutines.flow.Flow<List<WorkoutSession>> {
            return flowOf(sessions.toList())
        }
        
        override fun observeSession(id: String): kotlinx.coroutines.flow.Flow<WorkoutSession?> {
            val session = sessions.find { it.id == id }
            return flowOf(session)
        }
        
        override suspend fun getActiveSession(): DomainResult<WorkoutSession?> {
            val activeSession = sessions.find { it.status == WorkoutStatus.ACTIVE }
            return DomainResult.Success(activeSession)
        }
        
        override fun observeActiveSession(): kotlinx.coroutines.flow.Flow<WorkoutSession?> {
            val activeSession = sessions.find { it.status == WorkoutStatus.ACTIVE }
            return flowOf(activeSession)
        }
        
        override suspend fun deleteSession(id: String): DomainResult<Unit> {
            val removed = sessions.removeIf { it.id == id }
            return if (removed) {
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
            val index = sessions.indexOfFirst { it.id == id }
            return if (index >= 0) {
                val updated = sessions[index].copy(
                    endTime = endTime,
                    status = WorkoutStatus.COMPLETED,
                    totalDuration = finalStats.totalDuration,
                    totalDistance = finalStats.totalDistance,
                    averageHeartRate = finalStats.averageHeartRate,
                    maxHeartRate = finalStats.maxHeartRate,
                    calories = finalStats.calories
                )
                sessions[index] = updated
                DomainResult.Success(updated)
            } else {
                DomainResult.Error(DomainError.NotFound("Session not found"))
            }
        }
    }
    
    @Test
    fun testStartWorkoutUseCase() = runTest {
        val repository = MockWorkoutRepository()
        val useCase = StartWorkoutUseCase(repository)
        
        val result = useCase.invoke(StartWorkoutUseCase.Params())
        
        assertTrue(result is DomainResult.Success)
        val session = result.value
        assertEquals(WorkoutStatus.ACTIVE, session.status)
        assertNotNull(session.id)
    }
    
    @Test
    fun testStopWorkoutUseCase() = runTest {
        val repository = MockWorkoutRepository()
        val useCase = StopWorkoutUseCase(repository)
        
        val result = useCase.invoke(StopWorkoutUseCase.Params("test-session-id"))
        
        assertTrue(result is DomainResult.Success)
        val session = result.value
        assertEquals(WorkoutStatus.COMPLETED, session.status)
        assertNotNull(session.endTime)
    }
    
    @Test
    fun testUpdateWorkoutDataUseCase() = runTest {
        val repository = MockWorkoutRepository()
        val useCase = UpdateWorkoutDataUseCase(repository)
        
        val result = useCase.invoke(
            UpdateWorkoutDataUseCase.Params(
                heartRate = 150,
                pace = "5:00",
                distance = 1000.0
            )
        )
        
        assertTrue(result is DomainResult.Success)
        val session = result.value
        assertEquals(150, session.averageHeartRate)
    }
    
    @Test
    fun testStartWorkoutFailsWhenRepositoryFails() = runTest {
        val repository = MockWorkoutRepository().apply { shouldFailOnCreate = true }
        val useCase = StartWorkoutUseCase(repository)
        
        val result = useCase.invoke(StartWorkoutUseCase.Params())
        
        assertTrue(result is DomainResult.Error)
    }
    
    @Test
    fun testUpdateWorkoutFailsWhenRepositoryFails() = runTest {
        val repository = MockWorkoutRepository().apply { shouldFailOnUpdate = true }
        val useCase = UpdateWorkoutDataUseCase(repository)
        
        val result = useCase.invoke(
            UpdateWorkoutDataUseCase.Params(
                heartRate = 150,
                pace = "5:00",
                distance = 1000.0
            )
        )
        
        assertTrue(result is DomainResult.Error)
    }
}
