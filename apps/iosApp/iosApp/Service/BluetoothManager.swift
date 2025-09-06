import Foundation
import CoreBluetooth
import Combine

/**
 * iOS-specific Bluetooth management for PebbleRun.
 * Implements TASK-038: Platform-specific Bluetooth management.
 */
class BluetoothManager: NSObject, ObservableObject {
    
    private var centralManager: CBCentralManager!
    private var cancellables = Set<AnyCancellable>()
    
    // State tracking
    @Published var bluetoothState: BluetoothState = .unknown
    @Published var discoveredPebbles: Set<PebblePeripheral> = []
    @Published var connectedPebbles: Set<PebblePeripheral> = []
    @Published var isScanning = false
    
    // Connection management
    private var connectionAttempts: [CBPeripheral: Int] = [:]
    private var connectionTimers: [CBPeripheral: Timer] = [:]
    private var signalStrengthTimers: [CBPeripheral: Timer] = [:]
    
    enum BluetoothState: String, CaseIterable {
        case unknown = "Unknown"
        case resetting = "Resetting"
        case unsupported = "Unsupported"
        case unauthorized = "Unauthorized"
        case poweredOff = "Powered Off"
        case poweredOn = "Powered On"
        
        var description: String {
            return rawValue
        }
        
        var canScan: Bool {
            return self == .poweredOn
        }
    }
    
    enum ConnectionQuality: String, CaseIterable {
        case excellent = "Excellent"
        case good = "Good"
        case fair = "Fair"
        case poor = "Poor"
        
        var description: String {
            return rawValue
        }
    }
    
    struct PebblePeripheral: Hashable, Identifiable {
        let id = UUID()
        let peripheral: CBPeripheral
        let name: String
        let rssi: NSNumber
        let advertisementData: [String: Any]
        var isConnected: Bool
        var connectionQuality: ConnectionQuality
        var lastSeen: Date
        var connectionAttempts: Int
        var successfulConnections: Int
        
        func hash(into hasher: inout Hasher) {
            hasher.combine(peripheral.identifier)
        }
        
        static func == (lhs: PebblePeripheral, rhs: PebblePeripheral) -> Bool {
            return lhs.peripheral.identifier == rhs.peripheral.identifier
        }
    }
    
    override init() {
        super.init()
        setupCentralManager()
    }
    
    /**
     * Sets up Core Bluetooth central manager
     */
    private func setupCentralManager() {
        centralManager = CBCentralManager(delegate: self, queue: nil)
    }
    
    /**
     * Starts scanning for Pebble devices
     */
    func startScanning() {
        guard bluetoothState.canScan else {
            print("Cannot start scanning - Bluetooth state: \(bluetoothState)")
            return
        }
        
        if !isScanning {
            // Clear previous discoveries
            discoveredPebbles.removeAll()
            
            // Start scanning for Pebble devices
            // Pebble devices typically advertise standard services
            centralManager.scanForPeripherals(withServices: nil, options: [
                CBCentralManagerScanOptionAllowDuplicatesKey: true
            ])
            
            isScanning = true
            print("Started scanning for Pebble devices")
            
            // Stop scanning after 30 seconds
            DispatchQueue.main.asyncAfter(deadline: .now() + 30) {
                self.stopScanning()
            }
        }
    }
    
    /**
     * Stops scanning for devices
     */
    func stopScanning() {
        if isScanning {
            centralManager.stopScan()
            isScanning = false
            print("Stopped scanning for devices")
        }
    }
    
    /**
     * Connects to a specific Pebble device
     */
    func connectToPebble(_ pebblePeripheral: PebblePeripheral) {
        let peripheral = pebblePeripheral.peripheral
        
        guard peripheral.state != .connected else {
            print("Already connected to \(pebblePeripheral.name)")
            return
        }
        
        // Track connection attempts
        connectionAttempts[peripheral] = (connectionAttempts[peripheral] ?? 0) + 1
        
        print("Attempting to connect to Pebble: \(pebblePeripheral.name)")
        
        // Set connection timeout
        let timer = Timer.scheduledTimer(withTimeInterval: 15.0, repeats: false) { _ in
            self.handleConnectionTimeout(peripheral)
        }
        connectionTimers[peripheral] = timer
        
        centralManager.connect(peripheral, options: nil)
    }
    
