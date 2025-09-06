package com.arikachmad.pebblerun.android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arikachmad.pebblerun.domain.entity.GeoPoint
import com.arikachmad.pebblerun.domain.entity.HRSample
import com.arikachmad.pebblerun.domain.entity.WorkoutSession
import com.arikachmad.pebblerun.domain.entity.WorkoutStatus
import com.arikachmad.pebblerun.domain.usecase.StartWorkoutUseCase
import com.arikachmad.pebblerun.domain.usecase.StopWorkoutUseCase
import com.arikachmad.pebblerun.domain.usecase.UpdateWorkoutDataUseCase
import com.arikachmad.pebblerun.shared.ui.PlatformActions
import com.arikachmad.pebblerun.shared.ui.UIBridge
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * Android-specific ViewModel for workout management using StateFlow
 * Satisfies REQ-011 (Create Android-specific ViewModels with StateFlow integration)
 * Satisfies PAT-001 (MVVM pattern with platform-specific ViewModels for Android)
 * Satisfies TEC-001 (Maintain KMP shared business logic while implementing platform-specific UI)
 */
class WorkoutViewModel(
    private val startWorkoutUseCase: StartWorkoutUseCase,
    private val stopWorkoutUseCase: StopWorkoutUseCase,
    private val updateWorkoutDataUseCase: UpdateWorkoutDataUseCase,
    private val platformActions: PlatformActions,
    private val uiBridge: UIBridge
) : ViewModel() {
    
    // UI State
    private val _uiState = MutableStateFlow(WorkoutUiState())
    val uiState: StateFlow<WorkoutUiState> = _uiState.asStateFlow()
    
    // Events
    private val _events = MutableSharedFlow<WorkoutEvent>()
    val events: SharedFlow<WorkoutEvent> = _events.asSharedFlow()
    
    // Error handling
    private val _errors = MutableSharedFlow<String>()
    val errors: SharedFlow<String> = _errors.asSharedFlow()
    
    init {
        // Check initial permissions
        checkPermissions()
    }
    
    /**
     * Start a new workout session
     */
    fun startWorkout() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                
                // Check permissions first
                if (!uiBridge.requestPermissions()) {
                    _errors.emit("Required permissions not granted")
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    return@launch
                }
                
                // Check Pebble connection
                if (!platformActions.isPebbleConnected()) {
                    _errors.emit("Pebble not connected")
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    return@launch
                }
                
                // Launch Pebble app
                if (!platformActions.launchPebbleApp()) {
                    _errors.emit("Failed to launch Pebble app")
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    return@launch
                }
                
                // Start workout
                val result = startWorkoutUseCase()
                result.fold(
                    onSuccess = { session ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            currentSession = session,
                            workoutStatus = WorkoutStatus.ACTIVE
                        )
                        _events.emit(WorkoutEvent.WorkoutStarted)
                        uiBridge.startBackgroundService()
                    },
                    onFailure = { error ->
                        _errors.emit(error.message ?: "Failed to start workout")
                        _uiState.value = _uiState.value.copy(isLoading = false)
                    }
                )
                
            } catch (e: Exception) {
                _errors.emit(e.message ?: "Unknown error occurred")
                _uiState.value = _uiState.value.copy(isLoading = false)
                uiBridge.handleError(e)
            }
        }
    }
    
    /**
     * Stop the current workout session
     */
    fun stopWorkout() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                
                // Stop workout
                val result = stopWorkoutUseCase()
                result.fold(
                    onSuccess = { session ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            currentSession = session,
                            workoutStatus = WorkoutStatus.COMPLETED
                        )
                        _events.emit(WorkoutEvent.WorkoutStopped(session))
                        
                        // Close Pebble app and stop service
                        platformActions.closePebbleApp()
                        uiBridge.stopBackgroundService()
                    },
                    onFailure = { error ->
                        _errors.emit(error.message ?: "Failed to stop workout")
                        _uiState.value = _uiState.value.copy(isLoading = false)
                    }
                )
                
            } catch (e: Exception) {
                _errors.emit(e.message ?: "Unknown error occurred")
                _uiState.value = _uiState.value.copy(isLoading = false)
                uiBridge.handleError(e)
            }
        }
    }
    
    /**
     * Clear the last event
     */
    fun clearEvent() {
        // Events are consumed automatically via SharedFlow
    }
    
    /**
     * Clear errors
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    /**
     * Update real-time workout data
     */
    fun updateWorkoutData(hrSample: HRSample?, geoPoint: GeoPoint?) {
        val currentState = _uiState.value
        val currentSession = currentState.currentSession ?: return
        
        viewModelScope.launch {
            try {
                // Use the UpdateWorkoutDataUseCase for real-time updates
                val result = updateWorkoutDataUseCase(
                    hrSample = hrSample,
                    geoPoint = geoPoint
                )
                
                result.fold(
                    onSuccess = { updatedSession ->
                        _uiState.value = currentState.copy(currentSession = updatedSession)
                    },
                    onFailure = { error ->
                        _errors.emit(error.message ?: "Failed to update workout data")
                    }
                )
            } catch (e: Exception) {
                _errors.emit(e.message ?: "Unknown error occurred")
                uiBridge.handleError(e)
            }
        }
    }
    
    /**
     * Check and update permission status
     */
    private fun checkPermissions() {
        viewModelScope.launch {
            val hasPermissions = uiBridge.requestPermissions()
            _uiState.value = _uiState.value.copy(hasRequiredPermissions = hasPermissions)
        }
    }
}

/**
 * UI State for workout screen
 */
data class WorkoutUiState(
    val isLoading: Boolean = false,
    val currentSession: WorkoutSession? = null,
    val workoutStatus: WorkoutStatus = WorkoutStatus.IDLE,
    val hasRequiredPermissions: Boolean = false,
    val error: String? = null
)

/**
 * Events emitted by the WorkoutViewModel
 */
sealed class WorkoutEvent {
    object WorkoutStarted : WorkoutEvent()
    data class WorkoutStopped(val session: WorkoutSession) : WorkoutEvent()
}
