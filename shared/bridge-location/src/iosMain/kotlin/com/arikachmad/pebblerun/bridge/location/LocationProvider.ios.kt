package com.arikachmad.pebblerun.bridge.location

import com.arikachmad.pebblerun.bridge.location.model.LocationAccuracy
import com.arikachmad.pebblerun.bridge.location.model.LocationData
import com.arikachmad.pebblerun.bridge.location.model.LocationPermission
import com.arikachmad.pebblerun.bridge.location.model.LocationResult
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import platform.CoreLocation.*
import platform.Foundation.NSError
import platform.darwin.NSObject

/**
 * iOS implementation of LocationProvider using CoreLocation.
 * Satisfies REQ-002 (GPS-based pace and distance calculation) on iOS platform.
 * Handles permissions outside domain per bridge instructions.
 * 
 * Note: This implementation requires CoreLocation framework and proper Info.plist permissions.
 * Background location usage requires "Location updates" background mode.
 */
actual class LocationProvider : NSObject(), CLLocationManagerDelegateProtocol {
    
    private val locationManager = CLLocationManager()
    private var _isTracking = false
    
    // Flow channels for sending location updates
    private var locationChannel: kotlinx.coroutines.channels.SendChannel<LocationData>? = null
    
    actual val isTracking: Boolean
        get() = _isTracking
    
    init {
        locationManager.delegate = this
    }
    
    /**
     * Flow of location updates with timestamps.
     * Uses callbackFlow to convert CoreLocation delegate callbacks to Flow.
     * Does not leak platform callbacks to domain layer per bridge instructions.
     */
    actual val locationFlow: Flow<LocationData> = callbackFlow {
        locationChannel = this.channel
        
        awaitClose {
            stopLocationUpdatesInternal()
            locationChannel = null
        }
    }
    
    /**
     * Current location permission status.
     * Supports SEC-002 (Privacy-compliant location data handling).
     */
    actual suspend fun getPermissionStatus(): LocationPermission {
        val authStatus = locationManager.authorizationStatus
        return when (authStatus) {
            kCLAuthorizationStatusAuthorizedAlways,
            kCLAuthorizationStatusAuthorizedWhenInUse -> LocationPermission.GRANTED
            kCLAuthorizationStatusDenied -> LocationPermission.DENIED_FOREVER
            kCLAuthorizationStatusRestricted -> LocationPermission.DENIED
            kCLAuthorizationStatusNotDetermined -> LocationPermission.NOT_REQUESTED
            else -> LocationPermission.DENIED
        }
    }
    
    /**
     * Request location permissions from user.
     * Requests "when in use" permission initially.
     * For background tracking, "always" permission would be needed.
     */
    actual suspend fun requestPermissions(): LocationResult<LocationPermission> {
        val currentStatus = getPermissionStatus()
        
        return when (currentStatus) {
            LocationPermission.GRANTED -> LocationResult.Success(currentStatus)
            LocationPermission.NOT_REQUESTED -> {
                // Request when-in-use permission
                locationManager.requestWhenInUseAuthorization()
                // Note: The actual result will come through delegate callback
                // For this implementation, we return current status
                LocationResult.Success(getPermissionStatus())
            }
            LocationPermission.DENIED_FOREVER -> LocationResult.PermissionDenied
            else -> LocationResult.PermissionDenied
        }
    }
    
    /**
     * Start receiving location updates.
     * Supports REQ-002 (GPS-based tracking) and REQ-004 (Background tracking).
     */
    actual suspend fun startLocationUpdates(
        accuracy: LocationAccuracy,
        intervalMs: Long
    ): LocationResult<Unit> {
        val permissionStatus = getPermissionStatus()
        if (permissionStatus != LocationPermission.GRANTED) {
            return LocationResult.PermissionDenied
        }
        
        if (!isLocationEnabled()) {
            return LocationResult.ServiceDisabled
        }
        
        if (_isTracking) {
            return LocationResult.Success(Unit) // Already tracking
        }
        
        return try {
            // Configure location manager based on accuracy requirements
            locationManager.desiredAccuracy = when (accuracy) {
                LocationAccuracy.HIGH -> kCLLocationAccuracyBest
                LocationAccuracy.BALANCED -> kCLLocationAccuracyNearestTenMeters
                LocationAccuracy.LOW -> kCLLocationAccuracyHundredMeters
                LocationAccuracy.PASSIVE -> kCLLocationAccuracyKilometer
            }
            
            // Set distance filter based on accuracy (higher accuracy = smaller filter)
            locationManager.distanceFilter = when (accuracy) {
                LocationAccuracy.HIGH -> 5.0 // 5 meters
                LocationAccuracy.BALANCED -> 10.0 // 10 meters
                LocationAccuracy.LOW -> 50.0 // 50 meters
                LocationAccuracy.PASSIVE -> 100.0 // 100 meters
            }
            
            // Start location updates
            locationManager.startUpdatingLocation()
            _isTracking = true
            
            LocationResult.Success(Unit)
        } catch (e: Exception) {
            LocationResult.Error("Failed to start location updates", e)
        }
    }
    
    /**
     * Stop receiving location updates.
     */
    actual suspend fun stopLocationUpdates(): LocationResult<Unit> {
        return try {
            stopLocationUpdatesInternal()
            LocationResult.Success(Unit)
        } catch (e: Exception) {
            LocationResult.Error("Failed to stop location updates", e)
        }
    }
    
    /**
     * Get last known location without starting continuous updates.
     */
    actual suspend fun getLastKnownLocation(): LocationResult<LocationData?> {
        val permissionStatus = getPermissionStatus()
        if (permissionStatus != LocationPermission.GRANTED) {
            return LocationResult.PermissionDenied
        }
        
        val lastLocation = locationManager.location
        val locationData = lastLocation?.let { location ->
            LocationData(
                latitude = location.coordinate.latitude,
                longitude = location.coordinate.longitude,
                altitude = if (location.altitude != -1.0) location.altitude else null,
                accuracy = location.horizontalAccuracy.toFloat(),
                verticalAccuracy = if (location.verticalAccuracy >= 0) location.verticalAccuracy.toFloat() else null,
                speed = if (location.speed >= 0) location.speed.toFloat() else null,
                bearing = if (location.course >= 0) location.course.toFloat() else null,
                timestamp = Instant.fromEpochMilliseconds((location.timestamp.timeIntervalSince1970 * 1000).toLong()),
                provider = "CoreLocation"
            )
        }
        
        return LocationResult.Success(locationData)
    }
    
    /**
     * Check if location services are enabled on device.
     */
    actual suspend fun isLocationEnabled(): Boolean {
        return CLLocationManager.locationServicesEnabled()
    }
    
    /**
     * Internal method to stop location updates.
     */
    private fun stopLocationUpdatesInternal() {
        locationManager.stopUpdatingLocation()
        _isTracking = false
    }
    
    // MARK: - CLLocationManagerDelegate
    
    /**
     * Called when new location data is available.
     * Converts CLLocation to LocationData and sends through Flow.
     */
    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        didUpdateLocations.forEach { location ->
            if (location is CLLocation) {
                // Filter out invalid locations
                if (location.horizontalAccuracy < 0) return@forEach
                
                val locationData = LocationData(
                    latitude = location.coordinate.latitude,
                    longitude = location.coordinate.longitude,
                    altitude = if (location.altitude != -1.0) location.altitude else null,
                    accuracy = location.horizontalAccuracy.toFloat(),
                    verticalAccuracy = if (location.verticalAccuracy >= 0) location.verticalAccuracy.toFloat() else null,
                    speed = if (location.speed >= 0) location.speed.toFloat() else null,
                    bearing = if (location.course >= 0) location.course.toFloat() else null,
                    timestamp = Instant.fromEpochMilliseconds((location.timestamp.timeIntervalSince1970 * 1000).toLong()),
                    provider = "CoreLocation"
                )
                
                // Send location through Flow
                locationChannel?.trySend(locationData)
            }
        }
    }
    
    /**
     * Called when location manager fails with an error.
     */
    override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
        // Handle location errors
        // Could emit error through a separate error flow if needed
    }
    
    /**
     * Called when authorization status changes.
     */
    override fun locationManager(manager: CLLocationManager, didChangeAuthorizationStatus: CLAuthorizationStatus) {
        // Handle permission changes
        // Could emit permission state changes through a separate flow if needed
    }
}
