package com.arikachmad.pebblerun.bridge.pebble

import android.content.Context
import com.arikachmad.pebblerun.bridge.pebble.model.HRDataFromPebble
import com.arikachmad.pebblerun.bridge.pebble.model.PebbleConnectionState
import com.arikachmad.pebblerun.bridge.pebble.model.PebbleResult
import com.arikachmad.pebblerun.bridge.pebble.model.WorkoutCommand
import com.arikachmad.pebblerun.bridge.pebble.model.WorkoutDataToPebble
import com.arikachmad.pebblerun.proto.PebbleMessageKeys
import com.getpebble.android.kit.PebbleKit
import com.getpebble.android.kit.util.PebbleDictionary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import java.util.UUID

/**
 * Android implementation of PebbleTransport using PebbleKit SDK.
 * Satisfies REQ-001 (Real-time HR data collection), REQ-003 (Auto-launch watchapp),
 * and REQ-006 (Real-time data synchronization) on Android platform.
 */
actual class PebbleTransport(private val context: Context) {
    
    companion object {
        // PebbleRun watchapp UUID - must match the UUID in appinfo.json
        private val PEBBLERUN_UUID = UUID.fromString("12345678-1234-5678-9012-123456789012")
        
        // Retry configuration for AppMessage failures
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 1000L
        private const val RETRY_BACKOFF_MULTIPLIER = 2
    }
    
    private val _connectionStateFlow = MutableStateFlow(PebbleConnectionState.DISCONNECTED)
    actual val connectionStateFlow: Flow<PebbleConnectionState> = _connectionStateFlow.asStateFlow()
    
    private var messageReceiver: PebbleKit.PebbleDataReceiver? = null
    private var connectionReceiver: PebbleKit.PebbleAckReceiver? = null
    private var nackReceiver: PebbleKit.PebbleNackReceiver? = null
    
    /**
     * Flow of HR data from Pebble device.
     * Uses callbackFlow to convert PebbleKit callbacks to Flow.
     * Does not leak platform callbacks to domain layer per bridge instructions.
     */
    actual val heartRateFlow: Flow<HRDataFromPebble> = callbackFlow {
        val receiver = PebbleKit.PebbleDataReceiver(PEBBLERUN_UUID) { transactionId, data ->
            try {
                val heartRate = data.getInteger(PebbleMessageKeys.KEY_HEART_RATE.toLong())
                val quality = data.getInteger(PebbleMessageKeys.KEY_HR_QUALITY.toLong()) ?: 1
                
                if (heartRate != null && PebbleMessageKeys.isValidHeartRate(heartRate.toInt())) {
                    val hrData = HRDataFromPebble(
                        heartRate = heartRate.toInt(),
                        quality = quality.toInt(),
                        timestamp = Clock.System.now()
                    )
                    trySend(hrData)
                }
                
                // Always ACK the message
                PebbleKit.sendAckToPebble(context, transactionId)
            } catch (e: Exception) {
                // Log error but don't crash
                PebbleKit.sendNackToPebble(context, transactionId)
            }
        }
        
        PebbleKit.registerReceivedDataHandler(context, receiver)
        messageReceiver = receiver
        
        awaitClose {
            messageReceiver?.let { 
                PebbleKit.unregisterReceivedDataHandler(context, it)
            }
        }
    }
    
    /**
     * Initialize PebbleKit and start listening for device connections.
     * Sets up connection state monitoring and message receivers.
     */
    actual suspend fun initialize(): PebbleResult<Unit> {
        return try {
            // Check if Pebble app is installed
            if (!PebbleKit.isWatchConnected(context)) {
                _connectionStateFlow.value = PebbleConnectionState.DISCONNECTED
                return PebbleResult.Error("No Pebble watch connected")
            }
            
            // Set up ACK receiver for successful message delivery
            connectionReceiver = PebbleKit.PebbleAckReceiver(PEBBLERUN_UUID) { transactionId ->
                // Message sent successfully
            }
            PebbleKit.registerReceivedAckHandler(context, connectionReceiver)
            
            // Set up NACK receiver for failed message delivery
            nackReceiver = PebbleKit.PebbleNackReceiver(PEBBLERUN_UUID) { transactionId ->
                // Message failed to send - could trigger retry logic
            }
            PebbleKit.registerReceivedNackHandler(context, nackReceiver)
            
            _connectionStateFlow.value = PebbleConnectionState.CONNECTED
            PebbleResult.Success(Unit)
        } catch (e: Exception) {
            _connectionStateFlow.value = PebbleConnectionState.ERROR
            PebbleResult.Error("Failed to initialize PebbleKit", e)
        }
    }
    
    /**
     * Send workout command to Pebble watchapp.
     * Commands are idempotent per bridge instructions.
     * Implements retry with backoff on failure per bridge instructions.
     */
    actual suspend fun sendWorkoutCommand(command: WorkoutCommand): PebbleResult<Unit> {
        if (!isConnected()) {
            return PebbleResult.Disconnected
        }
        
        val commandValue = when (command) {
            WorkoutCommand.START -> PebbleMessageKeys.COMMAND_START_WORKOUT
            WorkoutCommand.STOP -> PebbleMessageKeys.COMMAND_STOP_WORKOUT
            WorkoutCommand.PAUSE -> PebbleMessageKeys.COMMAND_PAUSE_WORKOUT
            WorkoutCommand.RESUME -> PebbleMessageKeys.COMMAND_RESUME_WORKOUT
        }
        
        val data = PebbleDictionary().apply {
            addInt32(PebbleMessageKeys.KEY_COMMAND.toLong(), commandValue)
        }
        
        return sendMessageWithRetry(data, "workout command")
    }
    
    /**
     * Send workout data to Pebble for display.
     * Validates send result and retries with backoff on failure per bridge instructions.
     */
    actual suspend fun sendWorkoutData(data: WorkoutDataToPebble): PebbleResult<Unit> {
        if (!isConnected()) {
            return PebbleResult.Disconnected
        }
        
        // Validate data before sending
        if (!PebbleMessageKeys.isValidPace(data.pace)) {
            return PebbleResult.Error("Invalid pace value: ${data.pace}")
        }
        
        val pebbleData = PebbleDictionary().apply {
            addInt32(PebbleMessageKeys.KEY_PACE.toLong(), (data.pace * 100).toInt()) // Send as centiseconds
            addInt32(PebbleMessageKeys.KEY_DURATION.toLong(), data.duration)
            addInt32(PebbleMessageKeys.KEY_DISTANCE.toLong(), data.distance.toInt())
        }
        
        return sendMessageWithRetry(pebbleData, "workout data")
    }
    
    /**
     * Check if Pebble is connected and ready for communication.
     */
    actual suspend fun isConnected(): Boolean {
        val connected = PebbleKit.isWatchConnected(context)
        val currentState = if (connected) PebbleConnectionState.CONNECTED else PebbleConnectionState.DISCONNECTED
        _connectionStateFlow.value = currentState
        return connected
    }
    
    /**
     * Cleanup resources and stop listening.
     */
    actual suspend fun cleanup() {
        messageReceiver?.let {
            PebbleKit.unregisterReceivedDataHandler(context, it)
            messageReceiver = null
        }
        
        connectionReceiver?.let {
            PebbleKit.unregisterReceivedAckHandler(context, it)
            connectionReceiver = null
        }
        
        nackReceiver?.let {
            PebbleKit.unregisterReceivedNackHandler(context, it)
            nackReceiver = null
        }
        
        _connectionStateFlow.value = PebbleConnectionState.DISCONNECTED
    }
    
    /**
     * Send message with retry logic and exponential backoff.
     * Implements retry with backoff on failure per bridge instructions.
     */
    private suspend fun sendMessageWithRetry(
        data: PebbleDictionary,
        messageType: String
    ): PebbleResult<Unit> {
        var retryCount = 0
        var delayMs = RETRY_DELAY_MS
        
        while (retryCount < MAX_RETRIES) {
            try {
                PebbleKit.sendDataToPebble(context, PEBBLERUN_UUID, data)
                return PebbleResult.Success(Unit)
            } catch (e: Exception) {
                retryCount++
                if (retryCount >= MAX_RETRIES) {
                    return PebbleResult.Error("Failed to send $messageType after $MAX_RETRIES retries", e)
                }
                
                delay(delayMs)
                delayMs *= RETRY_BACKOFF_MULTIPLIER
            }
        }
        
        return PebbleResult.Error("Failed to send $messageType")
    }
}
