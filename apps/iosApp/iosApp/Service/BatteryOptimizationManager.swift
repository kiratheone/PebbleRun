import Foundation
import UIKit
import CoreLocation

/**
 * iOS Battery Optimization Manager for PebbleRun
 * Implements TASK-036: iOS battery optimization strategies
 */
class BatteryOptimizationManager: ObservableObject {
    
    @Published var batteryLevel: Float = 1.0
    @Published var batteryState: UIDevice.BatteryState = .unknown
    @Published var isLowPowerModeEnabled: Bool = false
    @Published var currentOptimizationMode: OptimizationMode = .normal
    
    private var batteryLevelObserver: NSObjectProtocol?
    private var batteryStateObserver: NSObjectProtocol?
    private var lowPowerModeObserver: NSObjectProtocol?
    
    enum OptimizationMode {
        case normal
        case balanced
        case powerSaver
        case critical
        
        var description: String {
            switch self {
            case .normal:
                return "Normal - Full tracking accuracy"
            case .balanced:
                return "Balanced - Reduced update frequency"
            case .powerSaver:
                return "Power Saver - Minimal background activity"
            case .critical:
                return "Critical - Essential tracking only"
            }
        }
    }
    
    struct OptimizationSettings {
        let locationUpdateInterval: TimeInterval
        let heartRateUpdateInterval: TimeInterval
        let backgroundTaskFrequency: TimeInterval
        let notificationUpdateInterval: TimeInterval
        let gpsAccuracy: CLLocationAccuracy
        let enableBackgroundLocation: Bool
        let enableBackgroundTasks: Bool
    }
    
    init() {
        setupBatteryMonitoring()
        updateCurrentSettings()
    }
    
    deinit {
        removeBatteryObservers()
    }
    
    /**
     * Sets up battery level and state monitoring
     */
    private func setupBatteryMonitoring() {
        UIDevice.current.isBatteryMonitoringEnabled = true
        
        // Initial battery state
        batteryLevel = UIDevice.current.batteryLevel
        batteryState = UIDevice.current.batteryState
        isLowPowerModeEnabled = ProcessInfo.processInfo.isLowPowerModeEnabled
        
        // Battery level observer
        batteryLevelObserver = NotificationCenter.default.addObserver(
            forName: UIDevice.batteryLevelDidChangeNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            self?.updateBatteryLevel()
        }
        
        // Battery state observer
        batteryStateObserver = NotificationCenter.default.addObserver(
            forName: UIDevice.batteryStateDidChangeNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            self?.updateBatteryState()
        }
        
        // Low power mode observer
        lowPowerModeObserver = NotificationCenter.default.addObserver(
            forName: .NSProcessInfoPowerStateDidChange,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            self?.updateLowPowerMode()
        }
    }
    
    /**
     * Removes battery monitoring observers
     */
    private func removeBatteryObservers() {
        if let observer = batteryLevelObserver {
            NotificationCenter.default.removeObserver(observer)
        }
        if let observer = batteryStateObserver {
            NotificationCenter.default.removeObserver(observer)
        }
        if let observer = lowPowerModeObserver {
            NotificationCenter.default.removeObserver(observer)
        }
    }
    
    /**
     * Updates battery level and adjusts optimization mode
     */
    private func updateBatteryLevel() {
        batteryLevel = UIDevice.current.batteryLevel
        updateOptimizationMode()
    }
    
    /**
     * Updates battery state
     */
    private func updateBatteryState() {
        batteryState = UIDevice.current.batteryState
        updateOptimizationMode()
    }
    
    /**
     * Updates low power mode status
     */
    private func updateLowPowerMode() {
        isLowPowerModeEnabled = ProcessInfo.processInfo.isLowPowerModeEnabled
        updateOptimizationMode()
    }
    
    /**
     * Updates current optimization mode based on battery status
     */
    private func updateOptimizationMode() {
        let newMode = determineOptimizationMode()
        if newMode != currentOptimizationMode {
            currentOptimizationMode = newMode
            updateCurrentSettings()
            notifyOptimizationModeChanged(newMode)
        }
    }
    
