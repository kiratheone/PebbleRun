import Foundation
import CoreLocation
import Combine

/**
 * iOS-specific location service optimizations for PebbleRun.
 * Implements TASK-037: Platform-specific location service optimizations.
 */
class OptimizedLocationService: NSObject, ObservableObject {
    
    private let locationManager = CLLocationManager()
    private var cancellables = Set<AnyCancellable>()
    
    // Location tracking state
    @Published var isTracking = false
    @Published var currentOptimizationMode: OptimizationMode = .normal
    @Published var locationAuthorizationStatus: CLAuthorizationStatus = .notDetermined
    
    // Location data
    private let locationSubject = PassthroughSubject<CLLocation, Never>()
    var locationUpdates: AnyPublisher<CLLocation, Never> {
        locationSubject.eraseToAnyPublisher()
    }
    
    // Location filtering and analysis
    private var locationBuffer: [CLLocation] = []
    private var kalmanFilter = LocationKalmanFilter()
    private var lastValidLocation: CLLocation?
    
    enum OptimizationMode: CaseIterable {
        case normal     // Full accuracy, high frequency
        case balanced   // Good accuracy, moderate frequency
        case powerSave  // Reduced accuracy, low frequency
        case critical   // Minimal accuracy, very low frequency
        
        var description: String {
            switch self {
            case .normal: return "Normal - Full accuracy"
            case .balanced: return "Balanced - Good accuracy, power efficient"
            case .powerSave: return "Power Save - Reduced accuracy"
            case .critical: return "Critical - Minimal tracking"
            }
        }
    }
    
    struct LocationConfig {
        let desiredAccuracy: CLLocationAccuracy
        let distanceFilter: CLLocationDistance
        let enableGpsSmoothing: Bool
        let enableLocationFiltering: Bool
        let maxLocationAge: TimeInterval
        let allowsBackgroundUpdates: Bool
        let pausesLocationUpdatesAutomatically: Bool
    }
    
    override init() {
        super.init()
        setupLocationManager()
    }
    
    /**
     * Sets up location manager with initial configuration
     */
    private func setupLocationManager() {
        locationManager.delegate = self
        locationManager.requestAlwaysAuthorization()
        
        // Monitor authorization status changes
        locationManager.publisher(for: \.authorizationStatus)
            .assign(to: \.locationAuthorizationStatus, on: self)
            .store(in: &cancellables)
    }
    
    /**
     * Starts optimized location tracking
     */
    func startLocationTracking(mode: OptimizationMode = .normal) {
        guard locationAuthorizationStatus == .authorizedAlways || 
              locationAuthorizationStatus == .authorizedWhenInUse else {
            print("Location authorization not granted")
            return
        }
        
        currentOptimizationMode = mode
        let config = getLocationConfig(for: mode)
        
        // Configure location manager
        configureLocationManager(with: config)
        
        // Reset state
        locationBuffer.removeAll()
        kalmanFilter.reset()
        lastValidLocation = nil
        
        // Start location updates
        locationManager.startUpdatingLocation()
        
        // Start significant location changes for background
        if config.allowsBackgroundUpdates {
            locationManager.startMonitoringSignificantLocationChanges()
        }
        
        isTracking = true
        print("Started location tracking with mode: \(mode.description)")
    }
    
    /**
     * Stops location tracking
     */
    func stopLocationTracking() {
        locationManager.stopUpdatingLocation()
        locationManager.stopMonitoringSignificantLocationChanges()
        
        isTracking = false
        locationBuffer.removeAll()
        print("Stopped location tracking")
    }
    
    /**
     * Updates optimization mode dynamically
     */
    func updateOptimizationMode(_ mode: OptimizationMode) {
        if mode != currentOptimizationMode && isTracking {
            currentOptimizationMode = mode
            let config = getLocationConfig(for: mode)
            configureLocationManager(with: config)
            print("Updated location tracking to mode: \(mode.description)")
        }
    }
    
    /**
     * Gets location configuration for optimization mode
     */
    private func getLocationConfig(for mode: OptimizationMode) -> LocationConfig {
        switch mode {
        case .normal:
            return LocationConfig(
                desiredAccuracy: kCLLocationAccuracyBest,
                distanceFilter: 2.0,                    // 2 meters
                enableGpsSmoothing: true,
                enableLocationFiltering: true,
                maxLocationAge: 5.0,                     // 5 seconds
                allowsBackgroundUpdates: true,
                pausesLocationUpdatesAutomatically: false
            )
            
        case .balanced:
            return LocationConfig(
                desiredAccuracy: kCLLocationAccuracyNearestTenMeters,
                distanceFilter: 5.0,                    // 5 meters
                enableGpsSmoothing: true,
                enableLocationFiltering: true,
                maxLocationAge: 10.0,                    // 10 seconds
                allowsBackgroundUpdates: true,
                pausesLocationUpdatesAutomatically: false
            )
            
        case .powerSave:
            return LocationConfig(
                desiredAccuracy: kCLLocationAccuracyHundredMeters,
                distanceFilter: 10.0,                   // 10 meters
                enableGpsSmoothing: false,
                enableLocationFiltering: true,
                maxLocationAge: 15.0,                    // 15 seconds
                allowsBackgroundUpdates: true,
                pausesLocationUpdatesAutomatically: true
            )
            
        case .critical:
            return LocationConfig(
                desiredAccuracy: kCLLocationAccuracyKilometer,
                distanceFilter: 20.0,                   // 20 meters
                enableGpsSmoothing: false,
                enableLocationFiltering: false,
                maxLocationAge: 30.0,                    // 30 seconds
                allowsBackgroundUpdates: false,
                pausesLocationUpdatesAutomatically: true
            )
        }
    }
    