    /**
     * Disconnects from a Pebble device
     */
    func disconnectFromPebble(_ pebblePeripheral: PebblePeripheral) {
        let peripheral = pebblePeripheral.peripheral
        
        print("Disconnecting from Pebble: \(pebblePeripheral.name)")
        
        // Cancel any connection timers
        connectionTimers[peripheral]?.invalidate()
        connectionTimers.removeValue(forKey: peripheral)
        
        // Stop signal strength monitoring
        signalStrengthTimers[peripheral]?.invalidate()
        signalStrengthTimers.removeValue(forKey: peripheral)
        
        centralManager.cancelPeripheralConnection(peripheral)
    }
    
    /**
     * Gets connection information for a Pebble device
     */
    func getPebbleConnectionInfo(_ pebblePeripheral: PebblePeripheral) -> PebbleConnectionInfo {
        let peripheral = pebblePeripheral.peripheral
        
        return PebbleConnectionInfo(
            pebble: pebblePeripheral,
            isConnected: peripheral.state == .connected,
            connectionQuality: assessConnectionQuality(pebblePeripheral),
            signalStrength: pebblePeripheral.rssi.intValue,
            lastSeen: pebblePeripheral.lastSeen,
            connectionAttempts: connectionAttempts[peripheral] ?? 0,
            successfulConnections: pebblePeripheral.successfulConnections
        )
    }
    
    /**
     * Assesses connection quality based on RSSI and stability
     */
    private func assessConnectionQuality(_ pebblePeripheral: PebblePeripheral) -> ConnectionQuality {
        let rssi = pebblePeripheral.rssi.intValue
        
        switch rssi {
        case -40...0:
            return .excellent
        case -60..<(-40):
            return .good
        case -80..<(-60):
            return .fair
        default:
            return .poor
        }
    }
    
    /**
     * Handles connection timeout
     */
    private func handleConnectionTimeout(_ peripheral: CBPeripheral) {
        print("Connection timeout for peripheral: \(peripheral.name ?? "Unknown")")
        
        connectionTimers.removeValue(forKey: peripheral)
        centralManager.cancelPeripheralConnection(peripheral)
        
        // Update UI
        updatePebbleConnectionState(peripheral, isConnected: false)
    }
    
    /**
     * Starts monitoring signal strength for connected device
     */
    private func startSignalStrengthMonitoring(_ peripheral: CBPeripheral) {
        let timer = Timer.scheduledTimer(withTimeInterval: 5.0, repeats: true) { _ in
            peripheral.readRSSI()
        }
        signalStrengthTimers[peripheral] = timer
    }
    
    /**
     * Stops monitoring signal strength for device
     */
    private func stopSignalStrengthMonitoring(_ peripheral: CBPeripheral) {
        signalStrengthTimers[peripheral]?.invalidate()
        signalStrengthTimers.removeValue(forKey: peripheral)
    }
    
    /**
     * Updates Pebble connection state in UI
     */
    private func updatePebbleConnectionState(_ peripheral: CBPeripheral, isConnected: Bool) {
        // Update discovered pebbles
        discoveredPebbles = discoveredPebbles.map { pebble in
            if pebble.peripheral.identifier == peripheral.identifier {
                var updatedPebble = pebble
                updatedPebble.isConnected = isConnected
                return updatedPebble
            }
            return pebble
        }
        
        // Update connected pebbles
        if isConnected {
            if let pebble = discoveredPebbles.first(where: { $0.peripheral.identifier == peripheral.identifier }) {
                connectedPebbles.insert(pebble)
            }
        } else {
            connectedPebbles = connectedPebbles.filter { $0.peripheral.identifier != peripheral.identifier }
        }
    }
    
