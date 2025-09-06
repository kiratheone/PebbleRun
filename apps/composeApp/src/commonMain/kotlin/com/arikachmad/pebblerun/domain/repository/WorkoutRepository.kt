package com.arikachmad.pebblerun.domain.repository

import com.arikachmad.pebblerun.domain.model.WorkoutSession
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for workout data operations
 */
interface WorkoutRepository {
    /**
     * Get all workout sessions
     */
    fun getAllWorkoutSessions(): Flow<List<WorkoutSession>>
    
    /**
     * Get a specific workout session by ID
     */
    suspend fun getWorkoutSession(id: String): WorkoutSession?
    
    /**
     * Save a workout session
     */
    suspend fun saveWorkoutSession(session: WorkoutSession)
    
    /**
     * Update an existing workout session
     */
    suspend fun updateWorkoutSession(session: WorkoutSession)
    
    /**
     * Delete a workout session
     */
    suspend fun deleteWorkoutSession(id: String)
}
