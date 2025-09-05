package com.arikachmad.pebblerun.bridge.pebble

import com.arikachmad.pebblerun.bridge.pebble.model.HRDataFromPebble
import com.arikachmad.pebblerun.bridge.pebble.model.PebbleConnectionState
import com.arikachmad.pebblerun.bridge.pebble.model.PebbleResult
import com.arikachmad.pebblerun.bridge.pebble.model.WorkoutCommand
import com.arikachmad.pebblerun.bridge.pebble.model.WorkoutDataToPebble
import kotlinx.coroutines.flow.Flow

/**
 * Cross-platform interface for Pebble communication.
 * Satisfies REQ-001 (Real-time HR data collection), REQ-003 (Auto-launch watchapp),
 * and REQ-006 (Real-time data synchronization).
 * 
 * Implementation follows PAT-002 (Repository pattern) and uses expect/actual
 * for platform-specific PebbleKit integration.
 */
expect class PebbleTransport {
    
    /**
     * Flow of HR data from Pebble device.
     * Satisfies REQ-001 and CON-003 (1-second update frequency).
     * Does not leak platform callbacks to domain layer per bridge instructions.
     */
    val heartRateFlow: Flow<HRDataFromPebble>
    
    /**
     * Flow of connection state changes.
     * Supports CON-004 (Graceful handling of Pebble disconnections).
     */
    val connectionStateFlow: Flow<PebbleConnectionState>
    
    /**
     * Initialize PebbleKit and start listening for device connections.
     * Must be called before other operations.
     */
    suspend fun initialize(): PebbleResult<Unit>
    
    /**
     * Send workout command to Pebble watchapp.
     * Commands are idempotent per bridge instructions.
     * Supports REQ-003 (Auto-launch PebbleRun watchapp).
     */
    suspend fun sendWorkoutCommand(command: WorkoutCommand): PebbleResult<Unit>
    
    /**
     * Send workout data to Pebble for display.
     * Satisfies REQ-006 (Mobile → Pebble: Pace/Duration data sync).
     * Validates send result and retries with backoff on failure per bridge instructions.
     */
    suspend fun sendWorkoutData(data: WorkoutDataToPebble): PebbleResult<Unit>
    
    /**
     * Check if Pebble is connected and ready for communication.
     * Supports connection state management requirements.
     */
    suspend fun isConnected(): Boolean
    
    /**
     * Cleanup resources and stop listening.
     * Should be called when transport is no longer needed.
     */
    suspend fun cleanup()
}
