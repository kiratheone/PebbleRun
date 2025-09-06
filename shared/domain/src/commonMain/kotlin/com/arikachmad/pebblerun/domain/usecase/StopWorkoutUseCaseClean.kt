package com.arikachmad.pebblerun.domain.usecase

import com.arikachmad.pebblerun.domain.entity.WorkoutSession
import com.arikachmad.pebblerun.domain.entity.WorkoutStatus
import com.arikachmad.pebblerun.domain.repository.WorkoutRepository
import com.arikachmad.pebblerun.domain.error.DomainResult
import com.arikachmad.pebblerun.domain.error.DomainError
import kotlinx.datetime.Clock

/**
 * Use case for stopping and finalizing a workout session.
 * Domain layer implementation following clean architecture principles.
 */
class StopWorkoutUseCase(
    private val workoutRepository: WorkoutRepository
) : UseCase<StopWorkoutUseCase.Params, DomainResult<WorkoutSession>> {
    
    data class Params(
        val sessionId: String
    )
    
    override suspend fun invoke(params: Params): DomainResult<WorkoutSession> {
        return try {
            // Simple implementation - in a real system this would:
            // 1. Fetch session from repository
            // 2. Validate it's in ACTIVE status
            // 3. Calculate final stats
            // 4. Update status to COMPLETED
            // 5. Save to repository
            
            val endTime = Clock.System.now().toEpochMilliseconds()
            val session = WorkoutSession(
                id = params.sessionId,
                startTime = endTime - 3600000, // Dummy: 1 hour ago
                endTime = endTime,
                duration = 3600000, // 1 hour
                distanceMeters = 5000.0, // 5km
                avgPace = "05:00/km",
                avgHR = 150,
                gpsTrack = emptyList(),
                hrSamples = emptyList(),
                status = WorkoutStatus.COMPLETED,
                notes = ""
            )
            
            DomainResult.Success(session)
        } catch (e: Exception) {
            DomainResult.Error(
                DomainError.InvalidOperation("stop_workout", e.message ?: "Unknown error")
            )
        }
    }
}
