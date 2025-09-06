package com.arikachmad.pebblerun.domain.integration

import com.arikachmad.pebblerun.domain.entity.WorkoutSession
import com.arikachmad.pebblerun.domain.repository.WorkoutRepository
import com.arikachmad.pebblerun.domain.usecase.StartWorkoutUseCase
import com.arikachmad.pebblerun.domain.usecase.StopWorkoutUseCase
import com.arikachmad.pebblerun.domain.usecase.UpdateWorkoutDataUseCase
import com.arikachmad.pebblerun.domain.error.DomainResult
import com.arikachmad.pebblerun.domain.error.DomainError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*

/**
 * Central integration manager that coordinates all components of the PebbleRun system.
 * Domain layer implementation following clean architecture principles.
 * Pure Kotlin with no external dependencies.
 */
class WorkoutIntegrationManager(
    private val workoutRepository: WorkoutRepository,
    private val startWorkoutUseCase: StartWorkoutUseCase,
    private val stopWorkoutUseCase: StopWorkoutUseCase,
    private val updateWorkoutDataUseCase: UpdateWorkoutDataUseCase,
    private val scope: CoroutineScope
) {
    
    // Current system state
    private val _systemState = MutableStateFlow(SystemState.IDLE)
    val systemState: StateFlow<SystemState> = _systemState.asStateFlow()
    
    private val _currentWorkout = MutableStateFlow<WorkoutSession?>(null)
    val currentWorkout: StateFlow<WorkoutSession?> = _currentWorkout.asStateFlow()
    
    /**
     * Starts a new workout session
     */
    suspend fun startWorkout(): DomainResult<WorkoutSession> {
        return try {
            _systemState.value = SystemState.STARTING_WORKOUT
            
            val result = startWorkoutUseCase.execute()
            when (result) {
                is DomainResult.Success -> {
                    _currentWorkout.value = result.data
                    _systemState.value = SystemState.WORKOUT_ACTIVE
                    result
                }
                is DomainResult.Error -> {
                    _systemState.value = SystemState.ERROR
                    result
                }
            }
        } catch (e: Exception) {
            _systemState.value = SystemState.ERROR
            DomainResult.Error(
                DomainError.InvalidOperation("start_workout", e.message ?: "Unknown error")
            )
        }
    }
    
    /**
     * Stops the current workout session
     */
    suspend fun stopWorkout(): DomainResult<WorkoutSession> {
        val currentSession = _currentWorkout.value
            ?: return DomainResult.Error(
                DomainError.EntityNotFound("WorkoutSession", "current")
            )
        
        return try {
            _systemState.value = SystemState.STOPPING_WORKOUT
            
            val result = stopWorkoutUseCase.execute(currentSession.id)
            when (result) {
                is DomainResult.Success -> {
                    _currentWorkout.value = null
                    _systemState.value = SystemState.IDLE
                    result
                }
                is DomainResult.Error -> {
                    _systemState.value = SystemState.ERROR
                    result
                }
            }
        } catch (e: Exception) {
            _systemState.value = SystemState.ERROR
            DomainResult.Error(
                DomainError.InvalidOperation("stop_workout", e.message ?: "Unknown error")
            )
        }
    }
    
    /**
     * Updates workout data during active session
     */
    suspend fun updateWorkoutData(
        heartRate: Int?,
        pace: String?,
        distance: Double?
    ): DomainResult<WorkoutSession> {
        val currentSession = _currentWorkout.value
            ?: return DomainResult.Error(
                DomainError.EntityNotFound("WorkoutSession", "current")
            )
        
        return try {
            val result = updateWorkoutDataUseCase.execute(
                sessionId = currentSession.id,
                heartRate = heartRate,
                pace = pace,
                distance = distance
            )
            
            when (result) {
                is DomainResult.Success -> {
                    _currentWorkout.value = result.data
                    result
                }
                is DomainResult.Error -> result
            }
        } catch (e: Exception) {
            DomainResult.Error(
                DomainError.InvalidOperation("update_workout_data", e.message ?: "Unknown error")
            )
        }
    }
}

/**
 * System state enumeration
 */
enum class SystemState {
    IDLE,
    STARTING_WORKOUT,
    WORKOUT_ACTIVE,
    STOPPING_WORKOUT,
    ERROR
}
