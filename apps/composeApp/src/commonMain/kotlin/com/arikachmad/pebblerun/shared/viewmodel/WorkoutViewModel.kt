package com.arikachmad.pebblerun.shared.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Shared ViewModel for workout functionality
 * Exposes StateFlow for platform-specific UI consumption
 * Follows MVVM pattern with separation of concerns (PAT-001, PAT-002)
 */
class WorkoutViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(WorkoutUiState())
    val uiState: StateFlow<WorkoutUiState> = _uiState.asStateFlow()

    private val _events = MutableStateFlow<WorkoutEvent?>(null)
    val events: StateFlow<WorkoutEvent?> = _events.asStateFlow()

    /**
     * Start a new workout session
     */
    fun startWorkout() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isActive = true,
                    isLoading = true,
                    error = null
                )
                
                // TODO: Integrate with domain use cases
                // startWorkoutUseCase()
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    startTime = System.currentTimeMillis()
                )
                
                _events.value = WorkoutEvent.WorkoutStarted
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to start workout"
                )
            }
        }
    }

    /**
     * Stop the current workout session
     */
    fun stopWorkout() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    error = null
                )
                
                // TODO: Integrate with domain use cases
                // stopWorkoutUseCase()
                
                _uiState.value = _uiState.value.copy(
                    isActive = false,
                    isLoading = false,
                    endTime = System.currentTimeMillis()
                )
                
                _events.value = WorkoutEvent.WorkoutStopped
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to stop workout"
                )
            }
        }
    }

    /**
     * Update workout data (HR, pace, etc.)
     */
    fun updateWorkoutData(
        heartRate: Int? = null,
        pace: String? = null,
        distance: Double? = null
    ) {
        _uiState.value = _uiState.value.copy(
            currentHeartRate = heartRate ?: _uiState.value.currentHeartRate,
            currentPace = pace ?: _uiState.value.currentPace,
            distanceMeters = distance ?: _uiState.value.distanceMeters
        )
    }

    /**
     * Clear current event after handling
     */
    fun clearEvent() {
        _events.value = null
    }

    /**
     * Clear error state
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

/**
 * UI state for workout screen
 */
data class WorkoutUiState(
    val isActive: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val startTime: Long? = null,
    val endTime: Long? = null,
    val currentHeartRate: Int = 0,
    val currentPace: String = "00:00/km",
    val distanceMeters: Double = 0.0,
    val elapsedTimeSeconds: Long = 0L
) {
    val duration: String
        get() = if (startTime != null) {
            val elapsed = if (isActive) {
                (System.currentTimeMillis() - startTime) / 1000
            } else {
                elapsedTimeSeconds
            }
            formatDuration(elapsed)
        } else "00:00:00"

    private fun formatDuration(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, secs)
    }
}

/**
 * Events that can be emitted by the ViewModel
 */
sealed class WorkoutEvent {
    object WorkoutStarted : WorkoutEvent()
    object WorkoutStopped : WorkoutEvent()
    data class Error(val message: String) : WorkoutEvent()
}
