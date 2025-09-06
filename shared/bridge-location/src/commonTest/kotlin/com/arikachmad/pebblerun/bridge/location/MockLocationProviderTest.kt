package com.arikachmad.pebblerun.bridge.location

import com.arikachmad.pebblerun.domain.entity.GeoPoint
import com.arikachmad.pebblerun.util.error.PebbleRunError
import com.arikachmad.pebblerun.util.error.Result
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.*

/**
 * Mock tests for LocationProvider testing with simulated GPS coordinates.
 * Satisfies TEST-009: Location provider testing with simulated GPS coordinates.
 */
class MockLocationProviderTest {
    
    // Mock implementation for testing
    private class MockLocationProvider : LocationProvider {
        private var permissionGranted = false
        private var locationEnabled = false
        private var shouldFailLocationRequests = false
        private val geoPoints = mutableListOf<GeoPoint>()
        private var isTracking = false
        
        fun setPermissionGranted(granted: Boolean) {
            permissionGranted = granted
        }
        
        fun setLocationEnabled(enabled: Boolean) {
            locationEnabled = enabled
        }
        
        fun setLocationRequestFailure(fail: Boolean) {
            shouldFailLocationRequests = fail
        }
        
        fun simulateLocationUpdate(latitude: Double, longitude: Double, accuracy: Float = 5.0f) {
            if (isTracking && permissionGranted && locationEnabled && !shouldFailLocationRequests) {
                val geoPoint = GeoPoint(
                    latitude = latitude,
                    longitude = longitude,
                    accuracy = accuracy,
                    timestamp = Clock.System.now()
                )
                geoPoints.add(geoPoint)
            }
        }
        
        fun getRecordedLocations(): List<GeoPoint> = geoPoints.toList()
        fun clearLocations() = geoPoints.clear()
        
        override suspend fun requestLocationPermission(): Result<Boolean> {
            return Result.Success(permissionGranted)
        }
        
        override fun isLocationPermissionGranted(): Boolean = permissionGranted
        
        override fun isLocationEnabled(): Boolean = locationEnabled
        
        override suspend fun getCurrentLocation(): Result<GeoPoint> {
            return when {
                !permissionGranted -> Result.Error(PebbleRunError.LocationPermissionError())
                !locationEnabled -> Result.Error(PebbleRunError.LocationServiceError("Location services disabled"))
                shouldFailLocationRequests -> Result.Error(PebbleRunError.LocationServiceError("Mock location request failed"))
                else -> {
                    val location = GeoPoint(
                        latitude = 40.7128, // Default NYC coordinates
                        longitude = -74.0060,
                        accuracy = 5.0f,
                        timestamp = Clock.System.now()
                    )
                    Result.Success(location)
                }
            }
        }
        
        override suspend fun startLocationTracking(): Result<Boolean> {
            return when {
                !permissionGranted -> Result.Error(PebbleRunError.LocationPermissionError())
                !locationEnabled -> Result.Error(PebbleRunError.LocationServiceError("Location services disabled"))
                shouldFailLocationRequests -> Result.Error(PebbleRunError.LocationServiceError("Failed to start tracking"))
                else -> {
                    isTracking = true
                    Result.Success(true)
                }
            }
        }
        
        override suspend fun stopLocationTracking(): Result<Boolean> {
            isTracking = false
            return Result.Success(true)
        }
        
        override fun isTracking(): Boolean = isTracking
        
        override fun observeLocation() = flowOf(*geoPoints.toTypedArray())
        
        override suspend fun getLocationAccuracy(): Result<Float> {
            return if (permissionGranted && locationEnabled) {
                Result.Success(5.0f) // Mock accuracy
            } else {
                Result.Error(PebbleRunError.LocationServiceError("Cannot get accuracy"))
            }
        }
    }
    
    private lateinit var locationProvider: MockLocationProvider
    
    @BeforeTest
    fun setup() {
        locationProvider = MockLocationProvider()
    }
    
    @AfterTest
    fun cleanup() {
        locationProvider.clearLocations()
    }
    