    /**
     * Configures location manager with optimization settings
     */
    private func configureLocationManager(with config: LocationConfig) {
        locationManager.desiredAccuracy = config.desiredAccuracy
        locationManager.distanceFilter = config.distanceFilter
        locationManager.allowsBackgroundLocationUpdates = config.allowsBackgroundUpdates
        locationManager.pausesLocationUpdatesAutomatically = config.pausesLocationUpdatesAutomatically
        
        print("Configured location manager - Accuracy: \(config.desiredAccuracy), Distance filter: \(config.distanceFilter)m")
    }
    
    /**
     * Processes incoming location updates with filtering and smoothing
     */
    private func processLocationUpdate(_ location: CLLocation) {
        let config = getLocationConfig(for: currentOptimizationMode)
        
        // Check location age
        let locationAge = abs(location.timestamp.timeIntervalSinceNow)
        if locationAge > config.maxLocationAge {
            print("Discarding old location: \(locationAge)s old")
            return
        }
        
        // Check location accuracy
        if location.horizontalAccuracy < 0 || location.horizontalAccuracy > getMaxAccuracyThreshold() {
            print("Discarding inaccurate location: \(location.horizontalAccuracy)m accuracy")
            return
        }
        
        // Apply location filtering
        let filteredLocation = config.enableLocationFiltering ? 
            applyLocationFiltering(location) : location
        
        // Apply GPS smoothing (Kalman filter)
        let smoothedLocation = config.enableGpsSmoothing ? 
            kalmanFilter.filter(filteredLocation) : filteredLocation
        
        // Validate location update
        if isValidLocationUpdate(smoothedLocation) {
            lastValidLocation = smoothedLocation
            locationSubject.send(smoothedLocation)
            
            // Add to buffer for analysis
            addToLocationBuffer(smoothedLocation)
            
            print("Location update: \(smoothedLocation.coordinate.latitude), \(smoothedLocation.coordinate.longitude) " +
                  "(accuracy: \(smoothedLocation.horizontalAccuracy)m)")
        }
    }
    
    /**
     * Applies location filtering to remove outliers
     */
    private func applyLocationFiltering(_ location: CLLocation) -> CLLocation {
        guard let lastLocation = lastValidLocation else {
            return location
        }
        
        let distance = lastLocation.distance(from: location)
        let timeDiff = location.timestamp.timeIntervalSince(lastLocation.timestamp)
        
        if timeDiff > 0 {
            let speed = distance / timeDiff // m/s
            let maxReasonableSpeed = 30.0 // 30 m/s (108 km/h)
            
            if speed > maxReasonableSpeed {
                print("Filtering out location with unreasonable speed: \(speed)m/s")
                return lastLocation // Return previous valid location
            }
        }
        
        return location
    }
    
    /**
     * Validates if location update should be processed
     */
    private func isValidLocationUpdate(_ location: CLLocation) -> Bool {
        guard let lastLocation = lastValidLocation else {
            return true
        }
        
        let distance = lastLocation.distance(from: location)
        let config = getLocationConfig(for: currentOptimizationMode)
        
        // Check minimum distance
        if distance < config.distanceFilter {
            return false
        }
        
        return true
    }
    
    /**
     * Adds location to buffer for analysis
     */
    private func addToLocationBuffer(_ location: CLLocation) {
        locationBuffer.append(location)
        
        // Keep buffer size manageable
        if locationBuffer.count > 50 {
            locationBuffer.removeFirst()
        }
        
        // Analyze location quality periodically
        if locationBuffer.count % 10 == 0 {
            analyzeLocationQuality()
        }
    }
    
    /**
     * Analyzes location quality and provides insights
     */
    private func analyzeLocationQuality() {
        guard locationBuffer.count >= 10 else { return }
        
        let recentLocations = Array(locationBuffer.suffix(10))
        let avgAccuracy = recentLocations.map { $0.horizontalAccuracy }.reduce(0, +) / Double(recentLocations.count)
        
        let timeSpan = recentLocations.last!.timestamp.timeIntervalSince(recentLocations.first!.timestamp)
        let updateFrequency = Double(recentLocations.count) / timeSpan // updates per second
        
        print("Location quality - Avg accuracy: \(avgAccuracy)m, Update frequency: \(updateFrequency)/s")
        
        // Auto-adjust optimization mode based on quality
        if avgAccuracy > 20 && currentOptimizationMode == .normal {
            print("Poor GPS accuracy detected, consider adjusting optimization mode")
        }
    }
    
