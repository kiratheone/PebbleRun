package com.arikachmad.pebblerun.android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arikachmad.pebblerun.domain.entity.WorkoutSession
import com.arikachmad.pebblerun.shared.ui.UIBridge
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Android-specific ViewModel for workout history management using StateFlow
 * Satisfies REQ-011 (Create Android-specific ViewModels with StateFlow integration)
 * Satisfies PAT-001 (MVVM pattern with platform-specific ViewModels for Android)
 * Note: Using simplified implementation until history use cases are available
 */
class HistoryViewModel(
    private val uiBridge: UIBridge
) : ViewModel() {
    
    // UI State
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()
    
    // Events
    private val _events = MutableSharedFlow<HistoryEvent>()
    val events: SharedFlow<HistoryEvent> = _events.asSharedFlow()
    
    // Error handling
    private val _errors = MutableSharedFlow<String>()
    val errors: SharedFlow<String> = _errors.asSharedFlow()
    
    init {
        loadWorkoutHistory()
    }
    
    /**
     * Load workout history from the domain layer
     * TODO: Implement with GetWorkoutHistoryUseCase when available
     */
    fun loadWorkoutHistory() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                
                // TODO: Replace with actual use case when available
                // For now, show empty list
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    workoutSessions = emptyList(),
                    error = null
                )
                
            } catch (e: Exception) {
                _errors.emit(e.message ?: "Failed to load workout history")
                _uiState.value = _uiState.value.copy(isLoading = false)
                uiBridge.handleError(e)
            }
        }
    }
    
    /**
     * Delete a workout session
     * TODO: Implement with DeleteWorkoutSessionUseCase when available
     */
    fun deleteWorkout(sessionId: String) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                
                // TODO: Replace with actual use case when available
                _events.emit(HistoryEvent.WorkoutDeleted(sessionId))
                _uiState.value = _uiState.value.copy(isLoading = false)
                
            } catch (e: Exception) {
                _errors.emit(e.message ?: "Unknown error occurred")
                _uiState.value = _uiState.value.copy(isLoading = false)
                uiBridge.handleError(e)
            }
        }
    }
    
    /**
     * Refresh workout history
     */
    fun refreshHistory() {
        loadWorkoutHistory()
    }
    
    /**
     * Clear errors
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    /**
     * Show workout details
     */
    fun showWorkoutDetails(session: WorkoutSession) {
        viewModelScope.launch {
            _events.emit(HistoryEvent.ShowWorkoutDetails(session))
        }
    }
}

/**
 * UI State for history screen
 */
data class HistoryUiState(
    val isLoading: Boolean = false,
    val workoutSessions: List<WorkoutSession> = emptyList(),
    val error: String? = null
)

/**
 * Events emitted by the HistoryViewModel
 */
sealed class HistoryEvent {
    data class WorkoutDeleted(val sessionId: String) : HistoryEvent()
    data class ShowWorkoutDetails(val session: WorkoutSession) : HistoryEvent()
}
