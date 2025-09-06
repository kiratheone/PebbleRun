package com.arikachmad.pebblerun.domain.usecase

import com.arikachmad.pebblerun.domain.entity.WorkoutSession
import com.arikachmad.pebblerun.domain.entity.WorkoutStatus
import com.arikachmad.pebblerun.domain.repository.WorkoutRepository
import com.arikachmad.pebblerun.domain.error.DomainResult
import com.arikachmad.pebblerun.domain.error.DomainError
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Use case for starting a new workout session.
 * Domain layer implementation following clean architecture principles.
 */
class StartWorkoutUseCase(
    private val workoutRepository: WorkoutRepository
) : UseCase<StartWorkoutUseCase.Params, DomainResult<WorkoutSession>> {
    
    data class Params(
        val sessionId: String? = null, // Optional custom ID, generates UUID if null
        val startTime: Instant = Clock.System.now(),
        val notes: String = ""
    )
    
    override suspend fun invoke(params: Params): DomainResult<WorkoutSession> {
        return try {
            // Create a new workout session
            val sessionId = params.sessionId ?: generateSessionId()
            val session = WorkoutSession(
                id = sessionId,
                startTime = params.startTime.toEpochMilliseconds(),
                endTime = null,
                duration = 0L,
                distanceMeters = 0.0,
                avgPace = "00:00/km",
                avgHR = 0,
                gpsTrack = emptyList(),
                hrSamples = emptyList(),
                status = WorkoutStatus.ACTIVE,
                notes = params.notes
            )
            
            // Return success - in a real implementation, this would save to repository
            DomainResult.Success(session)
        } catch (e: Exception) {
            DomainResult.Error(
                DomainError.InvalidOperation("start_workout", e.message ?: "Unknown error")
            )
        }
    }
    
    private fun generateSessionId(): String {
        // Simple UUID generation - in real implementation would use proper UUID
        return "workout_${Clock.System.now().toEpochMilliseconds()}"
    }
}