    /**
     * Determines appropriate optimization mode based on battery status
     */
    private func determineOptimizationMode() -> OptimizationMode {
        let batteryPercent = Int(batteryLevel * 100)
        
        // Critical battery level
        if batteryPercent <= 5 {
            return .critical
        }
        
        // Low power mode is enabled
        if isLowPowerModeEnabled {
            return .powerSaver
        }
        
        // Low battery level
        if batteryPercent <= 15 {
            return .powerSaver
        }
        
        // Moderate battery level
        if batteryPercent <= 30 {
            return .balanced
        }
        
        // Good battery level
        return .normal
    }
    
    /**
     * Gets optimization settings for current mode
     */
    func getCurrentOptimizationSettings() -> OptimizationSettings {
        return getOptimizationSettings(for: currentOptimizationMode)
    }
    
    /**
     * Gets optimization settings for specific mode
     */
    func getOptimizationSettings(for mode: OptimizationMode) -> OptimizationSettings {
        switch mode {
        case .normal:
            return OptimizationSettings(
                locationUpdateInterval: 1.0,           // 1 second
                heartRateUpdateInterval: 1.0,          // 1 second
                backgroundTaskFrequency: 30.0,         // 30 seconds
                notificationUpdateInterval: 5.0,       // 5 seconds
                gpsAccuracy: kCLLocationAccuracyBest,
                enableBackgroundLocation: true,
                enableBackgroundTasks: true
            )
            
        case .balanced:
            return OptimizationSettings(
                locationUpdateInterval: 2.0,           // 2 seconds
                heartRateUpdateInterval: 2.0,          // 2 seconds
                backgroundTaskFrequency: 60.0,         // 1 minute
                notificationUpdateInterval: 10.0,      // 10 seconds
                gpsAccuracy: kCLLocationAccuracyNearestTenMeters,
                enableBackgroundLocation: true,
                enableBackgroundTasks: true
            )
            
        case .powerSaver:
            return OptimizationSettings(
                locationUpdateInterval: 5.0,           // 5 seconds
                heartRateUpdateInterval: 5.0,          // 5 seconds
                backgroundTaskFrequency: 120.0,        // 2 minutes
                notificationUpdateInterval: 30.0,      // 30 seconds
                gpsAccuracy: kCLLocationAccuracyHundredMeters,
                enableBackgroundLocation: true,
                enableBackgroundTasks: false
            )
            
        case .critical:
            return OptimizationSettings(
                locationUpdateInterval: 10.0,          // 10 seconds
                heartRateUpdateInterval: 10.0,         // 10 seconds
                backgroundTaskFrequency: 300.0,        // 5 minutes
                notificationUpdateInterval: 60.0,      // 1 minute
                gpsAccuracy: kCLLocationAccuracyKilometer,
                enableBackgroundLocation: false,
                enableBackgroundTasks: false
            )
        }
    }
    
    /**
     * Gets battery optimization recommendations
     */
    func getBatteryOptimizationRecommendations() -> [BatteryRecommendation] {
        var recommendations: [BatteryRecommendation] = []
        
        // Battery level recommendations
        let batteryPercent = Int(batteryLevel * 100)
        if batteryPercent <= 20 {
            recommendations.append(
                BatteryRecommendation(
                    title: "Low Battery",
                    description: "Consider enabling Low Power Mode or connecting to a charger",
                    priority: .high,
                    action: .enableLowPowerMode
                )
            )
        }
        
        // Low power mode recommendation
        if !isLowPowerModeEnabled && batteryPercent <= 30 {
            recommendations.append(
                BatteryRecommendation(
                    title: "Enable Low Power Mode",
                    description: "Reduce background activity to extend battery life during workouts",
                    priority: .medium,
                    action: .enableLowPowerMode
                )
            )
        }
        
        // Background app refresh recommendation
        if UIApplication.shared.backgroundRefreshStatus != .available {
            recommendations.append(
                BatteryRecommendation(
                    title: "Background App Refresh",
                    description: "Enable Background App Refresh for continuous workout tracking",
                    priority: .medium,
                    action: .enableBackgroundRefresh
                )
            )
        }
        
        // Location services recommendation
        recommendations.append(
            BatteryRecommendation(
                title: "Location Services Optimization",
                description: "PebbleRun uses GPS efficiently. Consider disabling location for other apps",
                priority: .low,
                action: .optimizeLocationServices
            )
        )
        
        return recommendations
    }
    
