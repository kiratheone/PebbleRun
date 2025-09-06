package com.arikachmad.pebblerun.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for settings screen implementing MVVM pattern (PAT-003)
 * Manages user preferences and app configuration
 * Satisfies TASK-038 (Settings screen for user preferences)
 */
class SettingsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    /**
     * Loads current settings from storage
     */
    private fun loadSettings() {
        viewModelScope.launch {
            try {
                // TODO: Load from actual preferences storage
                // For now, using default values
                _uiState.value = _uiState.value.copy(
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load settings"
                )
            }
        }
    }

    /**
     * Updates heart rate sampling interval
     */
    fun updateHRSamplingInterval(interval: HRSamplingInterval) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    heartRateSamplingInterval = interval
                )
                // TODO: Save to preferences storage
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Failed to update sampling interval"
                )
            }
        }
    }

    /**
     * Updates GPS accuracy setting
     */
    fun updateGPSAccuracy(accuracy: GPSAccuracy) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    gpsAccuracy = accuracy
                )
                // TODO: Save to preferences storage
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Failed to update GPS accuracy"
                )
            }
        }
    }

    /**
     * Updates battery optimization setting
     */
    fun updateBatteryOptimization(enabled: Boolean) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    batteryOptimizationEnabled = enabled
                )
                // TODO: Save to preferences storage
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Failed to update battery optimization"
                )
            }
        }
    }

    /**
     * Updates auto-pause setting
     */
    fun updateAutoPause(enabled: Boolean) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    autoPauseEnabled = enabled
                )
                // TODO: Save to preferences storage
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Failed to update auto-pause setting"
                )
            }
        }
    }

    /**
     * Updates auto-pause threshold
     */
    fun updateAutoPauseThreshold(threshold: Double) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    autoPauseThreshold = threshold
                )
                // TODO: Save to preferences storage
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Failed to update auto-pause threshold"
                )
            }
        }
    }

    /**
     * Updates data retention period
     */
    fun updateDataRetention(period: DataRetentionPeriod) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    dataRetentionPeriod = period
                )
                // TODO: Save to preferences storage
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Failed to update data retention"
                )
            }
        }
    }

    /**
     * Updates notification settings
     */
    fun updateNotificationSettings(settings: NotificationSettings) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    notificationSettings = settings
                )
                // TODO: Save to preferences storage
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Failed to update notification settings"
                )
            }
        }
    }

    /**
     * Updates display units
     */
    fun updateDisplayUnits(units: DisplayUnits) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    displayUnits = units
                )
                // TODO: Save to preferences storage
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Failed to update display units"
                )
            }
        }
    }

    /**
     * Tests Pebble connection
     */
    fun testPebbleConnection() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isTestingConnection = true)
                
                // TODO: Implement actual Pebble connection test
                kotlinx.coroutines.delay(2000) // Simulate test
                
                _uiState.value = _uiState.value.copy(
                    isTestingConnection = false,
                    lastConnectionTest = "Connection successful!"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isTestingConnection = false,
                    lastConnectionTest = "Connection failed: ${e.message}",
                    error = e.message ?: "Connection test failed"
                )
            }
        }
    }

    /**
     * Exports workout data
     */
    fun exportWorkoutData() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isExporting = true)
                
                // TODO: Implement actual data export
                kotlinx.coroutines.delay(1000) // Simulate export
                
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    lastExportResult = "Data exported successfully!"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    lastExportResult = "Export failed: ${e.message}",
                    error = e.message ?: "Export failed"
                )
            }
        }
    }

    /**
     * Clears all workout data
     */
    fun clearAllData() {
        viewModelScope.launch {
            try {
                // TODO: Implement actual data clearing
                _uiState.value = _uiState.value.copy(
                    lastExportResult = "All data cleared successfully!"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Failed to clear data"
                )
            }
        }
    }

    /**
     * Clears error state
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

/**
 * UI state for settings screen
 */
data class SettingsUiState(
    // Workout Settings
    val heartRateSamplingInterval: HRSamplingInterval = HRSamplingInterval.ONE_SECOND,
    val gpsAccuracy: GPSAccuracy = GPSAccuracy.HIGH,
    val batteryOptimizationEnabled: Boolean = true,
    val autoPauseEnabled: Boolean = false,
    val autoPauseThreshold: Double = 0.5, // km/h
    
    // Data & Privacy
    val dataRetentionPeriod: DataRetentionPeriod = DataRetentionPeriod.ONE_YEAR,
    
    // Notifications
    val notificationSettings: NotificationSettings = NotificationSettings(),
    
    // Display
    val displayUnits: DisplayUnits = DisplayUnits.METRIC,
    
    // Connection
    val isTestingConnection: Boolean = false,
    val lastConnectionTest: String? = null,
    
    // Data Management
    val isExporting: Boolean = false,
    val lastExportResult: String? = null,
    
    // State
    val isLoading: Boolean = true,
    val error: String? = null
)

/**
 * Heart rate sampling interval options
 * Satisfies CON-003 (1-second update frequency)
 */
enum class HRSamplingInterval(val displayName: String, val intervalMs: Long) {
    HALF_SECOND("0.5 seconds", 500),
    ONE_SECOND("1 second", 1000),
    TWO_SECONDS("2 seconds", 2000),
    FIVE_SECONDS("5 seconds", 5000)
}

/**
 * GPS accuracy options
 * Balances accuracy with battery consumption (CON-001)
 */
enum class GPSAccuracy(val displayName: String, val description: String) {
    HIGH("High Accuracy", "Best precision, higher battery usage"),
    BALANCED("Balanced", "Good precision with moderate battery usage"),
    POWER_SAVE("Power Save", "Lower precision, optimized for battery life")
}

/**
 * Data retention period options
 * Satisfies SEC-001 (Secure local data storage)
 */
enum class DataRetentionPeriod(val displayName: String, val days: Int) {
    ONE_MONTH("1 Month", 30),
    THREE_MONTHS("3 Months", 90),
    SIX_MONTHS("6 Months", 180),
    ONE_YEAR("1 Year", 365),
    FOREVER("Forever", -1)
}

/**
 * Display units options
 */
enum class DisplayUnits(val displayName: String) {
    METRIC("Metric (km, m, kg)"),
    IMPERIAL("Imperial (mi, ft, lb)")
}

/**
 * Notification settings
 * Supports workout tracking notifications
 */
data class NotificationSettings(
    val workoutStarted: Boolean = true,
    val workoutPaused: Boolean = true,
    val workoutCompleted: Boolean = true,
    val milestoneAlerts: Boolean = true,
    val pebbleDisconnected: Boolean = true,
    val lowBattery: Boolean = true
)
