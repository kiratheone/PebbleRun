package com.arikachmad.pebblerun.domain.usecase

import com.arikachmad.pebblerun.domain.model.WorkoutSession
import com.arikachmad.pebblerun.domain.repository.WorkoutRepository

/**
 * Use case for updating workout data during a session
 */
class UpdateWorkoutDataUseCase(
    private val workoutRepository: WorkoutRepository
) {
    suspend fun execute(sessionId: String, updateData: WorkoutUpdateData): WorkoutSession? {
        val session = workoutRepository.getWorkoutSession(sessionId)
        return session?.let {
            val updatedSession = it.copy(
                totalDuration = updateData.totalDuration ?: it.totalDuration,
                totalDistance = updateData.totalDistance ?: it.totalDistance,
                averagePace = updateData.averagePace ?: it.averagePace,
                averageHeartRate = updateData.averageHeartRate ?: it.averageHeartRate,
                maxHeartRate = updateData.maxHeartRate ?: it.maxHeartRate,
                calories = updateData.calories ?: it.calories,
                hrSamples = updateData.hrSamples ?: it.hrSamples,
                geoPoints = updateData.geoPoints ?: it.geoPoints
            )
            workoutRepository.updateWorkoutSession(updatedSession)
            updatedSession
        }
    }
}

/**
 * Data class for workout updates
 */
data class WorkoutUpdateData(
    val totalDuration: Long? = null,
    val totalDistance: Double? = null,
    val averagePace: Double? = null,
    val averageHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val calories: Int? = null,
    val hrSamples: List<com.arikachmad.pebblerun.domain.model.HRSample>? = null,
    val geoPoints: List<com.arikachmad.pebblerun.domain.model.GeoPoint>? = null
)