    /**
     * Checks if device is a Pebble based on name and advertisement data
     */
    private func isPebbleDevice(name: String?, advertisementData: [String: Any]) -> Bool {
        guard let deviceName = name else { return false }
        
        let pebbleNames = ["Pebble", "P2-HR", "Pebble Time", "Pebble Steel", "Pebble 2"]
        
        return pebbleNames.contains { pebbleName in
            deviceName.localizedCaseInsensitiveContains(pebbleName)
        }
    }
    
    /**
     * Creates PebblePeripheral from discovered peripheral
     */
    private func createPebblePeripheral(
        peripheral: CBPeripheral,
        rssi: NSNumber,
        advertisementData: [String: Any]
    ) -> PebblePeripheral {
        return PebblePeripheral(
            peripheral: peripheral,
            name: peripheral.name ?? "Unknown Pebble",
            rssi: rssi,
            advertisementData: advertisementData,
            isConnected: peripheral.state == .connected,
            connectionQuality: assessConnectionQuality(rssi),
            lastSeen: Date(),
            connectionAttempts: connectionAttempts[peripheral] ?? 0,
            successfulConnections: 0
        )
    }
    
    private func assessConnectionQuality(_ rssi: NSNumber) -> ConnectionQuality {
        let rssiValue = rssi.intValue
        
        switch rssiValue {
        case -40...0:
            return .excellent
        case -60..<(-40):
            return .good
        case -80..<(-60):
            return .fair
        default:
            return .poor
        }
    }
    
    /**
     * Gets Bluetooth management statistics
     */
    func getBluetoothStats() -> BluetoothStats {
        return BluetoothStats(
            bluetoothState: bluetoothState,
            totalDiscoveredPebbles: discoveredPebbles.count,
            connectedPebbles: connectedPebbles.count,
            averageConnectionQuality: calculateAverageConnectionQuality(),
            isScanning: isScanning,
            totalConnectionAttempts: connectionAttempts.values.reduce(0, +)
        )
    }
    
    private func calculateAverageConnectionQuality() -> ConnectionQuality {
        guard !connectedPebbles.isEmpty else { return .poor }
        
        let qualityScores = connectedPebbles.map { pebble in
            switch pebble.connectionQuality {
            case .excellent: return 4
            case .good: return 3
            case .fair: return 2
            case .poor: return 1
            }
        }
        
        let averageScore = Double(qualityScores.reduce(0, +)) / Double(qualityScores.count)
        
        switch averageScore {
        case 3.5...4.0:
            return .excellent
        case 2.5..<3.5:
            return .good
        case 1.5..<2.5:
            return .fair
        default:
            return .poor
        }
    }
    
    /**
     * Cleans up resources
     */
    func cleanup() {
        stopScanning()
        
        // Cancel all connection timers
        connectionTimers.values.forEach { $0.invalidate() }
        connectionTimers.removeAll()
        
        // Cancel all signal strength timers
        signalStrengthTimers.values.forEach { $0.invalidate() }
        signalStrengthTimers.removeAll()
        
        // Disconnect all connected devices
        connectedPebbles.forEach { pebble in
            centralManager.cancelPeripheralConnection(pebble.peripheral)
        }
    }
    
    struct PebbleConnectionInfo {
        let pebble: PebblePeripheral
        let isConnected: Bool
        let connectionQuality: ConnectionQuality
        let signalStrength: Int
        let lastSeen: Date
        let connectionAttempts: Int
        let successfulConnections: Int
    }
    
    struct BluetoothStats {
        let bluetoothState: BluetoothState
        let totalDiscoveredPebbles: Int
        let connectedPebbles: Int
        let averageConnectionQuality: ConnectionQuality
        let isScanning: Bool
        let totalConnectionAttempts: Int
    }
}

// MARK: - CBCentralManagerDelegate

extension BluetoothManager: CBCentralManagerDelegate {
    
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        switch central.state {
        case .unknown:
            bluetoothState = .unknown
        case .resetting:
            bluetoothState = .resetting
        case .unsupported:
            bluetoothState = .unsupported
        case .unauthorized:
            bluetoothState = .unauthorized
        case .poweredOff:
            bluetoothState = .poweredOff
            stopScanning()
        case .poweredOn:
            bluetoothState = .poweredOn
            print("Bluetooth powered on - ready to scan")
        @unknown default:
            bluetoothState = .unknown
        }
        
