package com.arikachmad.pebblerun.domain.repository

import com.arikachmad.pebblerun.domain.entity.WorkoutSession
import com.arikachmad.pebblerun.domain.entity.WorkoutStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

/**
 * Repository interface for workout session data access.
 * Satisfies PAT-002 (Repository pattern for data access) and REQ-005 (Local storage of workout data).
 */
interface WorkoutRepository {
    
    /**
     * Creates a new workout session
     * Supports TASK-011 (StartWorkoutUseCase implementation)
     */
    suspend fun createSession(session: WorkoutSession): Result<WorkoutSession>
    
    /**
     * Updates an existing workout session
     * Supports TASK-013 (UpdateWorkoutDataUseCase for real-time updates)
     */
    suspend fun updateSession(session: WorkoutSession): Result<WorkoutSession>
    
    /**
     * Retrieves a workout session by ID
     */
    suspend fun getSessionById(id: String): Result<WorkoutSession?>
    
    /**
     * Retrieves all workout sessions with optional filtering
     * Supports workout history display (FILE-024)
     */
    suspend fun getAllSessions(
        limit: Int? = null,
        offset: Int = 0,
        status: WorkoutStatus? = null,
        startDate: Instant? = null,
        endDate: Instant? = null
    ): Result<List<WorkoutSession>>
    
    /**
     * Observes workout sessions for real-time updates
     * Supports TEC-005 (Reactive state management)
     */
    fun observeSessions(): Flow<List<WorkoutSession>>
    
    /**
     * Observes a specific session for real-time updates
     * Supports REQ-006 (Real-time data synchronization)
     */
    fun observeSession(id: String): Flow<WorkoutSession?>
    
    /**
     * Deletes a workout session
     */
    suspend fun deleteSession(id: String): Result<Unit>
    
    /**
     * Gets the currently active session (if any)
     * Supports REQ-004 (Background tracking capability)
     */
    suspend fun getActiveSession(): Result<WorkoutSession?>
    
    /**
     * Observes the currently active session
     * Supports real-time tracking UI updates
     */
    fun observeActiveSession(): Flow<WorkoutSession?>
    
    /**
     * Completes a workout session with final statistics
     * Supports TASK-012 (StopWorkoutUseCase with data persistence)
     */
    suspend fun completeSession(
        id: String,
        endTime: Instant,
        finalStats: WorkoutSessionStats
    ): Result<WorkoutSession>
    
    /**
     * Gets session statistics summary
     * Supports workout analytics and history
     */
    suspend fun getSessionStats(id: String): Result<WorkoutSessionStats?>
    
    /**
     * Exports workout data for backup
     * Supports TASK-019 (backup and restore functionality)
     */
    suspend fun exportSessions(sessionIds: List<String>): Result<String>
    
    /**
     * Imports workout data from backup
     * Supports TASK-019 (backup and restore functionality)
     */
    suspend fun importSessions(data: String): Result<List<WorkoutSession>>
}

/**
 * Workout session statistics data class
 * Contains calculated metrics for completed sessions
 */
data class WorkoutSessionStats(
    val sessionId: String,
    val totalDuration: Long, // Duration in seconds
    val totalDistance: Double, // Distance in meters  
    val averagePace: Double, // Average pace in seconds per kilometer
    val averageHeartRate: Int, // Average HR in BPM
    val maxHeartRate: Int, // Maximum HR in BPM
    val minHeartRate: Int, // Minimum HR in BPM
    val caloriesBurned: Int, // Estimated calories
    val elevationGain: Double = 0.0, // Total elevation gain in meters
    val elevationLoss: Double = 0.0, // Total elevation loss in meters
    val avgSpeed: Double = 0.0, // Average speed in m/s
    val maxSpeed: Double = 0.0, // Maximum speed in m/s
    val hrZoneDistribution: Map<String, Long> = emptyMap() // Time spent in each HR zone
)
