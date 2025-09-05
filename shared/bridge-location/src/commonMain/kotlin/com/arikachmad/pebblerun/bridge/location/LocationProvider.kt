package com.arikachmad.pebblerun.bridge.location

import com.arikachmad.pebblerun.bridge.location.model.LocationAccuracy
import com.arikachmad.pebblerun.bridge.location.model.LocationData
import com.arikachmad.pebblerun.bridge.location.model.LocationPermission
import com.arikachmad.pebblerun.bridge.location.model.LocationResult
import kotlinx.coroutines.flow.Flow

/**
 * Cross-platform interface for location services.
 * Satisfies REQ-002 (GPS-based pace and distance calculation) and SEC-002 (Privacy compliance).
 * 
 * Implementation follows PAT-002 (Repository pattern) and uses expect/actual
 * for platform-specific location services (Android: FusedLocation, iOS: CoreLocation).
 * 
 * Per bridge instructions:
 * - Exposes Flow of positions with timestamps
 * - Handles permissions outside domain
 * - Smoothing & pace calc live in domain/data, not here
 */
expect class LocationProvider {
    
    /**
     * Flow of location updates with timestamps.
     * Satisfies REQ-002 and CON-003 (1-second update frequency).
     * Handle permissions outside domain per bridge instructions.
     */
    val locationFlow: Flow<LocationData>
    
    /**
     * Current location permission status.
     * Supports SEC-002 (Privacy-compliant location data handling).
     */
    suspend fun getPermissionStatus(): LocationPermission
    
    /**
     * Request location permissions from user.
     * Must be called before starting location updates.
     * Handles permissions outside domain per bridge instructions.
     */
    suspend fun requestPermissions(): LocationResult<LocationPermission>
    
    /**
     * Start receiving location updates.
     * Supports REQ-002 (GPS-based tracking) and REQ-004 (Background tracking).
     * 
     * @param accuracy Desired accuracy level for battery optimization
     * @param intervalMs Update interval in milliseconds (minimum 1000ms per CON-003)
     */
    suspend fun startLocationUpdates(
        accuracy: LocationAccuracy = LocationAccuracy.HIGH,
        intervalMs: Long = 1000L
    ): LocationResult<Unit>
    
    /**
     * Stop receiving location updates.
     * Should be called when location tracking is no longer needed.
     */
    suspend fun stopLocationUpdates(): LocationResult<Unit>
    
    /**
     * Get last known location without starting continuous updates.
     * Useful for getting initial position quickly.
     */
    suspend fun getLastKnownLocation(): LocationResult<LocationData?>
    
    /**
     * Check if location services are enabled on device.
     * Supports error handling for disabled GPS.
     */
    suspend fun isLocationEnabled(): Boolean
    
    /**
     * Check if currently receiving location updates.
     * Useful for UI state management.
     */
    val isTracking: Boolean
}
