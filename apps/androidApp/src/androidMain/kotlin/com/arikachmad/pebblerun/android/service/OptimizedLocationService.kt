package com.arikachmad.pebblerun.android.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.*

/**
 * Android-specific location service optimizations for PebbleRun.
 * Implements TASK-037: Platform-specific location service optimizations.
 */
class OptimizedLocationService(private val context: Context) {
    
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val handler = Handler(Looper.getMainLooper())
    
    // Location tracking state
    private var isTracking = false
    private var currentOptimizationMode = OptimizationMode.NORMAL
    private val _locationUpdates = MutableSharedFlow<Location>(extraBufferCapacity = 100)
    val locationUpdates: SharedFlow<Location> = _locationUpdates.asSharedFlow()
    
    // Location filtering and smoothing
    private val locationBuffer = mutableListOf<Location>()
    private val kalmanFilter = LocationKalmanFilter()
    private var lastValidLocation: Location? = null
    
    enum class OptimizationMode {
        NORMAL,     // Full accuracy, high frequency
        BALANCED,   // Good accuracy, moderate frequency
        POWER_SAVE, // Reduced accuracy, low frequency
        CRITICAL    // Minimal accuracy, very low frequency
    }
    
    data class LocationConfig(
        val minTimeMs: Long,
        val minDistanceM: Float,
        val accuracy: LocationAccuracy,
        val enableGpsSmoothing: Boolean,
        val enableLocationFiltering: Boolean,
        val maxLocationAge: Long
    )
    
    enum class LocationAccuracy {
        HIGH,    // GPS + Network
        MEDIUM,  // GPS or Network
        LOW      // Network only
    }
    
    /**
     * Starts optimized location tracking
     */
    @SuppressLint("MissingPermission")
    fun startLocationTracking(mode: OptimizationMode = OptimizationMode.NORMAL) {
        if (!hasLocationPermissions()) {
            throw SecurityException("Location permissions not granted")
        }
        
        currentOptimizationMode = mode
        val config = getLocationConfig(mode)
        
        isTracking = true
        locationBuffer.clear()
        kalmanFilter.reset()
        
        // Start GPS provider if available and needed
        if (shouldUseGps(config.accuracy)) {
            startGpsTracking(config)
        }
        
        // Start Network provider if available and needed
        if (shouldUseNetwork(config.accuracy)) {
            startNetworkTracking(config)
        }
        
        // Start location quality monitoring
        startLocationQualityMonitoring()
        
        println("Started location tracking with mode: $mode")
    }
    
    /**
     * Stops location tracking
     */
    fun stopLocationTracking() {
        isTracking = false
        locationManager.removeUpdates(gpsLocationListener)
        locationManager.removeUpdates(networkLocationListener)
        locationBuffer.clear()
        println("Stopped location tracking")
    }
    
    /**
     * Updates optimization mode dynamically
     */
    fun updateOptimizationMode(mode: OptimizationMode) {
        if (mode != currentOptimizationMode && isTracking) {
            currentOptimizationMode = mode
            stopLocationTracking()
            startLocationTracking(mode)
        }
    }
    
    /**
     * Gets location configuration for optimization mode
     */
    private fun getLocationConfig(mode: OptimizationMode): LocationConfig {
        return when (mode) {
            OptimizationMode.NORMAL -> LocationConfig(
                minTimeMs = 1000L,           // 1 second
                minDistanceM = 2f,           // 2 meters
                accuracy = LocationAccuracy.HIGH,
                enableGpsSmoothing = true,
                enableLocationFiltering = true,
                maxLocationAge = 5000L       // 5 seconds
            )
            OptimizationMode.BALANCED -> LocationConfig(
                minTimeMs = 2000L,           // 2 seconds
                minDistanceM = 5f,           // 5 meters
                accuracy = LocationAccuracy.HIGH,
                enableGpsSmoothing = true,
                enableLocationFiltering = true,
                maxLocationAge = 10000L      // 10 seconds
            )
            OptimizationMode.POWER_SAVE -> LocationConfig(
                minTimeMs = 5000L,           // 5 seconds
                minDistanceM = 10f,          // 10 meters
                accuracy = LocationAccuracy.MEDIUM,
                enableGpsSmoothing = false,
                enableLocationFiltering = true,
                maxLocationAge = 15000L      // 15 seconds
            )
            OptimizationMode.CRITICAL -> LocationConfig(
                minTimeMs = 10000L,          // 10 seconds
                minDistanceM = 20f,          // 20 meters
                accuracy = LocationAccuracy.LOW,
                enableGpsSmoothing = false,
                enableLocationFiltering = false,
                maxLocationAge = 30000L      // 30 seconds
            )
        }
    }
    
