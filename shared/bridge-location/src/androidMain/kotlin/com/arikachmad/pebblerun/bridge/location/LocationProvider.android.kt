package com.arikachmad.pebblerun.bridge.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.arikachmad.pebblerun.bridge.location.model.LocationAccuracy
import com.arikachmad.pebblerun.bridge.location.model.LocationData
import com.arikachmad.pebblerun.bridge.location.model.LocationPermission
import com.arikachmad.pebblerun.bridge.location.model.LocationResult
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.coroutines.resume

/**
 * Android implementation of LocationProvider using FusedLocationProviderClient.
 * Satisfies REQ-002 (GPS-based pace and distance calculation) on Android platform.
 * Handles permissions outside domain per bridge instructions.
 */
actual class LocationProvider(private val context: Context) {
    
    private val fusedLocationClient: FusedLocationProviderClient = 
        LocationServices.getFusedLocationProviderClient(context)
    
    private var locationCallback: LocationCallback? = null
    private var _isTracking = false
    
    actual val isTracking: Boolean
        get() = _isTracking
    
    /**
     * Flow of location updates with timestamps.
     * Uses callbackFlow to convert FusedLocationProviderClient callbacks to Flow.
     * Does not leak platform callbacks to domain layer per bridge instructions.
     */
    actual val locationFlow: Flow<LocationData> = callbackFlow {
        val callback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.locations.forEach { location ->
                    val locationData = LocationData(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        altitude = if (location.hasAltitude()) location.altitude else null,
                        accuracy = location.accuracy,
                        verticalAccuracy = if (location.hasVerticalAccuracy()) location.verticalAccuracyMeters else null,
                        speed = if (location.hasSpeed()) location.speed else null,
                        bearing = if (location.hasBearing()) location.bearing else null,
                        timestamp = Instant.fromEpochMilliseconds(location.time),
                        provider = location.provider
                    )
                    trySend(locationData)
                }
            }
            
            override fun onLocationAvailability(locationAvailability: LocationAvailability) {
                if (!locationAvailability.isLocationAvailable) {
                    // Location is not available - could emit error or handle gracefully
                }
            }
        }
        
        locationCallback = callback
        
        awaitClose {
            stopLocationUpdatesInternal()
        }
    }
    
    /**
     * Current location permission status.
     * Supports SEC-002 (Privacy-compliant location data handling).
     */
    actual suspend fun getPermissionStatus(): LocationPermission {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        val coarseLocationGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        return when {
            fineLocationGranted || coarseLocationGranted -> LocationPermission.GRANTED
            else -> LocationPermission.DENIED
        }
    }
    
    /**
     * Request location permissions from user.
     * Note: This method can only check current permissions.
     * Actual permission request must be handled by the Activity/Fragment.
     */
    actual suspend fun requestPermissions(): LocationResult<LocationPermission> {
        val currentStatus = getPermissionStatus()
        return if (currentStatus == LocationPermission.GRANTED) {
            LocationResult.Success(currentStatus)
        } else {
            LocationResult.PermissionDenied
        }
    }
    
    /**
     * Start receiving location updates.
     * Supports REQ-002 (GPS-based tracking) and REQ-004 (Background tracking).
     */
    @SuppressLint("MissingPermission") // Permission checked before calling
    actual suspend fun startLocationUpdates(
        accuracy: LocationAccuracy,
        intervalMs: Long
    ): LocationResult<Unit> {
        if (getPermissionStatus() != LocationPermission.GRANTED) {
            return LocationResult.PermissionDenied
        }
        
        if (!isLocationEnabled()) {
            return LocationResult.ServiceDisabled
        }
        
        if (_isTracking) {
            return LocationResult.Success(Unit) // Already tracking
        }
        
        val locationRequest = createLocationRequest(accuracy, intervalMs)
        val callback = locationCallback ?: return LocationResult.Error("Location callback not initialized")
        
        return try {
            suspendCancellableCoroutine { continuation ->
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    callback,
                    Looper.getMainLooper()
                ).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        _isTracking = true
                        continuation.resume(LocationResult.Success(Unit))
                    } else {
                        val exception = task.exception
                        continuation.resume(
                            LocationResult.Error(
                                "Failed to start location updates",
                                exception
                            )
                        )
                    }
                }
                
                continuation.invokeOnCancellation {
                    stopLocationUpdatesInternal()
                }
            }
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
    @SuppressLint("MissingPermission") // Permission checked before calling
    actual suspend fun getLastKnownLocation(): LocationResult<LocationData?> {
        if (getPermissionStatus() != LocationPermission.GRANTED) {
            return LocationResult.PermissionDenied
        }
        
        return try {
            suspendCancellableCoroutine { continuation ->
                fusedLocationClient.lastLocation.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val location = task.result
                        val locationData = location?.let {
                            LocationData(
                                latitude = it.latitude,
                                longitude = it.longitude,
                                altitude = if (it.hasAltitude()) it.altitude else null,
                                accuracy = it.accuracy,
                                verticalAccuracy = if (it.hasVerticalAccuracy()) it.verticalAccuracyMeters else null,
                                speed = if (it.hasSpeed()) it.speed else null,
                                bearing = if (it.hasBearing()) it.bearing else null,
                                timestamp = Instant.fromEpochMilliseconds(it.time),
                                provider = it.provider
                            )
                        }
                        continuation.resume(LocationResult.Success(locationData))
                    } else {
                        val exception = task.exception
                        continuation.resume(
                            LocationResult.Error(
                                "Failed to get last known location",
                                exception
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            LocationResult.Error("Failed to get last known location", e)
        }
    }
    
    /**
     * Check if location services are enabled on device.
     */
    actual suspend fun isLocationEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
    
    /**
     * Create LocationRequest based on accuracy and interval requirements.
     */
    private fun createLocationRequest(accuracy: LocationAccuracy, intervalMs: Long): LocationRequest {
        val priority = when (accuracy) {
            LocationAccuracy.HIGH -> Priority.PRIORITY_HIGH_ACCURACY
            LocationAccuracy.BALANCED -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
            LocationAccuracy.LOW -> Priority.PRIORITY_LOW_POWER
            LocationAccuracy.PASSIVE -> Priority.PRIORITY_PASSIVE
        }
        
        return LocationRequest.Builder(priority, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2) // Allow faster updates if available
            .setMaxUpdateDelayMillis(intervalMs * 2) // Batch updates if needed for battery
            .build()
    }
    
    /**
     * Internal method to stop location updates.
     */
    private fun stopLocationUpdatesInternal() {
        locationCallback?.let { callback ->
            fusedLocationClient.removeLocationUpdates(callback)
        }
        _isTracking = false
    }
}
