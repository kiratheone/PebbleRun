package com.arikachmad.pebblerun.domain.usecase

import com.arikachmad.pebblerun.domain.entity.WorkoutSession
import com.arikachmad.pebblerun.domain.entity.WorkoutStatus
import com.arikachmad.pebblerun.domain.repository.WorkoutRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Use case for starting a new workout session.
 * Satisfies REQ-003 (Auto-launch PebbleRun watchapp) and TASK-011 (StartWorkoutUseCase implementation).
 * Follows PAT-001 (Domain-driven design with use cases).
 */
class StartWorkoutUseCase(
    private val workoutRepository: WorkoutRepository
) : UseCase<StartWorkoutUseCase.Params, Result<WorkoutSession>> {
    
    data class Params(
        val sessionId: String? = null, // Optional custom ID, generates UUID if null
        val startTime: Instant = Clock.System.now(),
        val notes: String = ""
    )
    
    override suspend fun invoke(params: Params): Result<WorkoutSession> {
        return try {
            // Validate that no active session exists
            val activeSessions = workoutRepository.getAllSessions(
                status = WorkoutStatus.ACTIVE
            ).getOrElse { 
                return Result.failure(Exception("Failed to check for active sessions"))
            }
            
            if (activeSessions.isNotEmpty()) {
                return Result.failure(
                    IllegalStateException("Cannot start new workout: Active session already exists")
                )
            }
            
            // Check for paused sessions
            val pausedSessions = workoutRepository.getAllSessions(
                status = WorkoutStatus.PAUSED
            ).getOrElse { 
                return Result.failure(Exception("Failed to check for paused sessions"))
            }
            
            if (pausedSessions.isNotEmpty()) {
                return Result.failure(
                    IllegalStateException("Cannot start new workout: Paused session exists. Resume or stop it first.")
                )
            }
            
            // Generate session ID if not provided
            val sessionId = params.sessionId ?: generateSessionId()
            
            // Create new workout session
            val newSession = WorkoutSession(
                id = sessionId,
                startTime = params.startTime,
                endTime = null,
                status = WorkoutStatus.ACTIVE,
                totalDuration = 0L,
                totalDistance = 0.0,
                averagePace = 0.0,
                averageHeartRate = 0,
                maxHeartRate = 0,
                minHeartRate = 0,
                calories = 0,
                geoPoints = emptyList(),
                hrSamples = emptyList(),
                notes = params.notes
            )
            
            // Persist the session
            workoutRepository.createSession(newSession)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Generates a unique session ID using timestamp and random component
     * Format: "workout_YYYYMMDD_HHMMSS_XXXX"
     */
    private fun generateSessionId(): String {
        val now = Clock.System.now()
        val epochSeconds = now.epochSeconds
        val randomSuffix = (1000..9999).random()
        return "workout_${epochSeconds}_$randomSuffix"
    }
}

/**
 * Use case for resuming a paused workout session.
 * Supports session state management and CON-003 (1-second update frequency).
 */
class ResumeWorkoutUseCase(
    private val workoutRepository: WorkoutRepository
) : UseCase<ResumeWorkoutUseCase.Params, Result<WorkoutSession>> {
    
    data class Params(
        val sessionId: String,
        val resumeTime: Instant = Clock.System.now()
    )
    
    override suspend fun invoke(params: Params): Result<WorkoutSession> {
        return try {
            // Get the session to resume
            val session = workoutRepository.getSessionById(params.sessionId).getOrElse {
                return Result.failure(Exception("Failed to retrieve session"))
            } ?: return Result.failure(IllegalArgumentException("Session not found: ${params.sessionId}"))
            
            // Validate session can be resumed
            if (session.status != WorkoutStatus.PAUSED) {
                return Result.failure(
                    IllegalStateException("Cannot resume session: Session status is ${session.status}, expected PAUSED")
                )
            }
            
            // Resume the session
            val resumedSession = session.withStatus(WorkoutStatus.ACTIVE, params.resumeTime)
            
            // Update in repository
            workoutRepository.updateSession(resumedSession)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Use case for pausing an active workout session.
 * Supports REQ-004 (Background tracking capability) with pause/resume functionality.
 */
class PauseWorkoutUseCase(
    private val workoutRepository: WorkoutRepository
) : UseCase<PauseWorkoutUseCase.Params, Result<WorkoutSession>> {
    
    data class Params(
        val sessionId: String,
        val pauseTime: Instant = Clock.System.now()
    )
    
    override suspend fun invoke(params: Params): Result<WorkoutSession> {
        return try {
            // Get the session to pause
            val session = workoutRepository.getSessionById(params.sessionId).getOrElse {
                return Result.failure(Exception("Failed to retrieve session"))
            } ?: return Result.failure(IllegalArgumentException("Session not found: ${params.sessionId}"))
            
            // Validate session can be paused
            if (session.status != WorkoutStatus.ACTIVE) {
                return Result.failure(
                    IllegalStateException("Cannot pause session: Session status is ${session.status}, expected ACTIVE")
                )
            }
            
            // Pause the session and update duration
            val pausedSession = session
                .withUpdatedDuration(params.pauseTime)
                .withStatus(WorkoutStatus.PAUSED, params.pauseTime)
            
            // Update in repository
            workoutRepository.updateSession(pausedSession)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
