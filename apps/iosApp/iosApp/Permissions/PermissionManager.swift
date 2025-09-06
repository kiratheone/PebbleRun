import Foundation
import CoreLocation
import CoreBluetooth
import UserNotifications

/**
 * iOS-specific permission manager for handling Location, Bluetooth, and Notification permissions.
 * Implements TASK-017: Add iOS-specific permission handling (Location, Bluetooth).
 * Follows GUD-003: Platform-specific navigation patterns and user flows.
 */
@MainActor
class PermissionManager: NSObject, ObservableObject {
    
    // MARK: - Published Properties
    @Published var locationPermissionStatus: CLAuthorizationStatus = .notDetermined
    @Published var bluetoothPermissionStatus: CBManagerState = .unknown
    @Published var notificationPermissionStatus: UNAuthorizationStatus = .notDetermined
    
    // MARK: - Computed Properties
    var hasLocationPermission: Bool {
        switch locationPermissionStatus {
        case .authorizedAlways, .authorizedWhenInUse:
            return true
        default:
            return false
        }
    }
    
    var hasBluetoothPermission: Bool {
        return bluetoothPermissionStatus == .poweredOn
    }
    
    var hasNotificationPermission: Bool {
        return notificationPermissionStatus == .authorized
    }
    
    var allPermissionsGranted: Bool {
        return hasLocationPermission && hasBluetoothPermission && hasNotificationPermission
    }
    
    // MARK: - Private Properties
    private var locationManager: CLLocationManager?
    private var bluetoothManager: CBCentralManager?
    private var notificationCenter: UNUserNotificationCenter
    
    // MARK: - Initialization
    override init() {
        self.notificationCenter = UNUserNotificationCenter.current()
        super.init()
        
        setupLocationManager()
        setupBluetoothManager()
        checkCurrentPermissions()
    }
    
    // MARK: - Setup Methods
    private func setupLocationManager() {
        locationManager = CLLocationManager()
        locationManager?.delegate = self
        locationPermissionStatus = locationManager?.authorizationStatus ?? .notDetermined
    }
    
    private func setupBluetoothManager() {
        bluetoothManager = CBCentralManager(delegate: self, queue: nil)
    }
    
    private func checkCurrentPermissions() {
        Task {
            // Check notification permission
            let notificationSettings = await notificationCenter.notificationSettings()
            await MainActor.run {
                notificationPermissionStatus = notificationSettings.authorizationStatus
            }
        }
    }
    
    // MARK: - Permission Request Methods
    
    func requestLocationPermission() {
        guard let locationManager = locationManager else { return }
        
        switch locationPermissionStatus {
        case .notDetermined:
            // Request always authorization for background location
            locationManager.requestAlwaysAuthorization()
        case .authorizedWhenInUse:
            // Upgrade to always authorization
            locationManager.requestAlwaysAuthorization()
        case .denied, .restricted:
            // Direct user to settings
            openAppSettings()
        default:
            break
        }
    }
    
    func requestBluetoothPermission() {
        // Bluetooth permission is handled automatically when creating CBCentralManager
        // If denied, direct user to settings
        if bluetoothPermissionStatus == .unauthorized {
            openAppSettings()
        }
    }
    
    func requestNotificationPermission() {
        Task {
            do {
                let granted = try await notificationCenter.requestAuthorization(
                    options: [.alert, .sound, .badge]
                )
                
                await MainActor.run {
                    notificationPermissionStatus = granted ? .authorized : .denied
                }
                
                if granted {
                    // Register for remote notifications if needed
                    await UIApplication.shared.registerForRemoteNotifications()
                }
            } catch {
                print("Failed to request notification permission: \(error)")
            }
        }
    }
    
    func requestAllPermissions() {
        requestLocationPermission()
        requestBluetoothPermission()
        requestNotificationPermission()
    }
    
    // MARK: - Utility Methods
    
    func openAppSettings() {
        guard let settingsUrl = URL(string: UIApplication.openSettingsURLString) else { return }
        
        if UIApplication.shared.canOpenURL(settingsUrl) {
            UIApplication.shared.open(settingsUrl)
        }
    }
    
