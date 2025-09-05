package com.arikachmad.pebblerun.bridge.location.manager

import com.arikachmad.pebblerun.bridge.location.LocationProvider
import com.arikachmad.pebblerun.bridge.location.model.LocationAccuracy
import com.arikachmad.pebblerun.bridge.location.model.LocationData
import com.arikachmad.pebblerun.bridge.location.model.LocationPermission
import com.arikachmad.pebblerun.bridge.location.model.LocationResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Duration.Companion.seconds

/**
 * Manages location services with automatic error recovery and permission handling.
 * Supports REQ-002 (GPS-based tracking) and CON-004 (Graceful error handling).
 * Provides filtered and validated location data per bridge instructions.
 */
class LocationManager(
    private val locationProvider: LocationProvider,
    private val scope: CoroutineScope
) {
    companion object {
        private const val LOCATION_TIMEOUT_SECONDS = 30L
        private const val ACCURACY_THRESHOLD_METERS = 50f
        private const val MAX_LOCATION_AGE_SECONDS = 10
        private const val PERMISSION_CHECK_INTERVAL_SECONDS = 5L
    }
    
    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()
    
    private val _permissionState = MutableStateFlow(LocationPermission.NOT_REQUESTED)
    val permissionState: StateFlow<LocationPermission> = _permissionState.asStateFlow()
    
    private val _lastKnownLocation = MutableStateFlow<LocationData?>(null)
    val lastKnownLocation: StateFlow<LocationData?> = _lastKnownLocation.asStateFlow()
    
    private var permissionCheckJob: Job? = null
    
    /**
     * Filtered and validated location flow.
     * Filters out inaccurate locations and applies quality checks per bridge instructions.
     */
    val locationFlow: Flow<LocationData> = locationProvider.locationFlow
        .filter { location ->
            // Filter by accuracy threshold
            location.isAccurate
        }
        .filter { location ->
            // Filter by age (recent locations only)
            location.isRecent(MAX_LOCATION_AGE_SECONDS)
        }
        .onEach { location ->
            // Update last known location
            _lastKnownLocation.value = location
        }
        .shareIn(scope, SharingStarted.WhileSubscribed(), replay = 1)
    
    /**
     * Initialize location manager and start monitoring permissions.
     */
    suspend fun initialize(): LocationResult<Unit> {
        return try {
            // Check current permission status
            updatePermissionState()
            
            // Start monitoring permissions
            startPermissionMonitoring()
            
            LocationResult.Success(Unit)
        } catch (e: Exception) {
            LocationResult.Error("Failed to initialize location manager", e)
        }
    }
    
    /**
     * Request location permissions if needed.
     * Handles permission flow per bridge instructions.
     */
    suspend fun requestPermissions(): LocationResult<LocationPermission> {
        val currentPermission = _permissionState.value
        
        if (currentPermission == LocationPermission.GRANTED) {
            return LocationResult.Success(currentPermission)
        }
        
        return try {
            val result = locationProvider.requestPermissions()
            updatePermissionState()
            result
        } catch (e: Exception) {
            LocationResult.Error("Failed to request permissions", e)
        }
    }
    
    /**
     * Start location tracking with specified accuracy and interval.
     * Supports REQ-002 (GPS-based tracking) and REQ-004 (Background tracking).
     */
    suspend fun startTracking(
        accuracy: LocationAccuracy = LocationAccuracy.HIGH,
        intervalMs: Long = 1000L
    ): LocationResult<Unit> {
        if (_isTracking.value) {
            return LocationResult.Success(Unit) // Already tracking
        }
        
        // Check permissions first
        val permissionStatus = _permissionState.value
        if (permissionStatus != LocationPermission.GRANTED) {
            return LocationResult.PermissionDenied
        }
        
        // Check if location services are enabled
        if (!locationProvider.isLocationEnabled()) {
            return LocationResult.ServiceDisabled
        }
        
        return try {
            withTimeout(LOCATION_TIMEOUT_SECONDS.seconds) {
                val result = locationProvider.startLocationUpdates(accuracy, intervalMs)
                if (result is LocationResult.Success) {
                    _isTracking.value = true
                }
                result
            }
        } catch (e: TimeoutCancellationException) {
            LocationResult.Error("Location tracking start timeout")
        } catch (e: Exception) {
            LocationResult.Error("Failed to start location tracking", e)
        }
    }
    
    /**
     * Stop location tracking.
     */
    suspend fun stopTracking(): LocationResult<Unit> {
        if (!_isTracking.value) {
            return LocationResult.Success(Unit) // Already stopped
        }
        
        return try {
            val result = locationProvider.stopLocationUpdates()
            if (result is LocationResult.Success) {
                _isTracking.value = false
            }
            result
        } catch (e: Exception) {
            LocationResult.Error("Failed to stop location tracking", e)
        }
    }
    
    /**
     * Get current location without starting continuous tracking.
     * Useful for getting initial position quickly.
     */
    suspend fun getCurrentLocation(): LocationResult<LocationData?> {
        // Check permissions first
        val permissionStatus = _permissionState.value
        if (permissionStatus != LocationPermission.GRANTED) {
            return LocationResult.PermissionDenied
        }
        
        return try {
            withTimeout(LOCATION_TIMEOUT_SECONDS.seconds) {
                val result = locationProvider.getLastKnownLocation()
                if (result is LocationResult.Success && result.data != null) {
                    _lastKnownLocation.value = result.data
                }
                result
            }
        } catch (e: TimeoutCancellationException) {
            LocationResult.Error("Get current location timeout")
        } catch (e: Exception) {
            LocationResult.Error("Failed to get current location", e)
        }
    }
    
    /**
     * Check if location services are available and enabled.
     */
    suspend fun isLocationAvailable(): Boolean {
        return try {
            locationProvider.isLocationEnabled()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Update permission state by checking current status.
     */
    private suspend fun updatePermissionState() {
        try {
            val permission = locationProvider.getPermissionStatus()
            _permissionState.value = permission
        } catch (e: Exception) {
            // Log error but don't crash
        }
    }
    
    /**
     * Start periodic permission monitoring.
     * Helps detect when user changes permissions in device settings.
     */
    private fun startPermissionMonitoring() {
        permissionCheckJob?.cancel()
        
        permissionCheckJob = scope.launch {
            while (isActive) {
                delay(PERMISSION_CHECK_INTERVAL_SECONDS.seconds)
                updatePermissionState()
                
                // If permissions were revoked while tracking, stop tracking
                if (_isTracking.value && _permissionState.value != LocationPermission.GRANTED) {
                    try {
                        stopTracking()
                    } catch (e: Exception) {
                        // Log error but continue monitoring
                    }
                }
            }
        }
    }
    
    /**
     * Cleanup resources and stop all monitoring.
     */
    suspend fun cleanup() {
        permissionCheckJob?.cancel()
        
        try {
            if (_isTracking.value) {
                stopTracking()
            }
        } catch (e: Exception) {
            // Log error but continue cleanup
        }
        
        _isTracking.value = false
        _lastKnownLocation.value = null
    }
}