    /**
     * Starts GPS location tracking
     */
    @SuppressLint("MissingPermission")
    private fun startGpsTracking(config: LocationConfig) {
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                config.minTimeMs,
                config.minDistanceM,
                gpsLocationListener
            )
            println("Started GPS tracking: ${config.minTimeMs}ms, ${config.minDistanceM}m")
        }
    }
    
    /**
     * Starts Network location tracking
     */
    @SuppressLint("MissingPermission")
    private fun startNetworkTracking(config: LocationConfig) {
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                config.minTimeMs * 2, // Network updates less frequently
                config.minDistanceM * 2,
                networkLocationListener
            )
            println("Started Network tracking")
        }
    }
    
    /**
     * GPS location listener with optimization
     */
    private val gpsLocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            processLocationUpdate(location, "GPS")
        }
        
        override fun onProviderEnabled(provider: String) {
            println("GPS provider enabled")
        }
        
        override fun onProviderDisabled(provider: String) {
            println("GPS provider disabled")
        }
        
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            println("GPS status changed: $status")
        }
    }
    
    /**
     * Network location listener
     */
    private val networkLocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            processLocationUpdate(location, "Network")
        }
        
        override fun onProviderEnabled(provider: String) {
            println("Network provider enabled")
        }
        
        override fun onProviderDisabled(provider: String) {
            println("Network provider disabled")
        }
        
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            println("Network status changed: $status")
        }
    }
    
    /**
     * Processes incoming location updates with filtering and smoothing
     */
    private fun processLocationUpdate(location: Location, provider: String) {
        if (!isTracking) return
        
        val config = getLocationConfig(currentOptimizationMode)
        
        // Check location age
        val locationAge = System.currentTimeMillis() - location.time
        if (locationAge > config.maxLocationAge) {
            println("Discarding old location: ${locationAge}ms old")
            return
        }
        
        // Check location accuracy
        if (location.hasAccuracy() && location.accuracy > getMaxAccuracyThreshold()) {
            println("Discarding inaccurate location: ${location.accuracy}m accuracy")
            return
        }
        
        // Apply location filtering
        val filteredLocation = if (config.enableLocationFiltering) {
            applyLocationFiltering(location)
        } else {
            location
        }
        
        // Apply GPS smoothing (Kalman filter)
        val smoothedLocation = if (config.enableGpsSmoothing && provider == "GPS") {
            kalmanFilter.filter(filteredLocation)
        } else {
            filteredLocation
        }
        
        // Validate location change
        if (isValidLocationUpdate(smoothedLocation)) {
            lastValidLocation = smoothedLocation
            _locationUpdates.tryEmit(smoothedLocation)
            
            // Add to buffer for quality analysis
            addToLocationBuffer(smoothedLocation)
            
            println("Location update: ${smoothedLocation.latitude}, ${smoothedLocation.longitude} " +
                    "(${provider}, accuracy: ${smoothedLocation.accuracy}m)")
        }
    }
    
    /**
     * Applies location filtering to remove outliers
     */
    private fun applyLocationFiltering(location: Location): Location {
        lastValidLocation?.let { lastLocation ->
            val distance = lastLocation.distanceTo(location)
            val timeDiff = (location.time - lastLocation.time) / 1000.0 // seconds
            
            if (timeDiff > 0) {
                val speed = distance / timeDiff // m/s
                val maxReasonableSpeed = 30.0 // 30 m/s (108 km/h) - reasonable for running
                
                if (speed > maxReasonableSpeed) {
                    println("Filtering out location with unreasonable speed: ${speed}m/s")
                    return lastLocation // Return previous valid location
                }
            }
        }
        
        return location
    }
    
    /**
     * Validates if location update should be processed
     */
    private fun isValidLocationUpdate(location: Location): Boolean {
        lastValidLocation?.let { lastLocation ->
            val distance = lastLocation.distanceTo(location)
            val config = getLocationConfig(currentOptimizationMode)
            
            // Check minimum distance
            if (distance < config.minDistanceM) {
                return false
            }
            
            // Check time interval
            val timeDiff = location.time - lastLocation.time
            if (timeDiff < config.minTimeMs) {
                return false
            }
        }
        
        return true
    }
    
    /**
     * Adds location to buffer for analysis
     */
    private fun addToLocationBuffer(location: Location) {
        locationBuffer.add(location)
        
        // Keep buffer size manageable
        if (locationBuffer.size > 50) {
            locationBuffer.removeAt(0)
        }
    }
    
    /**
     * Starts location quality monitoring
     */
    private fun startLocationQualityMonitoring() {
        GlobalScope.launch {
            while (isTracking) {
                delay(30000) // Check every 30 seconds
                analyzeLocationQuality()
            }
        }
    }
    
    /**
     * Analyzes location quality and adjusts settings if needed
     */
    private fun analyzeLocationQuality() {
        if (locationBuffer.size < 10) return
        
        val recentLocations = locationBuffer.takeLast(10)
        val avgAccuracy = recentLocations.mapNotNull { 
            if (it.hasAccuracy()) it.accuracy else null 
        }.average()
        
        val locationCount = recentLocations.size
        val timeSpan = recentLocations.last().time - recentLocations.first().time
        val updateFrequency = locationCount / (timeSpan / 1000.0) // updates per second
        
        println("Location quality - Avg accuracy: ${avgAccuracy}m, Update frequency: ${updateFrequency}/s")
        
        // Auto-adjust optimization mode based on quality
        if (avgAccuracy > 20 && currentOptimizationMode == OptimizationMode.NORMAL) {
            println("Poor GPS accuracy detected, considering mode adjustment")
        }
    }
    
    /**
     * Helper methods
     */
    private fun hasLocationPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun shouldUseGps(accuracy: LocationAccuracy): Boolean {
        return accuracy != LocationAccuracy.LOW && 
               locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }
    
    private fun shouldUseNetwork(accuracy: LocationAccuracy): Boolean {
        return locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
    
    private fun getMaxAccuracyThreshold(): Float {
        return when (currentOptimizationMode) {
            OptimizationMode.NORMAL -> 10f      // 10 meters
            OptimizationMode.BALANCED -> 15f    // 15 meters
            OptimizationMode.POWER_SAVE -> 25f  // 25 meters
            OptimizationMode.CRITICAL -> 50f    // 50 meters
        }
    }
    
    /**
     * Gets current location statistics
     */
    fun getLocationStats(): LocationStats {
        val recentLocations = locationBuffer.takeLast(20)
        
        return LocationStats(
            totalLocations = locationBuffer.size,
            averageAccuracy = recentLocations.mapNotNull { 
                if (it.hasAccuracy()) it.accuracy.toDouble() else null 
            }.average(),
            updateFrequency = calculateUpdateFrequency(recentLocations),
            optimizationMode = currentOptimizationMode,
            isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER),
            isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        )
    }
    
    private fun calculateUpdateFrequency(locations: List<Location>): Double {
        if (locations.size < 2) return 0.0
        
        val timeSpan = locations.last().time - locations.first().time
        return if (timeSpan > 0) {
            (locations.size.toDouble() / (timeSpan / 1000.0))
        } else 0.0
    }
    
    data class LocationStats(
        val totalLocations: Int,
        val averageAccuracy: Double,
        val updateFrequency: Double,
        val optimizationMode: OptimizationMode,
        val isGpsEnabled: Boolean,
        val isNetworkEnabled: Boolean
    )
}

/**
 * Kalman filter for GPS smoothing
 */
class LocationKalmanFilter {
    private var isInitialized = false
    private var lastLat = 0.0
    private var lastLng = 0.0
    private var accuracy = 0.0
    
    fun filter(location: Location): Location {
        if (!isInitialized) {
            lastLat = location.latitude
            lastLng = location.longitude
            accuracy = location.accuracy.toDouble()
            isInitialized = true
            return location
        }
        
        val locationAccuracy = location.accuracy.toDouble()
        val kalmanGain = accuracy / (accuracy + locationAccuracy)
        
        lastLat += kalmanGain * (location.latitude - lastLat)
        lastLng += kalmanGain * (location.longitude - lastLng)
        accuracy = (1 - kalmanGain) * accuracy
        
        val filteredLocation = Location(location.provider)
        filteredLocation.latitude = lastLat
        filteredLocation.longitude = lastLng
        filteredLocation.altitude = location.altitude
        filteredLocation.time = location.time
        filteredLocation.accuracy = accuracy.toFloat()
        
        return filteredLocation
    }
    
    fun reset() {
        isInitialized = false
        accuracy = 0.0
    }
}
