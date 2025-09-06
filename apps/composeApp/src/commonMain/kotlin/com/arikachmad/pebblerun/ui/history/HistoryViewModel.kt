package com.arikachmad.pebblerun.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arikachmad.pebblerun.domain.model.WorkoutSession
import com.arikachmad.pebblerun.domain.repository.WorkoutRepository
import com.arikachmad.pebblerun.data.repository.MockWorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HistoryViewModel : ViewModel() {
    
    private val workoutRepository: WorkoutRepository = MockWorkoutRepository()
    
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()
    
    init {
        loadWorkoutHistory()
    }
    
    fun loadWorkoutHistory() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                
                workoutRepository.getAllWorkoutSessions().collectLatest { sessions ->
                    val filteredSessions = applyFilters(sessions)
                    val summary = calculateSummary(sessions)
                    
                    _uiState.value = _uiState.value.copy(
                        sessions = filteredSessions,
                        allSessions = sessions,
                        summary = summary,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load workout history"
                )
            }
        }
    }
    
    fun searchSessions(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        applyCombinedFilters()
    }
    
    fun filterByDateRange(startDate: kotlinx.datetime.LocalDate?, endDate: kotlinx.datetime.LocalDate?) {
        _uiState.value = _uiState.value.copy(
            startDateFilter = startDate,
            endDateFilter = endDate
        )
        applyCombinedFilters()
    }
    
    fun sortBy(sortBy: SortBy, ascending: Boolean = true) {
        _uiState.value = _uiState.value.copy(
            sortBy = sortBy,
            sortAscending = ascending
        )
        applyCombinedFilters()
    }
    
    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            try {
                workoutRepository.deleteWorkoutSession(sessionId)
                // The flow will automatically update with the new data
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Failed to delete session"
                )
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    private fun applyCombinedFilters() {
        val sessions = applyFilters(_uiState.value.allSessions)
        _uiState.value = _uiState.value.copy(sessions = sessions)
    }
    
    private fun applyFilters(sessions: List<WorkoutSession>): List<WorkoutSession> {
        var filtered = sessions
        
        // Apply search filter
        val query = _uiState.value.searchQuery
        if (query.isNotBlank()) {
            filtered = filtered.filter { session ->
                session.notes.contains(query, ignoreCase = true) ||
                session.startTime.toString().contains(query, ignoreCase = true) ||
                session.totalDuration.toString().contains(query)
            }
        }
        
        // Apply date range filter
        val startDate = _uiState.value.startDateFilter
        val endDate = _uiState.value.endDateFilter
        
        if (startDate != null || endDate != null) {
            filtered = filtered.filter { session ->
                val sessionDate = session.startTime.toString().substring(0, 10) // Simple date extraction
                val sessionLocalDate = try {
                    kotlinx.datetime.LocalDate.parse(sessionDate)
                } catch (e: Exception) {
                    return@filter true // Include if parsing fails
                }
                
                val afterStart = startDate?.let { sessionLocalDate >= it } ?: true
                val beforeEnd = endDate?.let { sessionLocalDate <= it } ?: true
                afterStart && beforeEnd
            }
        }
        
        // Apply sorting
        return when (_uiState.value.sortBy) {
            SortBy.DATE -> {
                if (_uiState.value.sortAscending) filtered.sortedBy { it.startTime }
                else filtered.sortedByDescending { it.startTime }
            }
            SortBy.DURATION -> {
                if (_uiState.value.sortAscending) filtered.sortedBy { it.totalDuration }
                else filtered.sortedByDescending { it.totalDuration }
            }
            SortBy.DISTANCE -> {
                if (_uiState.value.sortAscending) filtered.sortedBy { it.totalDistance }
                else filtered.sortedByDescending { it.totalDistance }
            }
            SortBy.PACE -> {
                if (_uiState.value.sortAscending) filtered.sortedBy { it.averagePace }
                else filtered.sortedByDescending { it.averagePace }
            }
        }
    }
    
    private fun calculateSummary(sessions: List<WorkoutSession>): WorkoutSummary {
        val completedSessions = sessions.filter { it.status == com.arikachmad.pebblerun.domain.model.WorkoutStatus.COMPLETED }
        
        return WorkoutSummary(
            totalWorkouts = completedSessions.size,
            totalDistance = completedSessions.sumOf { it.totalDistance },
            totalDuration = completedSessions.sumOf { it.totalDuration },
            averageHeartRate = if (completedSessions.isNotEmpty()) 
                completedSessions.sumOf { it.averageHeartRate } / completedSessions.size else 0,
            totalCalories = completedSessions.sumOf { it.calories },
            averagePace = if (completedSessions.isNotEmpty())
                completedSessions.sumOf { it.averagePace } / completedSessions.size else 0.0
        )
    }
}

data class HistoryUiState(
    val isLoading: Boolean = false,
    val sessions: List<WorkoutSession> = emptyList(),
    val allSessions: List<WorkoutSession> = emptyList(),
    val summary: WorkoutSummary = WorkoutSummary(),
    val searchQuery: String = "",
    val startDateFilter: kotlinx.datetime.LocalDate? = null,
    val endDateFilter: kotlinx.datetime.LocalDate? = null,
    val sortBy: SortBy = SortBy.DATE,
    val sortAscending: Boolean = false,
    val error: String? = null
)

data class WorkoutSummary(
    val totalWorkouts: Int = 0,
    val totalDistance: Double = 0.0,
    val totalDuration: Long = 0,
    val averageHeartRate: Int = 0,
    val totalCalories: Int = 0,
    val averagePace: Double = 0.0
)

enum class SortBy {
    DATE, DURATION, DISTANCE, PACE
}
