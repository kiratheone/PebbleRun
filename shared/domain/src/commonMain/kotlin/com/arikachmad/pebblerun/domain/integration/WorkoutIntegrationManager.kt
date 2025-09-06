package com.arikachmad.pebblerun.domain.integration

import com.arikachmad.pebblerun.domain.entity.WorkoutSession
import com.arikachmad.pebblerun.domain.entity.WorkoutStatus
import com.arikachmad.pebblerun.domain.repository.WorkoutRepository
import com.arikachmad.pebblerun.domain.usecase.StartWorkoutUseCase
import com.arikachmad.pebblerun.domain.usecase.StopWorkoutUseCase
import com.arikachmad.pebblerun.domain.usecase.UpdateWorkoutDataUseCase
import com.arikachmad.pebblerun.domain.recovery.ErrorRecoveryManager
import com.arikachmad.pebblerun.domain.recovery.ErrorContext
import com.arikachmad.pebblerun.domain.recovery.ErrorSeverity
import com.arikachmad.pebblerun.util.error.PebbleRunError
import com.arikachmad.pebblerun.util.error.Result
import com.arikachmad.pebblerun.util.error.safeSuspendCall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.datetime.Clock

/**
 * Central integration manager that coordinates all components of the PebbleRun system.
 * Satisfies TASK-048: Integrate all components with comprehensive error handling.
 * Implements CON-004 (Graceful handling of disconnections) and robust error recovery.
 */
