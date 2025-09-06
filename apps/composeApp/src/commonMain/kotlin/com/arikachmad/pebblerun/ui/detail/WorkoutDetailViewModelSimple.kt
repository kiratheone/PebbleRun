package com.arikachmad.pebblerun.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arikachmad.pebblerun.domain.model.WorkoutSession
import com.arikachmad.pebblerun.domain.repository.WorkoutRepository
import com.arikachmad.pebblerun.data.repository.MockWorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WorkoutDetailViewModel : ViewModel() {
    
    private val workoutRepository: WorkoutRepository = MockWorkoutRepository()
    
    private val _uiState = MutableStateFlow(WorkoutDetailUiState())
    val uiState: StateFlow<WorkoutDetailUiState> = _uiState.asStateFlow()
    
    fun loadWorkoutDetail(sessionId: String) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                
                val session = workoutRepository.getWorkoutSession(sessionId)
                if (session != null) {
                    val analytics = calculateAnalytics(session)
                    
                    _uiState.value = _uiState.value.copy(
                        session = session,
                        analytics = analytics,
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Workout session not found"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load workout details"
                )
            }
        }
    }
    
    fun updateNotes(notes: String) {
        viewModelScope.launch {
            val currentSession = _uiState.value.session
            if (currentSession != null) {
                try {
                    val updatedSession = currentSession.copy(notes = notes)
                    workoutRepository.updateWorkoutSession(updatedSession)
                    
                    _uiState.value = _uiState.value.copy(session = updatedSession)
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        error = e.message ?: "Failed to update notes"
                    )
                }
            }
        }
    }
    
    fun deleteWorkout() {
        viewModelScope.launch {
            val currentSession = _uiState.value.session
            if (currentSession != null) {
                try {
                    _uiState.value = _uiState.value.copy(isLoading = true)
                    
                    workoutRepository.deleteWorkoutSession(currentSession.id)
                    
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isDeleted = true
                    )
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to delete workout"
                    )
                }
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    private fun calculateAnalytics(session: WorkoutSession): WorkoutAnalytics {
        val hrSamples = session.hrSamples
        val geoPoints = session.geoPoints
        
        // HR Zone Analysis
        val hrZones = hrSamples.groupBy { sample ->
            when {
                sample.bpm < 100 -> "Zone 1 (Recovery)"
                sample.bpm < 120 -> "Zone 2 (Aerobic)"
                sample.bpm < 140 -> "Zone 3 (Aerobic)"
                sample.bpm < 160 -> "Zone 4 (Threshold)"
                else -> "Zone 5 (VO2 Max)"
            }
        }.mapValues { (_, samples) -> samples.size }
        
        // Pace Analysis
        val paceData = generateMockPaceData()
        
        // Elevation Analysis
        val elevationData = geoPoints.map { it.altitude }.takeIf { it.isNotEmpty() } ?: listOf(0.0)
        val elevationGain = elevationData.maxOrNull()?.minus(elevationData.minOrNull() ?: 0.0) ?: 0.0
        
        // Split Analysis
        val splits = calculateSplits(session)
        
        return WorkoutAnalytics(
            hrZoneData = hrZones,
            paceData = paceData,
            elevationData = elevationData,
            elevationGain = elevationGain,
            splits = splits,
            averageSpeed = if (session.totalDuration > 0) 
                (session.totalDistance / (session.totalDuration / 1000.0)) * 3.6 else 0.0, // km/h
            maxSpeed = paceData.maxOrNull()?.let { 1.0 / it * 3600 } ?: 0.0 // Convert from pace to speed
        )
    }
    
    private fun generateMockPaceData(): List<Double> {
        // Generate some realistic pace variations for demo
        val basePace = 300.0 // 5 min/km in seconds
        return (1..20).map { 
            basePace + kotlin.random.Random.nextDouble(-60.0, 60.0)
        }
    }
    
    private fun calculateSplits(session: WorkoutSession): List<Split> {
        val splitDistance = 1000.0 // 1km splits
        val totalKm = (session.totalDistance / splitDistance).toInt()
        
        return (1..totalKm).map { splitNumber ->
            val splitTime = session.totalDuration / totalKm // Simple even split
            val splitPace = splitTime / 1000.0 // seconds per km
            
            Split(
                splitNumber = splitNumber,
                distance = splitDistance,
                time = splitTime,
                pace = splitPace
            )
        }
    }
}

data class WorkoutDetailUiState(
    val isLoading: Boolean = false,
    val session: WorkoutSession? = null,
    val analytics: WorkoutAnalytics? = null,
    val isDeleted: Boolean = false,
    val error: String? = null
)

data class WorkoutAnalytics(
    val hrZoneData: Map<String, Int>,
    val paceData: List<Double>,
    val elevationData: List<Double>,
    val elevationGain: Double,
    val splits: List<Split>,
    val averageSpeed: Double,
    val maxSpeed: Double
)

data class Split(
    val splitNumber: Int,
    val distance: Double,
    val time: Long,
    val pace: Double
)
