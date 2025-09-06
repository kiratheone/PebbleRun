package com.arikachmad.pebblerun.domain.usecase

import com.arikachmad.pebblerun.domain.entity.WorkoutSession
import com.arikachmad.pebblerun.domain.entity.WorkoutStatus
import com.arikachmad.pebblerun.domain.repository.WorkoutRepository
import com.arikachmad.pebblerun.domain.error.DomainResult
import com.arikachmad.pebblerun.domain.error.DomainError
import kotlinx.datetime.Clock

/**
 * Use case for updating workout data during an active session.
 * Domain layer implementation following clean architecture principles.
 */
class UpdateWorkoutDataUseCase(
    private val workoutRepository: WorkoutRepository
) : UseCase<UpdateWorkoutDataUseCase.Params, DomainResult<WorkoutSession>> {
    
    data class Params(
        val sessionId: String,
        val heartRate: Int? = null,
        val pace: String? = null,
        val distance: Double? = null
    )
    
    override suspend fun invoke(params: Params): DomainResult<WorkoutSession> {
        return try {
            // Simple implementation - in a real system this would:
            // 1. Fetch current session from repository
            // 2. Update the specified fields
            // 3. Recalculate derived values (avg HR, avg pace, etc.)
            // 4. Save updated session to repository
            
            val currentTime = Clock.System.now().toEpochMilliseconds()
            val session = WorkoutSession(
                id = params.sessionId,
                startTime = currentTime - 1800000, // 30 minutes ago
                endTime = null,
                duration = 1800000, // 30 minutes
                distanceMeters = params.distance ?: 2500.0,
                avgPace = params.pace ?: "05:00/km",
                avgHR = params.heartRate ?: 145,
                gpsTrack = emptyList(),
                hrSamples = emptyList(),
                status = WorkoutStatus.ACTIVE,
                notes = ""
            )
            
            DomainResult.Success(session)
        } catch (e: Exception) {
            DomainResult.Error(
                DomainError.InvalidOperation("update_workout_data", e.message ?: "Unknown error")
            )
        }
    }
}