    @Test
    fun `requestLocationPermission returns permission status`() = runTest {
        // Test permission denied
        locationProvider.setPermissionGranted(false)
        val deniedResult = locationProvider.requestLocationPermission()
        assertTrue(deniedResult.isSuccess(), "Permission request should succeed")
        assertFalse(deniedResult.getOrNull() ?: true, "Permission should be denied")
        
        // Test permission granted
        locationProvider.setPermissionGranted(true)
        val grantedResult = locationProvider.requestLocationPermission()
        assertTrue(grantedResult.isSuccess(), "Permission request should succeed")
        assertTrue(grantedResult.getOrNull() ?: false, "Permission should be granted")
    }
    
    @Test
    fun `isLocationPermissionGranted reflects current permission state`() {
        locationProvider.setPermissionGranted(false)
        assertFalse(locationProvider.isLocationPermissionGranted(), "Should reflect denied permission")
        
        locationProvider.setPermissionGranted(true)
        assertTrue(locationProvider.isLocationPermissionGranted(), "Should reflect granted permission")
    }
    
    @Test
    fun `isLocationEnabled reflects location service state`() {
        locationProvider.setLocationEnabled(false)
        assertFalse(locationProvider.isLocationEnabled(), "Should reflect disabled location services")
        
        locationProvider.setLocationEnabled(true)
        assertTrue(locationProvider.isLocationEnabled(), "Should reflect enabled location services")
    }
    
    @Test
    fun `getCurrentLocation succeeds with proper conditions`() = runTest {
        locationProvider.setPermissionGranted(true)
        locationProvider.setLocationEnabled(true)
        
        val result = locationProvider.getCurrentLocation()
        
        assertTrue(result.isSuccess(), "Should get current location successfully")
        val location = result.getOrNull()!!
        assertEquals(40.7128, location.latitude, 0.0001, "Should return NYC latitude")
        assertEquals(-74.0060, location.longitude, 0.0001, "Should return NYC longitude")
        assertEquals(5.0f, location.accuracy, "Should return expected accuracy")
    }
    
    @Test
    fun `getCurrentLocation fails without permission`() = runTest {
        locationProvider.setPermissionGranted(false)
        locationProvider.setLocationEnabled(true)
        
        val result = locationProvider.getCurrentLocation()
        
        assertTrue(result.isError(), "Should fail without permission")
        assertTrue(result.exceptionOrNull() is PebbleRunError.LocationPermissionError)
    }
    
    @Test
    fun `getCurrentLocation fails with location services disabled`() = runTest {
        locationProvider.setPermissionGranted(true)
        locationProvider.setLocationEnabled(false)
        
        val result = locationProvider.getCurrentLocation()
        
        assertTrue(result.isError(), "Should fail with location services disabled")
        assertTrue(result.exceptionOrNull() is PebbleRunError.LocationServiceError)
    }
    
    @Test
    fun `getCurrentLocation fails when simulating request failure`() = runTest {
        locationProvider.setPermissionGranted(true)
        locationProvider.setLocationEnabled(true)
        locationProvider.setLocationRequestFailure(true)
        
        val result = locationProvider.getCurrentLocation()
        
        assertTrue(result.isError(), "Should fail when request failure simulated")
        assertTrue(result.exceptionOrNull() is PebbleRunError.LocationServiceError)
    }
    
    @Test
    fun `startLocationTracking succeeds with proper conditions`() = runTest {
        locationProvider.setPermissionGranted(true)
        locationProvider.setLocationEnabled(true)
        
        val result = locationProvider.startLocationTracking()
        
        assertTrue(result.isSuccess(), "Should start tracking successfully")
        assertTrue(locationProvider.isTracking(), "Should be tracking after start")
    }
    
    @Test
    fun `startLocationTracking fails without permission`() = runTest {
        locationProvider.setPermissionGranted(false)
        locationProvider.setLocationEnabled(true)
        
        val result = locationProvider.startLocationTracking()
        
        assertTrue(result.isError(), "Should fail without permission")
        assertFalse(locationProvider.isTracking(), "Should not be tracking")
        assertTrue(result.exceptionOrNull() is PebbleRunError.LocationPermissionError)
    }
    
    @Test
    fun `startLocationTracking fails with location services disabled`() = runTest {
        locationProvider.setPermissionGranted(true)
        locationProvider.setLocationEnabled(false)
        
        val result = locationProvider.startLocationTracking()
        
        assertTrue(result.isError(), "Should fail with location services disabled")
        assertFalse(locationProvider.isTracking(), "Should not be tracking")
        assertTrue(result.exceptionOrNull() is PebbleRunError.LocationServiceError)
    }
    
