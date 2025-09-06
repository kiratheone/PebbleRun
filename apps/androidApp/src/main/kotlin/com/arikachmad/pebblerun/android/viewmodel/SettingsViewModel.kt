package com.arikachmad.pebblerun.android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arikachmad.pebblerun.shared.ui.UIBridge
import com.arikachmad.pebblerun.shared.ui.PlatformActions
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Simple UserSettings data class for MVP
 */
data class UserSettings(
    val hrAlert: Boolean = true,
    val paceAlert: Boolean = true,
    val distanceUnit: String = "km"
)

/**
 * Android-specific ViewModel for settings management using StateFlow
 * Satisfies REQ-011 (Create Android-specific ViewModels with StateFlow integration)
 * Satisfies PAT-001 (MVVM pattern with platform-specific ViewModels for Android)
 * Note: Using simplified implementation until settings use cases are available
 */
class SettingsViewModel(
    private val platformActions: PlatformActions,
    private val uiBridge: UIBridge
) : ViewModel() {
    
    // UI State
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    // Events
    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()
    
    // Error handling
    private val _errors = MutableSharedFlow<String>()
    val errors: SharedFlow<String> = _errors.asSharedFlow()
    
    init {
        loadSettings()
        checkPebbleConnection()
    }
    
    /**
     * Load user settings from the domain layer
     * TODO: Implement with GetUserSettingsUseCase when available
     */
    fun loadSettings() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                
                // TODO: Replace with actual use case when available
                // For now, use default settings
                val defaultSettings = UserSettings()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    userSettings = defaultSettings,
                    error = null
                )
                
            } catch (e: Exception) {
                _errors.emit(e.message ?: "Failed to load settings")
                _uiState.value = _uiState.value.copy(isLoading = false)
                uiBridge.handleError(e)
            }
        }
    }
    
    /**
     * Update user settings
     * TODO: Implement with UpdateUserSettingsUseCase when available
     */
    fun updateSettings(newSettings: UserSettings) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                
                // TODO: Replace with actual use case when available
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    userSettings = newSettings
                )
                _events.emit(SettingsEvent.SettingsUpdated)
                
            } catch (e: Exception) {
                _errors.emit(e.message ?: "Unknown error occurred")
                _uiState.value = _uiState.value.copy(isLoading = false)
                uiBridge.handleError(e)
            }
        }
    }
    
    /**
     * Check Pebble connection status
     */
    fun checkPebbleConnection() {
        viewModelScope.launch {
            try {
                val isConnected = platformActions.isPebbleConnected()
                _uiState.value = _uiState.value.copy(isPebbleConnected = isConnected)
                
                if (!isConnected) {
                    _events.emit(SettingsEvent.PebbleDisconnected)
                }
                
            } catch (e: Exception) {
                _errors.emit("Failed to check Pebble connection")
                uiBridge.handleError(e)
            }
        }
    }
    
    /**
     * Request permissions
     */
    fun requestPermissions() {
        viewModelScope.launch {
            try {
                val granted = uiBridge.requestPermissions()
                _uiState.value = _uiState.value.copy(hasRequiredPermissions = granted)
                
                if (granted) {
                    _events.emit(SettingsEvent.PermissionsGranted)
                } else {
                    _events.emit(SettingsEvent.PermissionsDenied)
                }
                
            } catch (e: Exception) {
                _errors.emit("Failed to request permissions")
                uiBridge.handleError(e)
            }
        }
    }
    
    /**
     * Clear errors
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    /**
     * Show about dialog
     */
    fun showAbout() {
        viewModelScope.launch {
            _events.emit(SettingsEvent.ShowAbout)
        }
    }
}

/**
 * UI State for settings screen
 */
data class SettingsUiState(
    val isLoading: Boolean = false,
    val userSettings: UserSettings? = null,
    val isPebbleConnected: Boolean = false,
    val hasRequiredPermissions: Boolean = false,
    val error: String? = null
)

/**
 * Events emitted by the SettingsViewModel
 */
sealed class SettingsEvent {
    object SettingsUpdated : SettingsEvent()
    object PebbleDisconnected : SettingsEvent()
    object PermissionsGranted : SettingsEvent()
    object PermissionsDenied : SettingsEvent()
    object ShowAbout : SettingsEvent()
}