    /**
     * Gets battery usage statistics
     */
    func getBatteryUsageStats() -> BatteryUsageStats {
        return BatteryUsageStats(
            currentLevel: batteryLevel,
            batteryState: batteryState,
            isLowPowerModeEnabled: isLowPowerModeEnabled,
            estimatedUsagePerHour: getEstimatedUsagePerHour(),
            estimatedWorkoutTime: getEstimatedWorkoutTime(),
            optimizationMode: currentOptimizationMode
        )
    }
    
    /**
     * Estimates battery usage per hour based on current mode
     */
    private func getEstimatedUsagePerHour() -> Float {
        switch currentOptimizationMode {
        case .normal:
            return 0.25      // 25% per hour
        case .balanced:
            return 0.15      // 15% per hour
        case .powerSaver:
            return 0.08      // 8% per hour
        case .critical:
            return 0.05      // 5% per hour
        }
    }
    
    /**
     * Estimates remaining workout time based on current battery
     */
    private func getEstimatedWorkoutTime() -> TimeInterval {
        let usagePerHour = getEstimatedUsagePerHour()
        let remainingBattery = batteryLevel
        
        if usagePerHour > 0 {
            return TimeInterval((remainingBattery / usagePerHour) * 3600) // Convert to seconds
        }
        
        return 0
    }
    
    /**
     * Updates current settings based on optimization mode
     */
    private func updateCurrentSettings() {
        let settings = getCurrentOptimizationSettings()
        print("Battery optimization mode changed to: \(currentOptimizationMode.description)")
        print("New settings: Location interval: \(settings.locationUpdateInterval)s, GPS accuracy: \(settings.gpsAccuracy)")
    }
    
    /**
     * Notifies about optimization mode changes
     */
    private func notifyOptimizationModeChanged(_ mode: OptimizationMode) {
        NotificationCenter.default.post(
            name: .batteryOptimizationModeChanged,
            object: nil,
            userInfo: ["mode": mode]
        )
    }
    
    /**
     * Forces a specific optimization mode (manual override)
     */
    func setOptimizationMode(_ mode: OptimizationMode) {
        currentOptimizationMode = mode
        updateCurrentSettings()
        notifyOptimizationModeChanged(mode)
    }
    
    /**
     * Resets to automatic optimization mode
     */
    func resetToAutomaticMode() {
        updateOptimizationMode()
    }
}

// MARK: - Supporting Types

struct BatteryRecommendation {
    let title: String
    let description: String
    let priority: Priority
    let action: RecommendedAction
    
    enum Priority {
        case low, medium, high, critical
    }
    
    enum RecommendedAction {
        case enableLowPowerMode
        case enableBackgroundRefresh
        case optimizeLocationServices
        case reduceScreenBrightness
        case closeOtherApps
    }
}

struct BatteryUsageStats {
    let currentLevel: Float
    let batteryState: UIDevice.BatteryState
    let isLowPowerModeEnabled: Bool
    let estimatedUsagePerHour: Float
    let estimatedWorkoutTime: TimeInterval
    let optimizationMode: BatteryOptimizationManager.OptimizationMode
    
    var batteryPercentage: Int {
        return Int(currentLevel * 100)
    }
    
    var estimatedWorkoutHours: Double {
        return estimatedWorkoutTime / 3600.0
    }
}

// MARK: - Notification Extensions

extension Notification.Name {
    static let batteryOptimizationModeChanged = Notification.Name("batteryOptimizationModeChanged")
}
