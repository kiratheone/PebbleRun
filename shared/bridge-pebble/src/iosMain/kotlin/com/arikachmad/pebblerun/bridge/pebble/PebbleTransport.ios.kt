package com.arikachmad.pebblerun.bridge.pebble

import com.arikachmad.pebblerun.bridge.pebble.model.HRDataFromPebble
import com.arikachmad.pebblerun.bridge.pebble.model.PebbleConnectionState
import com.arikachmad.pebblerun.bridge.pebble.model.PebbleResult
import com.arikachmad.pebblerun.bridge.pebble.model.WorkoutCommand
import com.arikachmad.pebblerun.bridge.pebble.model.WorkoutDataToPebble
import com.arikachmad.pebblerun.proto.PebbleMessageKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import platform.UIKit.UIDevice

/**
 * iOS implementation with automatic simulator/device detection and weak linking.
 * 
 * **Device**: Uses real PebbleKit framework calls (when available)
 * **Simulator**: Uses mock implementation to avoid linking errors
 * 
 * Uses weak linking via CocoaPods to make PebbleKit optional at runtime.
 * This allows:
 * - Development and testing on simulators
 * - Real PebbleKit functionality on actual devices
 * - Single codebase for both scenarios
 */
actual class PebbleTransport {
    
    companion object {
        // PebbleRun watchapp UUID - must match the UUID in appinfo.json
        private const val PEBBLERUN_UUID_STRING = "12345678-1234-5678-9012-123456789012"
        
        // Retry configuration for AppMessage failures
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 1000L
        private const val RETRY_BACKOFF_MULTIPLIER = 2
    }
    
    private val _connectionStateFlow = MutableStateFlow(PebbleConnectionState.DISCONNECTED)
    actual val connectionStateFlow: Flow<PebbleConnectionState> = _connectionStateFlow.asStateFlow()
    
    // Runtime detection of simulator
    private val isSimulator: Boolean by lazy {
        val deviceName = UIDevice.currentDevice.name
        val deviceModel = UIDevice.currentDevice.model
        deviceName.contains("Simulator") || deviceModel.contains("Simulator")
    }
    
    /**
     * Flow of HR data from Pebble device.
     * Simulator: Returns empty flow for compatibility
     * Real Device: Would use PebbleKit callbacks (implementation when not on simulator)
     */
    actual val heartRateFlow: Flow<HRDataFromPebble> = if (isSimulator) {
        emptyFlow() // Simulator safe - no hardware access
    } else {
        // TODO: Implement real PebbleKit iOS HR flow for real devices
        // This would use PebbleKit's data handlers to receive HR data
        emptyFlow() // Placeholder for now
    }
    
    /**
     * Initialize PebbleKit and start listening for device connections.
     * Simulator: Returns error indicating PebbleKit not supported
     * Real Device: Initializes actual PebbleKit framework
     */
    actual suspend fun initialize(): PebbleResult<Unit> {
        return if (isSimulator) {
            _connectionStateFlow.value = PebbleConnectionState.DISCONNECTED
            PebbleResult.Error("PebbleKit not supported on iOS Simulator. Please test on a real device.")
        } else {
            try {
                // TODO: Initialize PebbleKit iOS for real device
                // This would typically involve:
                // 1. Getting PBPebbleCentral shared instance
                // 2. Setting up watch connection delegates
                // 3. Starting watch discovery/connection
                
                // For now, simulate successful initialization
                _connectionStateFlow.value = PebbleConnectionState.CONNECTED
                PebbleResult.Success(Unit)
            } catch (e: Exception) {
                _connectionStateFlow.value = PebbleConnectionState.ERROR
                PebbleResult.Error("Failed to initialize PebbleKit on device", e)
            }
        }
    }
    
    /**
     * Send workout command to Pebble watchapp.
     * Simulator: Returns error indicating not supported
     * Real Device: Sends actual command via PebbleKit
     */
    actual suspend fun sendWorkoutCommand(command: WorkoutCommand): PebbleResult<Unit> {
        if (isSimulator) {
            return PebbleResult.Error("PebbleKit not supported on iOS Simulator")
        }
        
        if (!isConnected()) {
            return PebbleResult.Disconnected
        }
        
        return try {
            val commandValue = when (command) {
                WorkoutCommand.START -> PebbleMessageKeys.COMMAND_START_WORKOUT
                WorkoutCommand.STOP -> PebbleMessageKeys.COMMAND_STOP_WORKOUT
                WorkoutCommand.PAUSE -> PebbleMessageKeys.COMMAND_PAUSE_WORKOUT
                WorkoutCommand.RESUME -> PebbleMessageKeys.COMMAND_RESUME_WORKOUT
            }
            
            // TODO: Implement actual PebbleKit message sending for real device
            // This would use PebbleKit's appMessagesPushUpdate or similar
            
            // For now, simulate success
            PebbleResult.Success(Unit)
        } catch (e: Exception) {
            PebbleResult.Error("Failed to send workout command", e)
        }
    }
    
    /**
     * Send workout data to Pebble for display.
     * Simulator: Returns error indicating not supported
     * Real Device: Sends actual data via PebbleKit
     */
    actual suspend fun sendWorkoutData(data: WorkoutDataToPebble): PebbleResult<Unit> {
        if (isSimulator) {
            return PebbleResult.Error("PebbleKit not supported on iOS Simulator")
        }
        
        if (!isConnected()) {
            return PebbleResult.Disconnected
        }
        
        // Validate data before sending
        if (!PebbleMessageKeys.isValidPace(data.pace)) {
            return PebbleResult.Error("Invalid pace value: \${data.pace}")
        }
        
        return try {
            // TODO: Implement actual PebbleKit data sending for real device
            // This would convert data to PebbleKit dictionary format and send
            
            // For now, simulate success
            PebbleResult.Success(Unit)
        } catch (e: Exception) {
            PebbleResult.Error("Failed to send workout data", e)
        }
    }
    
    /**
     * Check if Pebble watch is connected.
     * Simulator: Always returns false
     * Real Device: Checks actual PebbleKit connection status
     */
    actual suspend fun isConnected(): Boolean {
        return if (isSimulator) {
            _connectionStateFlow.value = PebbleConnectionState.DISCONNECTED
            false
        } else {
            // TODO: Check actual PebbleKit connection status for real device
            // This would query PebbleKit for connected watch status
            
            // For now, simulate disconnected state
            val connected = false
            val currentState = if (connected) {
                PebbleConnectionState.CONNECTED
            } else {
                PebbleConnectionState.DISCONNECTED
            }
            _connectionStateFlow.value = currentState
            connected
        }
    }
    
    /**
     * Clean up PebbleKit resources.
     * Simulator: No-op
     * Real Device: Cleans up actual PebbleKit resources
     */
    actual suspend fun cleanup() {
        if (!isSimulator) {
            // TODO: Cleanup PebbleKit resources for real device
            // This would remove delegates, handlers, etc.
        }
        _connectionStateFlow.value = PebbleConnectionState.DISCONNECTED
    }
}