    @Test
    fun `stopLocationTracking always succeeds`() = runTest {
        // Start tracking first
        locationProvider.setPermissionGranted(true)
        locationProvider.setLocationEnabled(true)
        locationProvider.startLocationTracking()
        assertTrue(locationProvider.isTracking(), "Should be tracking")
        
        // Stop tracking
        val result = locationProvider.stopLocationTracking()
        
        assertTrue(result.isSuccess(), "Should stop tracking successfully")
        assertFalse(locationProvider.isTracking(), "Should not be tracking after stop")
    }
    
    @Test
    fun `stopLocationTracking works even when not tracking`() = runTest {
        assertFalse(locationProvider.isTracking(), "Should not be tracking initially")
        
        val result = locationProvider.stopLocationTracking()
        
        assertTrue(result.isSuccess(), "Should succeed even when not tracking")
        assertFalse(locationProvider.isTracking(), "Should still not be tracking")
    }
    
    @Test
    fun `location simulation works when tracking`() = runTest {
        locationProvider.setPermissionGranted(true)
        locationProvider.setLocationEnabled(true)
        locationProvider.startLocationTracking()
        
        // Simulate location updates
        locationProvider.simulateLocationUpdate(40.7128, -74.0060, 5.0f)
        locationProvider.simulateLocationUpdate(40.7138, -74.0061, 4.0f)
        locationProvider.simulateLocationUpdate(40.7148, -74.0062, 6.0f)
        
        val locations = locationProvider.getRecordedLocations()
        
        assertEquals(3, locations.size, "Should have recorded 3 locations")
        assertEquals(40.7128, locations[0].latitude, 0.0001)
        assertEquals(40.7138, locations[1].latitude, 0.0001)
        assertEquals(40.7148, locations[2].latitude, 0.0001)
        assertEquals(5.0f, locations[0].accuracy)
        assertEquals(4.0f, locations[1].accuracy)
        assertEquals(6.0f, locations[2].accuracy)
    }
    
    @Test
    fun `location simulation doesn't work when not tracking`() = runTest {
        locationProvider.setPermissionGranted(true)
        locationProvider.setLocationEnabled(true)
        // Don't start tracking
        
        locationProvider.simulateLocationUpdate(40.7128, -74.0060)
        
        val locations = locationProvider.getRecordedLocations()
        assertEquals(0, locations.size, "Should not record locations when not tracking")
    }
    
    @Test
    fun `location simulation doesn't work without permission`() = runTest {
        locationProvider.setPermissionGranted(false)
        locationProvider.setLocationEnabled(true)
        locationProvider.startLocationTracking() // This will fail
        
        locationProvider.simulateLocationUpdate(40.7128, -74.0060)
        
        val locations = locationProvider.getRecordedLocations()
        assertEquals(0, locations.size, "Should not record locations without permission")
    }
    
    @Test
    fun `getLocationAccuracy returns expected values`() = runTest {
        // Test with proper conditions
        locationProvider.setPermissionGranted(true)
        locationProvider.setLocationEnabled(true)
        
        val result = locationProvider.getLocationAccuracy()
        
        assertTrue(result.isSuccess(), "Should get accuracy successfully")
        assertEquals(5.0f, result.getOrNull(), "Should return expected accuracy")
        
        // Test without permission
        locationProvider.setPermissionGranted(false)
        val failResult = locationProvider.getLocationAccuracy()
        
        assertTrue(failResult.isError(), "Should fail without permission")
        assertTrue(failResult.exceptionOrNull() is PebbleRunError.LocationServiceError)
    }
    