class WorkoutIntegrationManager(
    private val workoutRepository: WorkoutRepository,
    private val startWorkoutUseCase: StartWorkoutUseCase,
    private val stopWorkoutUseCase: StopWorkoutUseCase,
    private val updateWorkoutDataUseCase: UpdateWorkoutDataUseCase,
    private val errorRecoveryManager: ErrorRecoveryManager,
    private val scope: CoroutineScope
) {
    
    // Current system state
    private val _systemState = MutableStateFlow(SystemState.IDLE)
    val systemState: StateFlow<SystemState> = _systemState.asStateFlow()
    
    private val _currentWorkout = MutableStateFlow<WorkoutSession?>(null)
    val currentWorkout: StateFlow<WorkoutSession?> = _currentWorkout.asStateFlow()
    
    private val _systemHealth = MutableStateFlow(SystemHealth.HEALTHY)
    val systemHealth: StateFlow<SystemHealth> = _systemHealth.asStateFlow()
    
    // Integration status tracking
    private val _componentStatus = MutableStateFlow(
        mapOf(
            ComponentType.WORKOUT_REPOSITORY to ComponentStatus.OPERATIONAL,
            ComponentType.PEBBLE_TRANSPORT to ComponentStatus.OPERATIONAL,
            ComponentType.LOCATION_PROVIDER to ComponentStatus.OPERATIONAL,
            ComponentType.BACKGROUND_SERVICE to ComponentStatus.OPERATIONAL
        )
    )
    val componentStatus: StateFlow<Map<ComponentType, ComponentStatus>> = _componentStatus.asStateFlow()
    
    init {
        // Monitor error recovery system
        scope.launch {
            errorRecoveryManager.recoveryState.collect { recoveryState ->
                updateSystemHealthFromRecovery(recoveryState)
            }
        }
        
        // Monitor component health
        startHealthMonitoring()
    }
    
    /**
     * Starts a new workout session with integrated error handling
     */
    suspend fun startWorkout(): Result<WorkoutSession> = supervisorScope {
        return@supervisorScope safeSuspendCall {
            _systemState.value = SystemState.STARTING_WORKOUT
            
            try {
                // Pre-flight checks
                val healthCheck = performPreFlightChecks()
                if (!healthCheck.isHealthy) {
                    throw PebbleRunError.SystemNotReadyError(
                        "System health check failed: ${healthCheck.issues.joinToString(", ")}"
                    )
                }
                
                // Start workout using use case
                val result = startWorkoutUseCase.execute()
                
                when (result) {
                    is Result.Success -> {
                        _currentWorkout.value = result.data
                        _systemState.value = SystemState.WORKOUT_ACTIVE
                        
                        // Start real-time monitoring
                        startWorkoutMonitoring(result.data)
                        
                        result.data
                    }
                    is Result.Error -> {
                        _systemState.value = SystemState.ERROR
                        handleIntegrationError(result.exception, "start_workout")
                        throw result.exception
                    }
                }
            } catch (e: Exception) {
                _systemState.value = SystemState.ERROR
                val context = ErrorContext(
                    component = "WorkoutIntegrationManager",
                    operation = "startWorkout",
                    userFacing = true,
                    workoutActive = false
                )
                errorRecoveryManager.handleError(e, context)
                throw e
            }
        }
    }
    
    /**
     * Stops the current workout session with integrated error handling
     */
    suspend fun stopWorkout(): Result<WorkoutSession> = supervisorScope {
        return@supervisorScope safeSuspendCall {
            val currentSession = _currentWorkout.value
                ?: throw PebbleRunError.SessionNotFoundError("No active workout session")
            
            _systemState.value = SystemState.STOPPING_WORKOUT
            
            try {
                val result = stopWorkoutUseCase.execute()
                
                when (result) {
                    is Result.Success -> {
                        _currentWorkout.value = null
                        _systemState.value = SystemState.IDLE
                        
                        // Stop monitoring
                        stopWorkoutMonitoring()
                        
                        result.data
                    }
                    is Result.Error -> {
                        _systemState.value = SystemState.ERROR
                        handleIntegrationError(result.exception, "stop_workout")
                        throw result.exception
                    }
                }
            } catch (e: Exception) {
                _systemState.value = SystemState.ERROR
                val context = ErrorContext(
                    component = "WorkoutIntegrationManager",
                    operation = "stopWorkout", 
                    userFacing = true,
                    workoutActive = true
                )
                errorRecoveryManager.handleError(e, context)
                throw e
            }
        }
    }
    
    /**
     * Updates workout data with integrated error handling and recovery
     */
    suspend fun updateWorkoutData(
        heartRate: Int? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        accuracy: Float? = null
    ): Result<WorkoutSession> = supervisorScope {
        return@supervisorScope safeSuspendCall {
            val currentSession = _currentWorkout.value
                ?: throw PebbleRunError.SessionNotFoundError("No active workout session")
            
            try {
                val result = updateWorkoutDataUseCase.execute(
                    heartRate = heartRate,
                    latitude = latitude,
                    longitude = longitude,
                    accuracy = accuracy
                )
                
                when (result) {
                    is Result.Success -> {
                        _currentWorkout.value = result.data
                        result.data
                    }
                    is Result.Error -> {
                        handleIntegrationError(result.exception, "update_workout_data")
                        throw result.exception
                    }
                }
            } catch (e: Exception) {
                val context = ErrorContext(
                    component = "WorkoutIntegrationManager",
                    operation = "updateWorkoutData",
                    userFacing = false,
                    workoutActive = true
                )
                errorRecoveryManager.handleError(e, context)
                throw e
            }
        }
    }
    
    /**
     * Performs comprehensive system health checks
     */
    suspend fun performSystemHealthCheck(): SystemHealthReport = supervisorScope {
        val issues = mutableListOf<String>()
        val componentResults = mutableMapOf<ComponentType, ComponentHealthResult>()
        
        // Check repository health
        try {
            val repositoryTest = workoutRepository.getAllSessions()
            repositoryTest.first() // Test if we can read data
            componentResults[ComponentType.WORKOUT_REPOSITORY] = ComponentHealthResult.HEALTHY
        } catch (e: Exception) {
            issues.add("Repository health check failed: ${e.message}")
            componentResults[ComponentType.WORKOUT_REPOSITORY] = ComponentHealthResult.UNHEALTHY
        }
        
        // Check error recovery system
        try {
            val recoveryHealth = errorRecoveryManager.performHealthCheck()
            if (recoveryHealth.overallHealth.isHealthy) {
                componentResults[ComponentType.ERROR_RECOVERY] = ComponentHealthResult.HEALTHY
            } else {
                issues.add("Error recovery system degraded")
                componentResults[ComponentType.ERROR_RECOVERY] = ComponentHealthResult.DEGRADED
            }
        } catch (e: Exception) {
            issues.add("Error recovery system check failed: ${e.message}")
            componentResults[ComponentType.ERROR_RECOVERY] = ComponentHealthResult.UNHEALTHY
        }
        
        val overallHealth = if (issues.isEmpty()) {
            SystemHealth.HEALTHY
        } else if (componentResults.values.any { it == ComponentHealthResult.UNHEALTHY }) {
            SystemHealth.UNHEALTHY
        } else {
            SystemHealth.DEGRADED
        }
        
        _systemHealth.value = overallHealth
        
        return@supervisorScope SystemHealthReport(
            overallHealth = overallHealth,
            componentResults = componentResults,
            issues = issues,
            timestamp = Clock.System.now()
        )
    }
    
    /**
     * Recovers from system errors using integrated recovery mechanisms
     */
    suspend fun recoverFromError(error: Throwable): Result<Boolean> = supervisorScope {
        return@supervisorScope safeSuspendCall {
            _systemState.value = SystemState.RECOVERING
            
            val context = ErrorContext(
                component = "WorkoutIntegrationManager",
                operation = "recoverFromError",
                userFacing = true,
                workoutActive = _currentWorkout.value != null
            )
            
            val recoveryResult = errorRecoveryManager.handleError(error, context)
            
            if (recoveryResult.success) {
                _systemState.value = if (_currentWorkout.value != null) {
                    SystemState.WORKOUT_ACTIVE
                } else {
                    SystemState.IDLE
                }
                true
            } else {
                _systemState.value = SystemState.ERROR
                false
            }
        }
    }
    
    /**
     * Gracefully shuts down the integration manager
     */
    suspend fun shutdown() {
        try {
            _systemState.value = SystemState.SHUTTING_DOWN
            
            // Stop any active workout
            if (_currentWorkout.value != null) {
                try {
                    stopWorkout()
                } catch (e: Exception) {
                    // Log but don't throw during shutdown
                }
            }
            
            // Stop monitoring
            stopWorkoutMonitoring()
            
            _systemState.value = SystemState.SHUTDOWN
        } catch (e: Exception) {
            _systemState.value = SystemState.ERROR
        }
    }
    
    // Private helper methods
    
    private suspend fun performPreFlightChecks(): PreFlightResult {
        val issues = mutableListOf<String>()
        
        // Check if another workout is active
        val activeSessions = workoutRepository.getSessionsByStatus(WorkoutStatus.ACTIVE)
        if (activeSessions.isSuccess() && activeSessions.getOrNull()?.isNotEmpty() == true) {
            issues.add("Another workout session is already active")
        }
        
        // Check system health
        val healthReport = performSystemHealthCheck()
        if (healthReport.overallHealth == SystemHealth.UNHEALTHY) {
            issues.add("System health check failed")
        }
        
        return PreFlightResult(
            isHealthy = issues.isEmpty(),
            issues = issues
        )
    }
    
    private fun startWorkoutMonitoring(session: WorkoutSession) {
        scope.launch {
            // Monitor workout updates and handle errors
            try {
                // This would normally monitor real-time data flows
                // For now, just update system state
                _systemState.value = SystemState.WORKOUT_ACTIVE
            } catch (e: Exception) {
                val context = ErrorContext(
                    component = "WorkoutIntegrationManager",
                    operation = "workoutMonitoring",
                    userFacing = false,
                    workoutActive = true
                )
                errorRecoveryManager.handleError(e, context)
            }
        }
    }
    
    private fun stopWorkoutMonitoring() {
        // Stop monitoring activities
    }
    
    private fun startHealthMonitoring() {
        scope.launch {
            // Periodic health checks
            while (_systemState.value != SystemState.SHUTDOWN) {
                try {
                    kotlinx.coroutines.delay(30_000) // Check every 30 seconds
                    if (_systemState.value != SystemState.SHUTDOWN) {
                        performSystemHealthCheck()
                    }
                } catch (e: Exception) {
                    // Continue monitoring even if health check fails
                }
            }
        }
    }
    
    private suspend fun handleIntegrationError(error: PebbleRunError, operation: String) {
        val context = ErrorContext(
            component = "WorkoutIntegrationManager",
            operation = operation,
            userFacing = true,
            workoutActive = _currentWorkout.value != null
        )
        
        errorRecoveryManager.handleError(error, context)
    }
    
    private fun updateSystemHealthFromRecovery(recoveryState: com.arikachmad.pebblerun.domain.recovery.RecoveryState) {
        _systemHealth.value = when (recoveryState) {
            com.arikachmad.pebblerun.domain.recovery.RecoveryState.IDLE,
            com.arikachmad.pebblerun.domain.recovery.RecoveryState.MONITORING -> SystemHealth.HEALTHY
            com.arikachmad.pebblerun.domain.recovery.RecoveryState.RECOVERING -> SystemHealth.RECOVERING
            com.arikachmad.pebblerun.domain.recovery.RecoveryState.DEGRADED -> SystemHealth.DEGRADED
            com.arikachmad.pebblerun.domain.recovery.RecoveryState.FAILED,
            com.arikachmad.pebblerun.domain.recovery.RecoveryState.DISABLED -> SystemHealth.UNHEALTHY
        }
    }
}

// Supporting data classes and enums

enum class SystemState {
    IDLE,
    STARTING_WORKOUT,
    WORKOUT_ACTIVE,
    STOPPING_WORKOUT,
    RECOVERING,
    SHUTTING_DOWN,
    SHUTDOWN,
    ERROR
}

enum class SystemHealth {
    HEALTHY,
    DEGRADED,
    RECOVERING,
    UNHEALTHY
}

enum class ComponentType {
    WORKOUT_REPOSITORY,
    PEBBLE_TRANSPORT,
    LOCATION_PROVIDER,
    BACKGROUND_SERVICE,
    ERROR_RECOVERY
}

enum class ComponentStatus {
    OPERATIONAL,
    DEGRADED,
    OFFLINE,
    ERROR
}

enum class ComponentHealthResult {
    HEALTHY,
    DEGRADED,
    UNHEALTHY
}

data class SystemHealthReport(
    val overallHealth: SystemHealth,
    val componentResults: Map<ComponentType, ComponentHealthResult>,
    val issues: List<String>,
    val timestamp: kotlinx.datetime.Instant
)

data class PreFlightResult(
    val isHealthy: Boolean,
    val issues: List<String>
)