    func getPermissionStatusText(for permission: PermissionType) -> String {
        switch permission {
        case .location:
            return getLocationStatusText()
        case .bluetooth:
            return getBluetoothStatusText()
        case .notifications:
            return getNotificationStatusText()
        }
    }
    
    func getPermissionActionText(for permission: PermissionType) -> String {
        switch permission {
        case .location:
            return getLocationActionText()
        case .bluetooth:
            return getBluetoothActionText()
        case .notifications:
            return getNotificationActionText()
        }
    }
    
    // MARK: - Private Status Methods
    
    private func getLocationStatusText() -> String {
        switch locationPermissionStatus {
        case .notDetermined:
            return "Not requested"
        case .denied, .restricted:
            return "Denied"
        case .authorizedWhenInUse:
            return "When in use only"
        case .authorizedAlways:
            return "Always allowed"
        @unknown default:
            return "Unknown"
        }
    }
    
    private func getBluetoothStatusText() -> String {
        switch bluetoothPermissionStatus {
        case .unknown:
            return "Unknown"
        case .resetting:
            return "Resetting"
        case .unsupported:
            return "Not supported"
        case .unauthorized:
            return "Denied"
        case .poweredOff:
            return "Bluetooth off"
        case .poweredOn:
            return "Enabled"
        @unknown default:
            return "Unknown"
        }
    }
    
    private func getNotificationStatusText() -> String {
        switch notificationPermissionStatus {
        case .notDetermined:
            return "Not requested"
        case .denied:
            return "Denied"
        case .authorized:
            return "Enabled"
        case .provisional:
            return "Provisional"
        case .ephemeral:
            return "Ephemeral"
        @unknown default:
            return "Unknown"
        }
    }
    
    private func getLocationActionText() -> String {
        switch locationPermissionStatus {
        case .notDetermined:
            return "Request Permission"
        case .authorizedWhenInUse:
            return "Upgrade to Always"
        case .denied, .restricted:
            return "Open Settings"
        case .authorizedAlways:
            return "Enabled"
        @unknown default:
            return "Check Status"
        }
    }
    
    private func getBluetoothActionText() -> String {
        switch bluetoothPermissionStatus {
        case .unauthorized:
            return "Open Settings"
        case .poweredOff:
            return "Turn On Bluetooth"
        case .poweredOn:
            return "Enabled"
        default:
            return "Check Bluetooth"
        }
    }
    
    private func getNotificationActionText() -> String {
        switch notificationPermissionStatus {
        case .notDetermined:
            return "Request Permission"
        case .denied:
            return "Open Settings"
        case .authorized:
            return "Enabled"
        default:
            return "Check Status"
        }
    }
}

// MARK: - PermissionType Enum

enum PermissionType: CaseIterable {
    case location
    case bluetooth
    case notifications
    
    var displayName: String {
        switch self {
        case .location:
            return "Location"
        case .bluetooth:
            return "Bluetooth"
        case .notifications:
            return "Notifications"
        }
    }
    
    var icon: String {
        switch self {
        case .location:
            return "location.fill"
        case .bluetooth:
            return "bluetooth"
        case .notifications:
            return "bell.fill"
        }
    }
    
    var description: String {
        switch self {
        case .location:
            return "Required for GPS tracking during workouts"
        case .bluetooth:
            return "Required to connect to your Pebble watch"
        case .notifications:
            return "Get updates about your workout progress"
        }
    }
}

// MARK: - CLLocationManagerDelegate

extension PermissionManager: CLLocationManagerDelegate {
    
    nonisolated func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus) {
        Task { @MainActor in
            locationPermissionStatus = status
        }
    }
    
    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        // Handle location updates if needed
    }
    
    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        print("Location manager failed with error: \(error)")
    }
}

// MARK: - CBCentralManagerDelegate

extension PermissionManager: CBCentralManagerDelegate {
    
    nonisolated func centralManagerDidUpdateState(_ central: CBCentralManager) {
        Task { @MainActor in
            bluetoothPermissionStatus = central.state
        }
    }
}