        print("Bluetooth state changed: \(bluetoothState)")
    }
    
    func centralManager(
        _ central: CBCentralManager,
        didDiscover peripheral: CBPeripheral,
        advertisementData: [String: Any],
        rssi RSSI: NSNumber
    ) {
        // Filter for Pebble devices
        guard isPebbleDevice(name: peripheral.name, advertisementData: advertisementData) else {
            return
        }
        
        let pebblePeripheral = createPebblePeripheral(
            peripheral: peripheral,
            rssi: RSSI,
            advertisementData: advertisementData
        )
        
        // Update or add to discovered devices
        discoveredPebbles.insert(pebblePeripheral)
        
        print("Discovered Pebble: \(pebblePeripheral.name) (RSSI: \(RSSI))")
    }
    
    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        print("Connected to peripheral: \(peripheral.name ?? "Unknown")")
        
        // Cancel connection timer
        connectionTimers[peripheral]?.invalidate()
        connectionTimers.removeValue(forKey: peripheral)
        
        // Update connection state
        updatePebbleConnectionState(peripheral, isConnected: true)
        
        // Start signal strength monitoring
        startSignalStrengthMonitoring(peripheral)
        
        // Set peripheral delegate for service discovery
        peripheral.delegate = self
        peripheral.discoverServices(nil)
    }
    
    func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        print("Failed to connect to peripheral: \(peripheral.name ?? "Unknown")")
        if let error = error {
            print("Connection error: \(error.localizedDescription)")
        }
        
        // Cancel connection timer
        connectionTimers[peripheral]?.invalidate()
        connectionTimers.removeValue(forKey: peripheral)
        
        // Update connection state
        updatePebbleConnectionState(peripheral, isConnected: false)
    }
    
    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        print("Disconnected from peripheral: \(peripheral.name ?? "Unknown")")
        if let error = error {
            print("Disconnection error: \(error.localizedDescription)")
        }
        
        // Stop signal strength monitoring
        stopSignalStrengthMonitoring(peripheral)
        
        // Update connection state
        updatePebbleConnectionState(peripheral, isConnected: false)
    }
}

// MARK: - CBPeripheralDelegate

extension BluetoothManager: CBPeripheralDelegate {
    
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        if let error = error {
            print("Service discovery error: \(error.localizedDescription)")
            return
        }
        
        print("Discovered services for \(peripheral.name ?? "Unknown")")
        
        // Discover characteristics for each service
        peripheral.services?.forEach { service in
            peripheral.discoverCharacteristics(nil, for: service)
        }
    }
    
    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        if let error = error {
            print("Characteristic discovery error: \(error.localizedDescription)")
            return
        }
        
        print("Discovered characteristics for service: \(service.uuid)")
    }
    
    func peripheral(_ peripheral: CBPeripheral, didReadRSSI RSSI: NSNumber, error: Error?) {
        if let error = error {
            print("RSSI read error: \(error.localizedDescription)")
            return
        }
        
        // Update RSSI for the peripheral
        discoveredPebbles = discoveredPebbles.map { pebble in
            if pebble.peripheral.identifier == peripheral.identifier {
                var updatedPebble = pebble
                updatedPebble.rssi = RSSI
                updatedPebble.connectionQuality = assessConnectionQuality(RSSI)
                updatedPebble.lastSeen = Date()
                return updatedPebble
            }
            return pebble
        }
        
        // Also update connected pebbles
        connectedPebbles = connectedPebbles.map { pebble in
            if pebble.peripheral.identifier == peripheral.identifier {
                var updatedPebble = pebble
                updatedPebble.rssi = RSSI
                updatedPebble.connectionQuality = assessConnectionQuality(RSSI)
                updatedPebble.lastSeen = Date()
                return updatedPebble
            }
            return pebble
        }
        
        print("Updated RSSI for \(peripheral.name ?? "Unknown"): \(RSSI)")
    }
}
