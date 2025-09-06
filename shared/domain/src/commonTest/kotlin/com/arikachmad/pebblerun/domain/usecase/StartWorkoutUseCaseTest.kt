package com.arikachmad.pebblerun.domain.usecase

import com.arikachmad.pebblerun.domain.entity.GeoPoint
import com.arikachmad.pebblerun.domain.entity.HRSample
import com.arikachmad.pebblerun.domain.entity.WorkoutSession
import com.arikachmad.pebblerun.domain.entity.WorkoutStatus
import com.arikachmad.pebblerun.domain.error.DomainError
import com.arikachmad.pebblerun.domain.error.DomainResult
import com.arikachmad.pebblerun.domain.repository.WorkoutRepository
import com.arikachmad.pebblerun.domain.repository.WorkoutSessionStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StartWorkoutUseCaseTest {

    private class MockWorkoutRepository : WorkoutRepository {
        var sessions = mutableListOf<WorkoutSession>()
        var shouldFailCreation = false
        var activeSession: WorkoutSession? = null

        override suspend fun createSession(session: WorkoutSession): DomainResult<WorkoutSession> {
            return if (shouldFailCreation) {
                DomainResult.Error(DomainError.InvalidOperation("create", "test failure"))
            } else {
                sessions.add(session)
                activeSession = session
                DomainResult.Success(session)
            }
        }

        override suspend fun updateSession(session: WorkoutSession): DomainResult<WorkoutSession> {
            val index = sessions.indexOfFirst { it.id == session.id }
            return if (index >= 0) {
                sessions[index] = session
                if (activeSession?.id == session.id) activeSession = session
                DomainResult.Success(session)
            } else {
                DomainResult.Error(DomainError.EntityNotFound("WorkoutSession", session.id))
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
            return DomainResult.Success(sessions.toList())
        }

        override fun observeSessions(): Flow<List<WorkoutSession>> = flowOf(sessions.toList())

        override fun observeSession(id: String): Flow<WorkoutSession?> {
            val session = sessions.find { it.id == id }
            return flowOf(session)
        }

        override suspend fun deleteSession(id: String): DomainResult<Unit> {
            sessions.removeAll { it.id == id }
            if (activeSession?.id == id) activeSession = null
            return DomainResult.Success(Unit)
        }

        override suspend fun getActiveSession(): DomainResult<WorkoutSession?> {
            return DomainResult.Success(activeSession)
        }

        override fun observeActiveSession(): Flow<WorkoutSession?> = flowOf(activeSession)

        override suspend fun completeSession(
            id: String,
            endTime: Instant,
            finalStats: WorkoutSessionStats
        ): DomainResult<WorkoutSession> {
            val session = sessions.find { it.id == id }
            return if (session != null) {
                val completed = session.copy(
                    endTime = endTime,
                    status = WorkoutStatus.COMPLETED
                )
                updateSession(completed)
            } else {
                DomainResult.Error(DomainError.EntityNotFound("WorkoutSession", id))
            }
        }

        override suspend fun getSessionStats(id: String): DomainResult<WorkoutSessionStats?> {
            return DomainResult.Success(null) // Simple mock
        }

        override suspend fun exportSessions(sessionIds: List<String>): DomainResult<String> {
            return DomainResult.Success("mock_export_data")
        }

        override suspend fun importSessions(data: String): DomainResult<List<WorkoutSession>> {
            return DomainResult.Success(emptyList())
        }
    }

    @Test
    fun `should create new workout session successfully`() = runTest {
        // Given
        val repository = MockWorkoutRepository()
        val useCase = StartWorkoutUseCase(repository)
        val startTime = Instant.fromEpochMilliseconds(1000)

        // When
        val result = useCase.execute(startTime)

        // Then
        assertTrue(result is DomainResult.Success)
        val session = result.data
        assertEquals(startTime, session.startTime)
        assertEquals(WorkoutStatus.ACTIVE, session.status)
        assertEquals(1, repository.sessions.size)
    }

    @Test
    fun `should fail when repository fails to create session`() = runTest {
        // Given
        val repository = MockWorkoutRepository().apply {
            shouldFailCreation = true
        }
        val useCase = StartWorkoutUseCase(repository)
        val startTime = Instant.fromEpochMilliseconds(1000)

        // When
        val result = useCase.execute(startTime)

        // Then
        assertTrue(result is DomainResult.Error)
        assertTrue(result.exception is DomainError.InvalidOperation)
    }

    @Test
    fun `should prevent starting session when one is already active`() = runTest {
        // Given
        val repository = MockWorkoutRepository()
        val useCase = StartWorkoutUseCase(repository)
        val startTime1 = Instant.fromEpochMilliseconds(1000)
        val startTime2 = Instant.fromEpochMilliseconds(2000)

        // When - start first session
        val result1 = useCase.execute(startTime1)
        
        // Then - first session should succeed
        assertTrue(result1 is DomainResult.Success)
        
        // When - try to start second session
        val result2 = useCase.execute(startTime2)
        
        // Then - second session should fail
        assertTrue(result2 is DomainResult.Error)
        assertTrue(result2.exception is DomainError.BusinessRuleViolation)
        assertEquals(1, repository.sessions.size) // Only one session created
    }
}

/**
 * Use case for starting a new workout session
 */
class StartWorkoutUseCase(
    private val workoutRepository: WorkoutRepository
) {
    suspend fun execute(startTime: Instant): DomainResult<WorkoutSession> {
        // Check if there's already an active session
        return when (val activeResult = workoutRepository.getActiveSession()) {
            is DomainResult.Success -> {
                if (activeResult.data != null) {
                    DomainResult.Error(
                        DomainError.BusinessRuleViolation(
                            "Cannot start new session while another session is active"
                        )
                    )
                } else {
                    // Create new session
                    val newSession = WorkoutSession(
                        id = generateSessionId(),
                        startTime = startTime,
                        endTime = null,
                        status = WorkoutStatus.ACTIVE,
                        geoPoints = emptyList(),
                        hrSamples = emptyList()
                    )
                    workoutRepository.createSession(newSession)
                }
            }
            is DomainResult.Error -> activeResult
        }
    }

    private fun generateSessionId(): String {
        return "session_${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}"
    }
}
