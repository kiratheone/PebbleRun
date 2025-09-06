package com.arikachmad.pebblerun.domain.usecase

import com.arikachmad.pebblerun.domain.model.WorkoutSession
import com.arikachmad.pebblerun.domain.repository.WorkoutRepository
import kotlinx.datetime.Clock

/**
 * Use case for starting a workout session
 */
class StartWorkoutUseCase(
    private val workoutRepository: WorkoutRepository
) {
    suspend fun execute(): WorkoutSession {
        // Create a new workout session
        val session = WorkoutSession(
            id = generateId(),
            startTime = Clock.System.now(),
            status = com.arikachmad.pebblerun.domain.model.WorkoutStatus.ACTIVE,
            totalDuration = 0,
            totalDistance = 0.0,
            averagePace = 0.0,
            averageHeartRate = 0,
            maxHeartRate = 0,
            calories = 0,
            hrSamples = emptyList(),
            geoPoints = emptyList()
        )
        
        workoutRepository.saveWorkoutSession(session)
        return session
    }
    
    suspend fun pauseWorkout(sessionId: String) {
        val session = workoutRepository.getWorkoutSession(sessionId)
        session?.let {
            val updatedSession = it.copy(
                status = com.arikachmad.pebblerun.domain.model.WorkoutStatus.PAUSED
            )
            workoutRepository.updateWorkoutSession(updatedSession)
        }
    }
    
    suspend fun resumeWorkout(sessionId: String) {
        val session = workoutRepository.getWorkoutSession(sessionId)
        session?.let {
            val updatedSession = it.copy(
                status = com.arikachmad.pebblerun.domain.model.WorkoutStatus.ACTIVE
            )
            workoutRepository.updateWorkoutSession(updatedSession)
        }
    }
    
    private fun generateId(): String {
        return "workout_${Clock.System.now().toEpochMilliseconds()}"
    }
}
