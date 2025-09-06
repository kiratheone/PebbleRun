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

class UpdateWorkoutDataUseCaseTest {

    private class MockWorkoutRepository : WorkoutRepository {
        var sessions = mutableListOf<WorkoutSession>()
        var activeSession: WorkoutSession? = null

        override suspend fun createSession(session: WorkoutSession): DomainResult<WorkoutSession> {
            sessions.add(session)
            activeSession = session
            return DomainResult.Success(session)
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
        ): DomainResult<List<WorkoutSession>> = DomainResult.Success(sessions.toList())

        override fun observeSessions(): Flow<List<WorkoutSession>> = flowOf(sessions.toList())
        override fun observeSession(id: String): Flow<WorkoutSession?> = flowOf(sessions.find { it.id == id })
        override suspend fun deleteSession(id: String): DomainResult<Unit> = DomainResult.Success(Unit)
        override suspend fun getActiveSession(): DomainResult<WorkoutSession?> = DomainResult.Success(activeSession)
        override fun observeActiveSession(): Flow<WorkoutSession?> = flowOf(activeSession)

        override suspend fun completeSession(id: String, endTime: Instant, finalStats: WorkoutSessionStats): DomainResult<WorkoutSession> {
            return DomainResult.Success(sessions.first { it.id == id })
        }

        override suspend fun getSessionStats(id: String): DomainResult<WorkoutSessionStats?> = DomainResult.Success(null)
        override suspend fun exportSessions(sessionIds: List<String>): DomainResult<String> = DomainResult.Success("")
        override suspend fun importSessions(data: String): DomainResult<List<WorkoutSession>> = DomainResult.Success(emptyList())
    }

    @Test
    fun `should update workout session with new GPS point`() = runTest {
        // Given
        val repository = MockWorkoutRepository()
        val useCase = UpdateWorkoutDataUseCase(repository)
        
        val session = WorkoutSession(
            id = "test_session",
            startTime = Instant.fromEpochMilliseconds(1000),
            endTime = null,
            status = WorkoutStatus.ACTIVE,
            geoPoints = emptyList(),
            hrSamples = emptyList()
        )
        repository.createSession(session)

        val newGpsPoint = GeoPoint(
            latitude = 37.7749,
            longitude = -122.4194,
            timestamp = Instant.fromEpochMilliseconds(2000),
            accuracy = 5.0f
        )

        // When
        val result = useCase.addGpsPoint("test_session", newGpsPoint)

        // Then
        assertTrue(result is DomainResult.Success)
        val updatedSession = result.data
        assertEquals(1, updatedSession.geoPoints.size)
        assertEquals(newGpsPoint, updatedSession.geoPoints.first())
    }

    @Test
    fun `should update workout session with new heart rate sample`() = runTest {
        // Given
        val repository = MockWorkoutRepository()
        val useCase = UpdateWorkoutDataUseCase(repository)
        
        val session = WorkoutSession(
            id = "test_session",
            startTime = Instant.fromEpochMilliseconds(1000),
            endTime = null,
            status = WorkoutStatus.ACTIVE,
            geoPoints = emptyList(),
            hrSamples = emptyList()
        )
        repository.createSession(session)

        val hrSample = HRSample(
            heartRate = 150,
            timestamp = Instant.fromEpochMilliseconds(2000),
            sessionId = "test_session"
        )

        // When
        val result = useCase.addHeartRateSample("test_session", hrSample)

        // Then
        assertTrue(result is DomainResult.Success)
        val updatedSession = result.data
        assertEquals(1, updatedSession.hrSamples.size)
        assertEquals(hrSample, updatedSession.hrSamples.first())
    }

    @Test
    fun `should fail when session does not exist`() = runTest {
        // Given
        val repository = MockWorkoutRepository()
        val useCase = UpdateWorkoutDataUseCase(repository)
        
        val newGpsPoint = GeoPoint(
            latitude = 37.7749,
            longitude = -122.4194,
            timestamp = Instant.fromEpochMilliseconds(2000),
            accuracy = 5.0f
        )

        // When
        val result = useCase.addGpsPoint("nonexistent_session", newGpsPoint)

        // Then
        assertTrue(result is DomainResult.Error)
        assertTrue(result.exception is DomainError.EntityNotFound)
    }
}

/**
 * Use case for updating workout session data during active tracking
 */
class UpdateWorkoutDataUseCase(
    private val workoutRepository: WorkoutRepository
) {
    suspend fun addGpsPoint(sessionId: String, gpsPoint: GeoPoint): DomainResult<WorkoutSession> {
        return when (val sessionResult = workoutRepository.getSessionById(sessionId)) {
            is DomainResult.Success -> {
                val session = sessionResult.data
                if (session == null) {
                    DomainResult.Error(DomainError.EntityNotFound("WorkoutSession", sessionId))
                } else {
                    val updatedSession = session.copy(
                        geoPoints = session.geoPoints + gpsPoint
                    )
                    workoutRepository.updateSession(updatedSession)
                }
            }
            is DomainResult.Error -> sessionResult
        }
    }

    suspend fun addHeartRateSample(sessionId: String, hrSample: HRSample): DomainResult<WorkoutSession> {
        return when (val sessionResult = workoutRepository.getSessionById(sessionId)) {
            is DomainResult.Success -> {
                val session = sessionResult.data
                if (session == null) {
                    DomainResult.Error(DomainError.EntityNotFound("WorkoutSession", sessionId))
                } else {
                    val updatedSession = session.copy(
                        hrSamples = session.hrSamples + hrSample
                    )
                    workoutRepository.updateSession(updatedSession)
                }
            }
            is DomainResult.Error -> sessionResult
        }
    }
}
