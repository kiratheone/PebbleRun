package com.arikachmad.pebblerun.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arikachmad.pebblerun.domain.model.WorkoutSession
import com.arikachmad.pebblerun.domain.model.WorkoutStatus
import com.arikachmad.pebblerun.domain.usecase.StartWorkoutUseCase
import com.arikachmad.pebblerun.domain.usecase.StopWorkoutUseCase
import com.arikachmad.pebblerun.domain.usecase.UpdateWorkoutDataUseCase
import com.arikachmad.pebblerun.data.repository.MockWorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for workout screen implementing MVVM pattern
 * Handles workout session state management and user interactions
 */
class WorkoutViewModel : ViewModel() {

    // Mock dependencies for UI testing
    private val mockRepository = MockWorkoutRepository()
    private val startWorkoutUseCase = StartWorkoutUseCase(mockRepository)
    private val stopWorkoutUseCase = StopWorkoutUseCase(mockRepository)
    private val updateWorkoutDataUseCase = UpdateWorkoutDataUseCase(mockRepository)

    private val _uiState = MutableStateFlow(WorkoutUiState())
    val uiState: StateFlow<WorkoutUiState> = _uiState.asStateFlow()
    
    private var currentSession: WorkoutSession? = null

    fun startWorkout() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                
                val session = startWorkoutUseCase.execute()
                currentSession = session
                
                _uiState.value = _uiState.value.copy(
                    currentSession = session,
                    isLoading = false,
                    isWorkoutActive = true
                )
                
                startDataSimulation()
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to start workout"
                )
            }
        }
    }

    fun pauseWorkout() {
        viewModelScope.launch {
            currentSession?.let { session ->
                try {
                    startWorkoutUseCase.pauseWorkout(session.id)
                    
                    val updatedSession = session.copy(status = WorkoutStatus.PAUSED)
                    currentSession = updatedSession
                    
                    _uiState.value = _uiState.value.copy(
                        currentSession = updatedSession,
                        isWorkoutActive = false
                    )
                    
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(error = e.message)
                }
            }
        }
    }

    fun resumeWorkout() {
        viewModelScope.launch {
            currentSession?.let { session ->
                try {
                    startWorkoutUseCase.resumeWorkout(session.id)
                    
                    val updatedSession = session.copy(status = WorkoutStatus.ACTIVE)
                    currentSession = updatedSession
                    
                    _uiState.value = _uiState.value.copy(
                        currentSession = updatedSession,
                        isWorkoutActive = true
                    )
                    
                    startDataSimulation()
                    
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(error = e.message)
                }
            }
        }
    }

    fun stopWorkout() {
        viewModelScope.launch {
            currentSession?.let { session ->
                try {
                    _uiState.value = _uiState.value.copy(isLoading = true)
                    
                    val completedSession = stopWorkoutUseCase.execute(session.id)
                    
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        currentSession = completedSession,
                        isWorkoutActive = false,
                        showWorkoutComplete = true
                    )
                    
                    currentSession = null
                    
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
            }
        }
    }

    fun dismissWorkoutComplete() {
        _uiState.value = _uiState.value.copy(
            showWorkoutComplete = false,
            currentSession = null
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun startDataSimulation() {
        viewModelScope.launch {
            while (currentSession?.status == WorkoutStatus.ACTIVE) {
                currentSession?.let { session ->
                    val updatedSession = session.copy(
                        totalDuration = session.totalDuration + 1000, // +1 second
                        totalDistance = session.totalDistance + 2.5, // +2.5 meters
                        averagePace = if (session.totalDistance > 0) 
                            (session.totalDuration / 1000.0) / (session.totalDistance / 1000.0) * 1000 
                        else 0.0,
                        averageHeartRate = session.hrSamples.lastOrNull()?.bpm ?: 140,
                        calories = (session.totalDuration / 1000 * 0.2).toInt()
                    )
                    
                    currentSession = updatedSession
                    _uiState.value = _uiState.value.copy(currentSession = updatedSession)
                }
                
                kotlinx.coroutines.delay(1000)
            }
        }
    }
}

data class WorkoutUiState(
    val isLoading: Boolean = false,
    val currentSession: WorkoutSession? = null,
    val isWorkoutActive: Boolean = false,
    val showWorkoutComplete: Boolean = false,
    val error: String? = null
)