    @Test
    fun `simulated workout tracking session works end-to-end`() = runTest {
        // 1. Request and grant permission
        locationProvider.setPermissionGranted(true)
        val permissionResult = locationProvider.requestLocationPermission()
        assertTrue(permissionResult.isSuccess() && permissionResult.getOrNull() == true)
        
        // 2. Enable location services
        locationProvider.setLocationEnabled(true)
        assertTrue(locationProvider.isLocationEnabled())
        
        // 3. Get initial location
        val initialLocationResult = locationProvider.getCurrentLocation()
        assertTrue(initialLocationResult.isSuccess(), "Should get initial location")
        
        // 4. Start location tracking
        val startResult = locationProvider.startLocationTracking()
        assertTrue(startResult.isSuccess(), "Should start tracking")
        assertTrue(locationProvider.isTracking(), "Should be tracking")
        
        // 5. Simulate a workout route (running around Central Park)
        val workoutRoute = listOf(
            Triple(40.7829, -73.9654, 3.0f), // Central Park South
            Triple(40.7849, -73.9654, 4.0f), // Moving north
            Triple(40.7869, -73.9654, 3.5f), // Continue north
            Triple(40.7889, -73.9654, 5.0f), // North end
            Triple(40.7889, -73.9634, 4.5f), // Turn east
            Triple(40.7889, -73.9614, 3.8f), // Continue east
            Triple(40.7869, -73.9614, 4.2f), // Turn south
            Triple(40.7849, -73.9614, 3.9f), // Continue south
            Triple(40.7829, -73.9614, 4.1f), // Back to start area
            Triple(40.7829, -73.9634, 3.7f)  // Complete the loop
        )
        
        workoutRoute.forEach { (lat, lon, accuracy) ->
            locationProvider.simulateLocationUpdate(lat, lon, accuracy)
        }
        
        // 6. Verify tracking data
        val recordedLocations = locationProvider.getRecordedLocations()
        assertEquals(10, recordedLocations.size, "Should have recorded all route points")
        
        // Verify first and last points
        assertEquals(40.7829, recordedLocations.first().latitude, 0.0001)
        assertEquals(-73.9654, recordedLocations.first().longitude, 0.0001)
        assertEquals(40.7829, recordedLocations.last().latitude, 0.0001)
        assertEquals(-73.9634, recordedLocations.last().longitude, 0.0001)
        
        // Verify timestamps are in order
        val timestamps = recordedLocations.map { it.timestamp.epochSeconds }
        assertTrue(timestamps == timestamps.sorted(), "Timestamps should be in order")
        
        // Verify accuracy values
        val accuracies = recordedLocations.map { it.accuracy }
        assertTrue(accuracies.all { it in 3.0f..5.0f }, "All accuracies should be in expected range")
        
        // 7. Stop tracking
        val stopResult = locationProvider.stopLocationTracking()
        assertTrue(stopResult.isSuccess(), "Should stop tracking")
        assertFalse(locationProvider.isTracking(), "Should not be tracking after stop")
        
        // 8. Verify no more locations are recorded after stopping
        val initialCount = recordedLocations.size
        locationProvider.simulateLocationUpdate(40.7829, -73.9654)
        val finalLocations = locationProvider.getRecordedLocations()
        assertEquals(initialCount, finalLocations.size, "Should not record after stopping")
    }
    
    @Test
    fun `location accuracy varies realistically during tracking`() = runTest {
        locationProvider.setPermissionGranted(true)
        locationProvider.setLocationEnabled(true)
        locationProvider.startLocationTracking()
        
        // Simulate varying GPS accuracy (as would happen in real conditions)
        val accuracyValues = listOf(3.0f, 5.0f, 8.0f, 4.0f, 12.0f, 2.0f, 15.0f, 6.0f)
        
        accuracyValues.forEachIndexed { index, accuracy ->
            locationProvider.simulateLocationUpdate(
                latitude = 40.7128 + (index * 0.001),
                longitude = -74.0060 + (index * 0.001),
                accuracy = accuracy
            )
        }
        
        val locations = locationProvider.getRecordedLocations()
        assertEquals(8, locations.size, "Should have all location points")
        
        // Verify accuracy values match
        locations.forEachIndexed { index, location ->
            assertEquals(accuracyValues[index], location.accuracy, "Accuracy $index should match")
        }
        
        // Verify good accuracy points (< 10m)
        val goodAccuracyCount = locations.count { it.accuracy < 10.0f }
        val poorAccuracyCount = locations.count { it.accuracy >= 10.0f }
        
        assertEquals(6, goodAccuracyCount, "Should have 6 good accuracy readings")
        assertEquals(2, poorAccuracyCount, "Should have 2 poor accuracy readings")
    }
}
