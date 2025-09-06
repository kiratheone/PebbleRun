package com.arikachmad.pebblerun.domain.usecase

import com.arikachmad.pebblerun.domain.model.WorkoutSession
import com.arikachmad.pebblerun.domain.repository.WorkoutRepository
import kotlinx.datetime.Clock

/**
 * Use case for stopping a workout session
 */
class StopWorkoutUseCase(
    private val workoutRepository: WorkoutRepository
) {
    suspend fun execute(sessionId: String): WorkoutSession? {
        val session = workoutRepository.getWorkoutSession(sessionId)
        return session?.let {
            val updatedSession = it.copy(
                endTime = Clock.System.now(),
                status = com.arikachmad.pebblerun.domain.model.WorkoutStatus.COMPLETED
            )
            workoutRepository.updateWorkoutSession(updatedSession)
            updatedSession
        }
    }
}