    /**
     * Helper methods
     */
    private func getMaxAccuracyThreshold() -> CLLocationAccuracy {
        switch currentOptimizationMode {
        case .normal: return 10.0      // 10 meters
        case .balanced: return 15.0    // 15 meters
        case .powerSave: return 25.0   // 25 meters
        case .critical: return 50.0    // 50 meters
        }
    }
    
    /**
     * Gets current location statistics
     */
    func getLocationStats() -> LocationStats {
        let recentLocations = Array(locationBuffer.suffix(20))
        
        return LocationStats(
            totalLocations: locationBuffer.count,
            averageAccuracy: recentLocations.isEmpty ? 0 : 
                recentLocations.map { $0.horizontalAccuracy }.reduce(0, +) / Double(recentLocations.count),
            updateFrequency: calculateUpdateFrequency(from: recentLocations),
            optimizationMode: currentOptimizationMode,
            authorizationStatus: locationAuthorizationStatus,
            isLocationServicesEnabled: CLLocationManager.locationServicesEnabled()
        )
    }
    
    private func calculateUpdateFrequency(from locations: [CLLocation]) -> Double {
        guard locations.count >= 2 else { return 0.0 }
        
        let timeSpan = locations.last!.timestamp.timeIntervalSince(locations.first!.timestamp)
        return timeSpan > 0 ? Double(locations.count) / timeSpan : 0.0
    }
    
    /**
     * Requests location permissions
     */
    func requestLocationPermissions() {
        switch locationAuthorizationStatus {
        case .notDetermined:
            locationManager.requestAlwaysAuthorization()
        case .denied, .restricted:
            print("Location permissions denied. Please enable in Settings.")
        case .authorizedWhenInUse:
            locationManager.requestAlwaysAuthorization()
        case .authorizedAlways:
            print("Location permissions already granted")
        @unknown default:
            locationManager.requestAlwaysAuthorization()
        }
    }
    
    struct LocationStats {
        let totalLocations: Int
        let averageAccuracy: CLLocationAccuracy
        let updateFrequency: Double
        let optimizationMode: OptimizationMode
        let authorizationStatus: CLAuthorizationStatus
        let isLocationServicesEnabled: Bool
    }
}

// MARK: - CLLocationManagerDelegate

extension OptimizedLocationService: CLLocationManagerDelegate {
    
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        for location in locations {
            processLocationUpdate(location)
        }
    }
    
    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        print("Location manager failed with error: \(error.localizedDescription)")
        
        if let clError = error as? CLError {
            switch clError.code {
            case .locationUnknown:
                print("Location service was unable to obtain a location fix")
            case .denied:
                print("Location services are disabled or denied")
            case .network:
                print("Network was unavailable or a network error occurred")
            default:
                print("Other location error: \(clError.localizedDescription)")
            }
        }
    }
    
    func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus) {
        locationAuthorizationStatus = status
        
        switch status {
        case .notDetermined:
            print("Location authorization not determined")
        case .denied, .restricted:
            print("Location authorization denied or restricted")
            stopLocationTracking()
        case .authorizedWhenInUse:
            print("Location authorized when in use")
        case .authorizedAlways:
            print("Location authorized always")
        @unknown default:
            print("Unknown location authorization status")
        }
    }
    
    func locationManagerDidPauseLocationUpdates(_ manager: CLLocationManager) {
        print("Location updates paused automatically")
    }
    
    func locationManagerDidResumeLocationUpdates(_ manager: CLLocationManager) {
        print("Location updates resumed automatically")
    }
}

// MARK: - iOS Kalman Filter

class LocationKalmanFilter {
    private var isInitialized = false
    private var lastLat = 0.0
    private var lastLng = 0.0
    private var accuracy = 0.0
    
    func filter(_ location: CLLocation) -> CLLocation {
        if !isInitialized {
            lastLat = location.coordinate.latitude
            lastLng = location.coordinate.longitude
            accuracy = location.horizontalAccuracy
            isInitialized = true
            return location
        }
        
        let locationAccuracy = location.horizontalAccuracy
        let kalmanGain = accuracy / (accuracy + locationAccuracy)
        
        lastLat += kalmanGain * (location.coordinate.latitude - lastLat)
        lastLng += kalmanGain * (location.coordinate.longitude - lastLng)
        accuracy = (1 - kalmanGain) * accuracy
        
        let filteredCoordinate = CLLocationCoordinate2D(latitude: lastLat, longitude: lastLng)
        let filteredLocation = CLLocation(
            coordinate: filteredCoordinate,
            altitude: location.altitude,
            horizontalAccuracy: accuracy,
            verticalAccuracy: location.verticalAccuracy,
            timestamp: location.timestamp
        )
        
        return filteredLocation
    }
    
    func reset() {
        isInitialized = false
        accuracy = 0.0
    }
}
