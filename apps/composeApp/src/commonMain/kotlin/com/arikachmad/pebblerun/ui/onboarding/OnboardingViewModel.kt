package com.arikachmad.pebblerun.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for onboarding flow implementing MVVM pattern (PAT-003)
 * Manages Pebble connection setup and app introduction
 * Satisfies TASK-039 (Onboarding flow for Pebble connection setup)
 */
class OnboardingViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    /**
     * Navigates to the next onboarding step
     */
    fun nextStep() {
        val currentStep = _uiState.value.currentStep
        val nextStep = when (currentStep) {
            OnboardingStep.WELCOME -> OnboardingStep.PERMISSIONS
            OnboardingStep.PERMISSIONS -> OnboardingStep.PEBBLE_SETUP
            OnboardingStep.PEBBLE_SETUP -> OnboardingStep.PEBBLE_CONNECTION
            OnboardingStep.PEBBLE_CONNECTION -> OnboardingStep.COMPLETE
            OnboardingStep.COMPLETE -> OnboardingStep.COMPLETE
        }
        
        _uiState.value = _uiState.value.copy(currentStep = nextStep)
    }

    /**
     * Navigates to the previous onboarding step
     */
    fun previousStep() {
        val currentStep = _uiState.value.currentStep
        val previousStep = when (currentStep) {
            OnboardingStep.WELCOME -> OnboardingStep.WELCOME
            OnboardingStep.PERMISSIONS -> OnboardingStep.WELCOME
            OnboardingStep.PEBBLE_SETUP -> OnboardingStep.PERMISSIONS
            OnboardingStep.PEBBLE_CONNECTION -> OnboardingStep.PEBBLE_SETUP
            OnboardingStep.COMPLETE -> OnboardingStep.PEBBLE_CONNECTION
        }
        
        _uiState.value = _uiState.value.copy(currentStep = previousStep)
    }

    /**
     * Updates permission status
     */
    fun updatePermissionStatus(permission: Permission, granted: Boolean) {
        val currentPermissions = _uiState.value.permissions.toMutableMap()
        currentPermissions[permission] = granted
        
        _uiState.value = _uiState.value.copy(permissions = currentPermissions)
    }

    /**
     * Starts Pebble connection process
     */
    fun startPebbleConnection() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isConnecting = true,
                    connectionError = null
                )
                
                // TODO: Implement actual Pebble connection
                kotlinx.coroutines.delay(3000) // Simulate connection process
                
                // Simulate success/failure
                val success = true // For demo purposes
                
                if (success) {
                    _uiState.value = _uiState.value.copy(
                        isConnecting = false,
                        pebbleConnected = true
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isConnecting = false,
                        connectionError = "Failed to connect to Pebble. Please make sure your device is nearby and try again."
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isConnecting = false,
                    connectionError = e.message ?: "An unexpected error occurred"
                )
            }
        }
    }

    /**
     * Retries Pebble connection
     */
    fun retryConnection() {
        _uiState.value = _uiState.value.copy(connectionError = null)
        startPebbleConnection()
    }

    /**
     * Skips onboarding (for testing purposes)
     */
    fun skipOnboarding() {
        _uiState.value = _uiState.value.copy(currentStep = OnboardingStep.COMPLETE)
    }

    /**
     * Completes onboarding
     */
    fun completeOnboarding() {
        viewModelScope.launch {
            // TODO: Save onboarding completion to preferences
            _uiState.value = _uiState.value.copy(isCompleted = true)
        }
    }

    /**
     * Clears error state
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(connectionError = null)
    }
}

/**
 * UI state for onboarding flow
 */
data class OnboardingUiState(
    val currentStep: OnboardingStep = OnboardingStep.WELCOME,
    val permissions: Map<Permission, Boolean> = mapOf(
        Permission.LOCATION to false,
        Permission.BLUETOOTH to false,
        Permission.NOTIFICATION to false
    ),
    val isConnecting: Boolean = false,
    val pebbleConnected: Boolean = false,
    val connectionError: String? = null,
    val isCompleted: Boolean = false
) {
    val canProceed: Boolean
        get() = when (currentStep) {
            OnboardingStep.WELCOME -> true
            OnboardingStep.PERMISSIONS -> permissions.values.all { it }
            OnboardingStep.PEBBLE_SETUP -> true
            OnboardingStep.PEBBLE_CONNECTION -> pebbleConnected
            OnboardingStep.COMPLETE -> true
        }

    val progressPercentage: Float
        get() = when (currentStep) {
            OnboardingStep.WELCOME -> 0.2f
            OnboardingStep.PERMISSIONS -> 0.4f
            OnboardingStep.PEBBLE_SETUP -> 0.6f
            OnboardingStep.PEBBLE_CONNECTION -> 0.8f
            OnboardingStep.COMPLETE -> 1.0f
        }

    val stepTitle: String
        get() = when (currentStep) {
            OnboardingStep.WELCOME -> "Welcome to PebbleRun"
            OnboardingStep.PERMISSIONS -> "Grant Permissions"
            OnboardingStep.PEBBLE_SETUP -> "Pebble Setup"
            OnboardingStep.PEBBLE_CONNECTION -> "Connect Your Pebble"
            OnboardingStep.COMPLETE -> "All Set!"
        }
}

/**
 * Onboarding steps
 */
enum class OnboardingStep {
    WELCOME,
    PERMISSIONS,
    PEBBLE_SETUP,
    PEBBLE_CONNECTION,
    COMPLETE
}

/**
 * Required permissions for the app
 * Satisfies SEC-002 (Privacy-compliant location data handling)
 */
enum class Permission(val displayName: String, val description: String) {
    LOCATION(
        "Location Access",
        "Required for GPS tracking during workouts. Your location data stays on your device."
    ),
    BLUETOOTH(
        "Bluetooth Access",
        "Required to connect to your Pebble smartwatch for heart rate monitoring."
    ),
    NOTIFICATION(
        "Notifications",
        "Optional. Allows the app to show workout progress and status updates."
    )
}
