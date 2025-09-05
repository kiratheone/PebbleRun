package com.arikachmad.pebblerun.bridge.pebble.manager

import com.arikachmad.pebblerun.bridge.pebble.PebbleTransport
import com.arikachmad.pebblerun.bridge.pebble.model.PebbleConnectionState
import com.arikachmad.pebblerun.bridge.pebble.model.PebbleResult
import com.arikachmad.pebblerun.bridge.pebble.model.WorkoutCommand
import com.arikachmad.pebblerun.bridge.pebble.model.WorkoutDataToPebble
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Duration.Companion.seconds

/**
 * Manages Pebble connection state and implements auto-reconnection logic.
 * Satisfies CON-004 (Graceful handling of Pebble disconnections) and ensures 
 * reliable communication for REQ-001 (Real-time HR data) and REQ-006 (Data sync).
 */
class PebbleConnectionManager(
    private val pebbleTransport: PebbleTransport,
    private val scope: CoroutineScope
) {
    companion object {
        private const val RECONNECT_DELAY_SECONDS = 5L
        private const val MAX_RECONNECT_ATTEMPTS = 10
        private const val CONNECTION_TIMEOUT_SECONDS = 30L
        private const val HEALTH_CHECK_INTERVAL_SECONDS = 10L
    }
    
    private val _connectionState = MutableStateFlow(PebbleConnectionState.DISCONNECTED)
    val connectionState: StateFlow<PebbleConnectionState> = _connectionState.asStateFlow()
    
    private val _reconnectAttempts = MutableStateFlow(0)
    val reconnectAttempts: StateFlow<Int> = _reconnectAttempts.asStateFlow()
    
    private var reconnectJob: Job? = null
    private var healthCheckJob: Job? = null
    private var isAutoReconnectEnabled = true
    
    /**
     * Initialize connection manager and start monitoring.
     * Sets up auto-reconnection and health checking.
     */
    suspend fun initialize(): PebbleResult<Unit> {
        return try {
            // Initialize the transport
            val initResult = pebbleTransport.initialize()
            if (initResult is PebbleResult.Error) {
                return initResult
            }
            
            // Start monitoring connection state
            startConnectionMonitoring()
            
            // Start health checking
            startHealthCheck()
            
            // Attempt initial connection
            connect()
            
            PebbleResult.Success(Unit)
        } catch (e: Exception) {
            PebbleResult.Error("Failed to initialize connection manager", e)
        }
    }
    
    /**
     * Manually trigger connection attempt.
     * Supports explicit reconnection from UI or other components.
     */
    suspend fun connect(): PebbleResult<Unit> {
        if (_connectionState.value == PebbleConnectionState.CONNECTING) {
            return PebbleResult.Success(Unit) // Already connecting
        }
        
        _connectionState.value = PebbleConnectionState.CONNECTING
        
        return try {
            withTimeout(CONNECTION_TIMEOUT_SECONDS.seconds) {
                val connected = pebbleTransport.isConnected()
                if (connected) {
                    _connectionState.value = PebbleConnectionState.CONNECTED
                    _reconnectAttempts.value = 0
                    PebbleResult.Success(Unit)
                } else {
                    _connectionState.value = PebbleConnectionState.DISCONNECTED
                    PebbleResult.Error("Failed to connect to Pebble")
                }
            }
        } catch (e: TimeoutCancellationException) {
            _connectionState.value = PebbleConnectionState.ERROR
            PebbleResult.Error("Connection timeout")
        } catch (e: Exception) {
            _connectionState.value = PebbleConnectionState.ERROR
            PebbleResult.Error("Connection failed", e)
        }
    }
    
    /**
     * Disconnect from Pebble and disable auto-reconnection.
     */
    suspend fun disconnect() {
        isAutoReconnectEnabled = false
        reconnectJob?.cancel()
        healthCheckJob?.cancel()
        
        try {
            pebbleTransport.cleanup()
        } catch (e: Exception) {
            // Log error but continue
        }
        
        _connectionState.value = PebbleConnectionState.DISCONNECTED
    }
    
    /**
     * Enable or disable auto-reconnection feature.
     * Useful for battery optimization or user preference.
     */
    fun setAutoReconnectEnabled(enabled: Boolean) {
        isAutoReconnectEnabled = enabled
        
        if (enabled && _connectionState.value == PebbleConnectionState.DISCONNECTED) {
            startReconnection()
        } else if (!enabled) {
            reconnectJob?.cancel()
        }
    }
    
    /**
     * Send workout command with connection state management.
     * Automatically handles disconnections and retries.
     */
    suspend fun sendWorkoutCommand(command: WorkoutCommand): PebbleResult<Unit> {
        return executeWithConnectionCheck {
            pebbleTransport.sendWorkoutCommand(command)
        }
    }
    
    /**
     * Send workout data with connection state management.
     * Automatically handles disconnections and retries.
     */
    suspend fun sendWorkoutData(data: WorkoutDataToPebble): PebbleResult<Unit> {
        return executeWithConnectionCheck {
            pebbleTransport.sendWorkoutData(data)
        }
    }
    
    /**
     * Execute operation with automatic connection checking and recovery.
     */
    private suspend fun executeWithConnectionCheck(
        operation: suspend () -> PebbleResult<Unit>
    ): PebbleResult<Unit> {
        // Check connection before operation
        if (!ensureConnected()) {
            return PebbleResult.Disconnected
        }
        
        val result = operation()
        
        // Handle disconnection during operation
        if (result is PebbleResult.Disconnected) {
            _connectionState.value = PebbleConnectionState.DISCONNECTED
            if (isAutoReconnectEnabled) {
                startReconnection()
            }
        }
        
        return result
    }
    
    /**
     * Ensure Pebble is connected, attempt reconnection if needed.
     */
    private suspend fun ensureConnected(): Boolean {
        if (_connectionState.value == PebbleConnectionState.CONNECTED) {
            return true
        }
        
        if (_connectionState.value == PebbleConnectionState.CONNECTING) {
            // Wait for connection attempt to complete
            return connectionState.first { it != PebbleConnectionState.CONNECTING } == PebbleConnectionState.CONNECTED
        }
        
        // Attempt immediate connection
        val result = connect()
        return result is PebbleResult.Success
    }
    
    /**
     * Start monitoring connection state changes from transport.
     */
    private fun startConnectionMonitoring() {
        scope.launch {
            pebbleTransport.connectionStateFlow
                .distinctUntilChanged()
                .collect { state ->
                    val previousState = _connectionState.value
                    _connectionState.value = state
                    
                    // Handle state transitions
                    when (state) {
                        PebbleConnectionState.DISCONNECTED -> {
                            if (previousState == PebbleConnectionState.CONNECTED && isAutoReconnectEnabled) {
                                startReconnection()
                            }
                        }
                        PebbleConnectionState.CONNECTED -> {
                            _reconnectAttempts.value = 0
                            reconnectJob?.cancel()
                        }
                        PebbleConnectionState.ERROR -> {
                            if (isAutoReconnectEnabled) {
                                startReconnection()
                            }
                        }
                        PebbleConnectionState.CONNECTING -> {
                            // Do nothing, connection in progress
                        }
                    }
                }
        }
    }
    
    /**
     * Start auto-reconnection process with exponential backoff.
     */
    private fun startReconnection() {
        // Cancel existing reconnection job
        reconnectJob?.cancel()
        
        reconnectJob = scope.launch {
            var attempt = 0
            
            while (isAutoReconnectEnabled && 
                   attempt < MAX_RECONNECT_ATTEMPTS && 
                   _connectionState.value != PebbleConnectionState.CONNECTED) {
                
                attempt++
                _reconnectAttempts.value = attempt
                
                // Exponential backoff delay
                val delaySeconds = RECONNECT_DELAY_SECONDS * (1 shl (attempt - 1).coerceAtMost(4))
                delay(delaySeconds.seconds)
                
                if (!isAutoReconnectEnabled) break
                
                // Attempt reconnection
                val result = connect()
                if (result is PebbleResult.Success) {
                    break
                }
            }
            
            // If all attempts failed, set error state
            if (attempt >= MAX_RECONNECT_ATTEMPTS && _connectionState.value != PebbleConnectionState.CONNECTED) {
                _connectionState.value = PebbleConnectionState.ERROR
            }
        }
    }
    
    /**
     * Start periodic health check to detect connection issues.
     */
    private fun startHealthCheck() {
        healthCheckJob = scope.launch {
            while (isActive) {
                delay(HEALTH_CHECK_INTERVAL_SECONDS.seconds)
                
                if (_connectionState.value == PebbleConnectionState.CONNECTED) {
                    // Perform health check by verifying connection
                    val connected = try {
                        pebbleTransport.isConnected()
                    } catch (e: Exception) {
                        false
                    }
                    
                    if (!connected && _connectionState.value == PebbleConnectionState.CONNECTED) {
                        _connectionState.value = PebbleConnectionState.DISCONNECTED
                    }
                }
            }
        }
    }
    
    /**
     * Cleanup resources and stop all monitoring.
     */
    suspend fun cleanup() {
        isAutoReconnectEnabled = false
        reconnectJob?.cancel()
        healthCheckJob?.cancel()
        
        try {
            pebbleTransport.cleanup()
        } catch (e: Exception) {
            // Log error but continue cleanup
        }
        
        _connectionState.value = PebbleConnectionState.DISCONNECTED
    }
}
