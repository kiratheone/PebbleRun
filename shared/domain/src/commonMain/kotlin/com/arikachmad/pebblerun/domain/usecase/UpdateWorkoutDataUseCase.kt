package com.arikachmad.pebblerun.domain.usecase

import com.arikachmad.pebblerun.domain.entity.GeoPoint
import com.arikachmad.pebblerun.domain.entity.HRSample
import com.arikachmad.pebblerun.domain.entity.WorkoutSession
import com.arikachmad.pebblerun.domain.entity.WorkoutStatus
import com.arikachmad.pebblerun.domain.repository.WorkoutRepository
import com.arikachmad.pebblerun.domain.util.HRProcessor
import com.arikachmad.pebblerun.domain.util.PaceCalculator
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Use case for updating workout data in real-time during active sessions.
 * Satisfies TASK-013 (UpdateWorkoutDataUseCase for real-time updates) and REQ-006 (Real-time data synchronization).
 * Supports CON-003 (1-second update frequency).
 */
class UpdateWorkoutDataUseCase(
    private val workoutRepository: WorkoutRepository
) : UseCase<UpdateWorkoutDataUseCase.Params, Result<WorkoutSession>> {
    
    sealed class Params {
        abstract val sessionId: String
        abstract val timestamp: Instant
        
        data class AddHRSample(
            override val sessionId: String,
            val hrSample: HRSample,
            override val timestamp: Instant = Clock.System.now()
        ) : Params()
        
        data class AddGeoPoint(
            override val sessionId: String,
            val geoPoint: GeoPoint,
            override val timestamp: Instant = Clock.System.now()
        ) : Params()
        
        data class UpdateDuration(
            override val sessionId: String,
            override val timestamp: Instant = Clock.System.now()
        ) : Params()
        
        data class BulkUpdate(
            override val sessionId: String,
            val hrSample: HRSample? = null,
            val geoPoint: GeoPoint? = null,
            override val timestamp: Instant = Clock.System.now()
        ) : Params()
    }
    
    override suspend fun invoke(params: Params): Result<WorkoutSession> {
        return try {
            // Get current session
            val session = workoutRepository.getSessionById(params.sessionId).getOrElse {
                return Result.failure(Exception("Failed to retrieve session"))
            } ?: return Result.failure(IllegalArgumentException("Session not found: ${params.sessionId}"))
            
            // Validate session is active
            if (session.status != WorkoutStatus.ACTIVE) {
                return Result.failure(
                    IllegalStateException("Cannot update data: Session is not active (status: ${session.status})")
                )
            }
            
            // Process update based on type
            val updatedSession = when (params) {
                is Params.AddHRSample -> updateWithHRSample(session, params.hrSample)
                is Params.AddGeoPoint -> updateWithGeoPoint(session, params.geoPoint)
                is Params.UpdateDuration -> updateDuration(session, params.timestamp)
                is Params.BulkUpdate -> updateBulk(session, params.hrSample, params.geoPoint, params.timestamp)
            }
            
            // Persist updated session
            workoutRepository.updateSession(updatedSession)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Updates session with new HR sample and recalculated HR statistics
     */
    private fun updateWithHRSample(session: WorkoutSession, hrSample: HRSample): WorkoutSession {
        // Validate HR sample
        if (!HRProcessor.isValidHeartRate(hrSample.heartRate)) {
            return session // Skip invalid HR readings
        }
        
        // Check for sudden HR changes if we have previous samples
        if (session.hrSamples.isNotEmpty()) {
            val lastSample = session.hrSamples.last()
            val timeDiff = hrSample.timestamp.epochSeconds - lastSample.timestamp.epochSeconds
            
            if (!HRProcessor.isValidHRTransition(lastSample.heartRate, hrSample.heartRate, timeDiff)) {
                return session // Skip invalid transitions
            }
        }
        
        return session.withNewHRSample(hrSample)
    }
    
    /**
     * Updates session with new GPS point and recalculated pace/distance
     */
    private fun updateWithGeoPoint(session: WorkoutSession, geoPoint: GeoPoint): WorkoutSession {
        val updatedGeoPoints = session.geoPoints + geoPoint
        
        // Calculate new distance and pace
        val newDistance = PaceCalculator.calculateTotalDistance(updatedGeoPoints)
        val currentDuration = geoPoint.timestamp.epochSeconds - session.startTime.epochSeconds
        val newPace = if (newDistance > 0 && currentDuration > 0) {
            PaceCalculator.calculatePace(newDistance, currentDuration)
        } else {
            session.averagePace
        }
        
        return session.withNewGeoPoint(geoPoint, newPace, newDistance)
    }
    
    /**
     * Updates session duration without adding new data points
     */
    private fun updateDuration(session: WorkoutSession, currentTime: Instant): WorkoutSession {
        return session.withUpdatedDuration(currentTime)
    }
    
    /**
     * Performs bulk update with multiple data points for efficiency
     */
    private fun updateBulk(
        session: WorkoutSession,
        hrSample: HRSample?,
        geoPoint: GeoPoint?,
        timestamp: Instant
    ): WorkoutSession {
        var updatedSession = session
        
        // Update HR if provided
        if (hrSample != null) {
            updatedSession = updateWithHRSample(updatedSession, hrSample)
        }
        
        // Update GPS if provided
        if (geoPoint != null) {
            updatedSession = updateWithGeoPoint(updatedSession, geoPoint)
        }
        
        // Update duration
        updatedSession = updateDuration(updatedSession, timestamp)
        
        return updatedSession
    }
}

/**
 * Use case for getting real-time workout data for display purposes.
 * Supports REQ-006 (Real-time data synchronization) and reactive UI updates.
 */
class GetRealtimeWorkoutDataUseCase(
    private val workoutRepository: WorkoutRepository
) : UseCase<GetRealtimeWorkoutDataUseCase.Params, Result<RealtimeWorkoutData>> {
    
    data class Params(
        val sessionId: String
    )
    
    override suspend fun invoke(params: Params): Result<RealtimeWorkoutData> {
        return try {
            val session = workoutRepository.getSessionById(params.sessionId).getOrElse {
                return Result.failure(Exception("Failed to retrieve session"))
            } ?: return Result.failure(IllegalArgumentException("Session not found: ${params.sessionId}"))
            
            // Calculate current stats
            val currentTime = Clock.System.now()
            val currentDuration = if (session.status == WorkoutStatus.ACTIVE) {
                currentTime.epochSeconds - session.startTime.epochSeconds
            } else {
                session.totalDuration
            }
            
            // Get latest HR data (moving average for smoothness)
            val currentHR = if (session.hrSamples.isNotEmpty()) {
                HRProcessor.calculateMovingAverageHR(session.hrSamples, windowSeconds = 10)
            } else 0
            
            // Get current pace (smoothed)
            val currentPace = if (session.geoPoints.size >= 2) {
                PaceCalculator.calculateSmoothedPace(session.geoPoints, smoothingWindow = 5)
            } else session.averagePace
            
            // Calculate current speed
            val currentSpeed = if (currentPace > 0) {
                PaceCalculator.paceToSpeed(currentPace)
            } else 0.0
            
            val realtimeData = RealtimeWorkoutData(
                sessionId = params.sessionId,
                currentDuration = currentDuration,
                currentDistance = session.totalDistance,
                currentPace = currentPace,
                currentSpeed = currentSpeed,
                currentHeartRate = currentHR,
                averageHeartRate = session.averageHeartRate,
                averagePace = session.averagePace,
                status = session.status,
                lastUpdate = currentTime
            )
            
            Result.success(realtimeData)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Real-time workout data for UI display
 */
data class RealtimeWorkoutData(
    val sessionId: String,
    val currentDuration: Long, // Current duration in seconds
    val currentDistance: Double, // Current distance in meters
    val currentPace: Double, // Current pace in seconds per km
    val currentSpeed: Double, // Current speed in km/h
    val currentHeartRate: Int, // Current HR (moving average)
    val averageHeartRate: Int, // Session average HR
    val averagePace: Double, // Session average pace
    val status: WorkoutStatus,
    val lastUpdate: Instant
) {
    /**
     * Formats duration as HH:MM:SS
     */
    val formattedDuration: String
        get() {
            val hours = currentDuration / 3600
            val minutes = (currentDuration % 3600) / 60
            val seconds = currentDuration % 60
            return "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        }
    
    /**
     * Formats distance in appropriate units (meters or kilometers)
     */
    val formattedDistance: String
        get() = if (currentDistance >= 1000) {
            "${(currentDistance / 1000 * 100).toInt() / 100.0} km"
        } else {
            "${currentDistance.toInt()} m"
        }
    
    /**
     * Formats current pace as MM:SS per km
     */
    val formattedPace: String
        get() = PaceCalculator.formatPace(currentPace)
    
    /**
     * Formats speed with one decimal place
     */
    val formattedSpeed: String
        get() = "${(currentSpeed * 10).toInt() / 10.0} km/h"
}
