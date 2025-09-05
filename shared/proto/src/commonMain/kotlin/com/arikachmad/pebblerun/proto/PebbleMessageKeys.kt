package com.arikachmad.pebblerun.proto

/**
 * AppMessage keys for communication between PebbleRun mobile app and Pebble watchapp.
 * Satisfies REQ-001 (Real-time HR data collection) and REQ-006 (Real-time data synchronization).
 */
object PebbleMessageKeys {
    // Commands from mobile to Pebble
    const val KEY_COMMAND = 0x01
    const val COMMAND_START_WORKOUT = 1
    const val COMMAND_STOP_WORKOUT = 2
    const val COMMAND_PAUSE_WORKOUT = 3
    const val COMMAND_RESUME_WORKOUT = 4
    
    // Data from mobile to Pebble
    const val KEY_PACE = 0x02          // Current pace in seconds per kilometer
    const val KEY_DURATION = 0x03      // Workout duration in seconds
    const val KEY_DISTANCE = 0x04      // Distance in meters
    
    // Data from Pebble to mobile
    const val KEY_HEART_RATE = 0x10    // Heart rate in BPM
    const val KEY_HR_QUALITY = 0x11    // HR quality indicator (0=bad, 1=ok, 2=good)
    const val KEY_WATCHAPP_STATE = 0x12 // Watchapp state (0=stopped, 1=running, 2=paused)
    
    // Status and error codes
    const val KEY_STATUS = 0x20
    const val STATUS_OK = 0
    const val STATUS_ERROR = 1
    const val STATUS_NOT_READY = 2
    
    // Validation
    fun isValidCommand(command: Int): Boolean {
        return command in COMMAND_START_WORKOUT..COMMAND_RESUME_WORKOUT
    }
    
    fun isValidHeartRate(heartRate: Int): Boolean {
        return heartRate in 30..220
    }
    
    fun isValidPace(pace: Float): Boolean {
        return pace >= 0f && pace <= 3600f // Max 1 hour per km
    }
}
