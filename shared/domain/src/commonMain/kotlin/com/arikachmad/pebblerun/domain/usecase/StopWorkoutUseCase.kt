package com.arikachmad.pebblerun.domain.usecase

import com.arikachmad.pebblerun.domain.entity.WorkoutSession
import com.arikachmad.pebblerun.domain.entity.WorkoutStatus
import com.arikachmad.pebblerun.domain.repository.WorkoutRepository
import com.arikachmad.pebblerun.domain.util.HRProcessor
import com.arikachmad.pebblerun.domain.util.PaceCalculator
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Use case for stopping and finalizing a workout session.
 * Satisfies TASK-012 (StopWorkoutUseCase with data persistence) and REQ-005 (Local storage of workout data).
 * Follows PAT-001 (Domain-driven design with use cases).
 */
class StopWorkoutUseCase(
    private val workoutRepository: WorkoutRepository
) : UseCase<StopWorkoutUseCase.Params, Result<WorkoutSession>> {
    
    data class Params(
        val sessionId: String,
        val endTime: Instant = Clock.System.now(),
        val notes: String? = null // Optional completion notes
    )
    
    override suspend fun invoke(params: Params): Result<WorkoutSession> {
        return try {
            // Get the session to stop
            val session = workoutRepository.getSessionById(params.sessionId).getOrElse {
                return Result.failure(Exception("Failed to retrieve session"))
            } ?: return Result.failure(IllegalArgumentException("Session not found: ${params.sessionId}"))
            
            // Validate session can be stopped
            if (session.status !in listOf(WorkoutStatus.ACTIVE, WorkoutStatus.PAUSED)) {
                return Result.failure(
                    IllegalStateException("Cannot stop session: Session status is ${session.status}")
                )
            }
            
            // Calculate final session statistics
            val finalSession = calculateFinalStatistics(session, params.endTime, params.notes)
            
            // Complete the session
            val completedSession = finalSession.withStatus(WorkoutStatus.COMPLETED, params.endTime)
            
            // Persist the completed session
            workoutRepository.updateSession(completedSession)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Calculates final workout statistics before completion
     */
    private fun calculateFinalStatistics(
        session: WorkoutSession,
        endTime: Instant,
        additionalNotes: String?
    ): WorkoutSession {
        // Calculate final duration
        val finalDuration = endTime.epochSeconds - session.startTime.epochSeconds
        
        // Calculate final distance and pace
        val finalDistance = if (session.geoPoints.isNotEmpty()) {
            PaceCalculator.calculateTotalDistance(session.geoPoints)
        } else {
            session.totalDistance
        }
        
        val finalPace = if (finalDistance > 0 && finalDuration > 0) {
            PaceCalculator.calculatePace(finalDistance, finalDuration)
        } else {
            session.averagePace
        }
        
        // Calculate final HR statistics
        val hrSummary = HRProcessor.calculateHRSummary(session.hrSamples)
        
        // Estimate calories burned (simple formula)
        val estimatedCalories = estimateCaloriesBurned(
            durationMinutes = finalDuration / 60.0,
            averageHR = hrSummary.averageHR,
            distanceKm = finalDistance / 1000.0
        )
        
        // Update notes
        val finalNotes = if (additionalNotes != null) {
            if (session.notes.isEmpty()) additionalNotes
            else "${session.notes}\n$additionalNotes"
        } else {
            session.notes
        }
        
        return session.copy(
            totalDuration = finalDuration,
            totalDistance = finalDistance,
            averagePace = finalPace,
            averageHeartRate = hrSummary.averageHR,
            maxHeartRate = hrSummary.maxHR,
            minHeartRate = hrSummary.minHR,
            calories = estimatedCalories,
            notes = finalNotes
        )
    }
    
    /**
     * Simple calorie estimation based on duration, HR, and distance
     * Uses METs (Metabolic Equivalent of Task) calculation
     */
    private fun estimateCaloriesBurned(
        durationMinutes: Double,
        averageHR: Int,
        distanceKm: Double
    ): Int {
        if (durationMinutes <= 0) return 0
        
        // Base metabolic rate (assuming 70kg person, 1.2 METs at rest)
        val baseCaloriesPerMinute = 1.4
        
        // Estimate METs based on pace if distance available
        val mets = if (distanceKm > 0) {
            val paceMinutesPerKm = durationMinutes / distanceKm
            when {
                paceMinutesPerKm > 8.0 -> 3.5  // Walking
                paceMinutesPerKm > 6.0 -> 6.0  // Jogging
                paceMinutesPerKm > 5.0 -> 8.0  // Running
                paceMinutesPerKm > 4.0 -> 11.0 // Fast running
                else -> 14.0                   // Very fast running
            }
        } else {
            // Estimate METs based on HR if no distance
            when {
                averageHR < 100 -> 3.0
                averageHR < 120 -> 5.0
                averageHR < 140 -> 7.0
                averageHR < 160 -> 10.0
                else -> 12.0
            }
        }
        
        return (mets * baseCaloriesPerMinute * durationMinutes).toInt()
    }
}

/**
 * Use case for canceling a workout session without saving data.
 * Supports user ability to discard unwanted sessions.
 */
class CancelWorkoutUseCase(
    private val workoutRepository: WorkoutRepository
) : UseCase<CancelWorkoutUseCase.Params, Result<Boolean>> {
    
    data class Params(
        val sessionId: String,
        val deleteSession: Boolean = true // Whether to delete the session entirely
    )
    
    override suspend fun invoke(params: Params): Result<Boolean> {
        return try {
            // Get the session to cancel
            val session = workoutRepository.getSessionById(params.sessionId).getOrElse {
                return Result.failure(Exception("Failed to retrieve session"))
            } ?: return Result.failure(IllegalArgumentException("Session not found: ${params.sessionId}"))
            
            // Validate session can be cancelled
            if (session.status == WorkoutStatus.COMPLETED) {
                return Result.failure(
                    IllegalStateException("Cannot cancel completed session")
                )
            }
            
            if (params.deleteSession) {
                // Delete the session entirely
                workoutRepository.deleteSession(params.sessionId)
                    .map { true }
            } else {
                // Mark as cancelled/stopped without statistics
                val cancelledSession = session.copy(
                    status = WorkoutStatus.COMPLETED,
                    endTime = Clock.System.now(),
                    notes = session.notes + if (session.notes.isEmpty()) "Cancelled" else "\nCancelled"
                )
                
                workoutRepository.updateSession(cancelledSession)
                    .map { true }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
