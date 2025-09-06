import Foundation
import Combine
import shared
import UIKit

/**
 * iOS-specific ViewModel for SettingsView using ObservableObject pattern.
 * Implements TASK-019: Create iOS-specific ObservableObject classes for KMP integration.
 * Follows PAT-002: ObservableObject pattern for iOS SwiftUI integration.
 */

// MARK: - Supporting Types

enum DistanceUnit: String, CaseIterable {
    case kilometers = "km"
    case miles = "mi"
    
    var displayName: String {
        switch self {
        case .kilometers: return "Kilometers"
        case .miles: return "Miles"
        }
    }
}

enum PaceUnit: String, CaseIterable {
    case minPerKm = "min/km"
    case minPerMile = "min/mi"
    case kmPerHour = "km/h"
    case milesPerHour = "mph"
    
    var displayName: String {
        return rawValue
    }
}

enum ExportFormat: String, CaseIterable {
    case gpx = "GPX"
    case csv = "CSV"
    case json = "JSON"
    
    var displayName: String {
        return rawValue
    }
}

@MainActor
class SettingsViewModel: ObservableObject {
    
    // MARK: - Published Properties
    
    // Pebble Connection
    @Published var isPebbleConnected = false
    @Published var pebbleStatusText = "Disconnected"
    
    // Workout Settings
    @Published var gpsEnabled = true
    @Published var heartRateEnabled = true
    @Published var notificationsEnabled = true
    @Published var audioCuesEnabled = false
    
    // Units and Display
    @Published var distanceUnit: DistanceUnit = .kilometers
    @Published var paceUnit: PaceUnit = .minPerKm
    
    // Privacy & Security
    @Published var requireAuthentication = false
    @Published var anonymousDataSharing = false
    @Published var showClearDataAlert = false
    
    // Background Activity
    @Published var backgroundRefreshEnabled = true
    @Published var backgroundLocationEnabled = true
    
    // Export Settings
    @Published var exportFormat: ExportFormat = .gpx
    @Published var exportFromDate = Calendar.current.date(byAdding: .month, value: -1, to: Date()) ?? Date()
    @Published var exportToDate = Date()
    @Published var includeHeartRateData = true
    @Published var includeGPSData = true
    @Published var includeStatistics = true
    
    // Error Handling
    @Published var showError = false
    @Published var errorMessage = ""
    
    // Debug
    #if DEBUG
    @Published var debugModeEnabled = false
    #endif
    
    // MARK: - Computed Properties
    
    var appVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "Unknown"
    }
    
    var buildNumber: String {
        Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "Unknown"
    }
    
    var storageUsedText: String {
        // Calculate storage used - this would integrate with actual storage calculation
        return "2.5 MB"
    }
    
    // MARK: - Private Properties
    private var cancellables = Set<AnyCancellable>()
    private let userDefaults = UserDefaults.standard
    
    // KMP Integration
    private let settingsRepository: SettingsRepository
    private let workoutRepository: WorkoutRepository
    
    // MARK: - Initialization
    init() {
        // Use dependency injection from IOSContainer
        let container = IOSContainer.shared
        self.settingsRepository = MockSettingsRepository() // This would be from container in real app
        self.workoutRepository = container.workoutRepository
        
        setupBindings()
    }
    
    // MARK: - Setup
    private func setupBindings() {
        // Save settings when they change
        Publishers.CombineLatest4($gpsEnabled, $heartRateEnabled, $notificationsEnabled, $audioCuesEnabled)
            .debounce(for: .milliseconds(500), scheduler: RunLoop.main)
            .sink { [weak self] gps, hr, notifications, audio in
                self?.saveWorkoutSettings(gps: gps, hr: hr, notifications: notifications, audio: audio)
            }
            .store(in: &cancellables)
        
        Publishers.CombineLatest($distanceUnit, $paceUnit)
            .debounce(for: .milliseconds(500), scheduler: RunLoop.main)
            .sink { [weak self] distance, pace in
                self?.saveUnitSettings(distance: distance, pace: pace)
            }
            .store(in: &cancellables)
        
        Publishers.CombineLatest($requireAuthentication, $anonymousDataSharing)
            .debounce(for: .milliseconds(500), scheduler: RunLoop.main)
            .sink { [weak self] auth, sharing in
                self?.savePrivacySettings(auth: auth, sharing: sharing)
            }
            .store(in: &cancellables)
        
        // Check Pebble connection status periodically
        Timer.publish(every: 5.0, on: .main, in: .common)
            .autoconnect()
            .sink { [weak self] _ in
                self?.updatePebbleStatus()
            }
            .store(in: &cancellables)
    }
    
    // MARK: - Public Methods
    
    func loadSettings() {
        Task {
            await loadAllSettings()
        }
    }
    
    // MARK: - Pebble Connection Methods
    
    func connectToPebble() {
        Task {
            do {
                // This would integrate with the actual Pebble bridge
                try await MockPebbleManager.shared.connect()
                await MainActor.run {
                    isPebbleConnected = true
                    pebbleStatusText = "Connected"
                }
            } catch {
                handleError(error)
            }
        }
    }
    
    func disconnectFromPebble() {
        Task {
            MockPebbleManager.shared.disconnect()
            await MainActor.run {
                isPebbleConnected = false
                pebbleStatusText = "Disconnected"
            }
        }
    }
    
    func scanForPebbles() {
        Task {
            do {
                try await MockPebbleManager.shared.scanForDevices()
                // Handle scan results
            } catch {
                handleError(error)
            }
        }
    }
    
    // MARK: - Data Management Methods
    
    func showClearDataAlert() {
        showClearDataAlert = true
    }
    
    func clearAllData() {
        Task {
            do {
                // Clear all workout data
                let sessionsResult = await workoutRepository.getAllSessions(limit: nil, offset: 0, status: nil, startDate: nil, endDate: nil)
                
                switch sessionsResult {
                case .success(let sessions):
                    for session in sessions {
                        let _ = await workoutRepository.deleteSession(id: session.id)
                    }
                    
                    // Clear settings
                    resetAllSettings()
                    
                case .error(let error):
                    handleError(NSError(domain: "SettingsViewModel", code: -1, userInfo: [NSLocalizedDescriptionKey: error.localizedDescription]))
                }
            }
        }
    }
    
    func exportWorkouts() {
        Task {
            do {
                // Get all sessions in date range
                let startInstant = Instant.fromEpochMilliseconds(epochMilliseconds: Int64(exportFromDate.timeIntervalSince1970 * 1000))
                let endInstant = Instant.fromEpochMilliseconds(epochMilliseconds: Int64(exportToDate.timeIntervalSince1970 * 1000))
                
                let result = await workoutRepository.getAllSessions(
                    limit: nil,
                    offset: 0,
                    status: nil,
                    startDate: startInstant,
                    endDate: endInstant
                )
                
                switch result {
                case .success(let sessions):
                    let sessionIds = sessions.map { $0.id }
                    let exportResult = await workoutRepository.exportSessions(sessionIds: sessionIds)
                    
                    switch exportResult {
                    case .success(let exportData):
                        // Present share sheet with export data
                        presentShareSheet(with: exportData)
                    case .error(let error):
                        handleError(NSError(domain: "Export", code: -1, userInfo: [NSLocalizedDescriptionKey: error.localizedDescription]))
                    }
                    
                case .error(let error):
                    handleError(NSError(domain: "Export", code: -1, userInfo: [NSLocalizedDescriptionKey: error.localizedDescription]))
                }
            }
        }
    }
    
    func importWorkoutData() {
        // This would open a file picker to import workout data
        // For now, just show a placeholder
        print("Import workout data - not implemented yet")
    }
    
    // MARK: - System Settings Methods
    
    func openiOSSettings() {
        if let settingsUrl = URL(string: UIApplication.openSettingsURLString) {
            UIApplication.shared.open(settingsUrl)
        }
    }
    
    func openPrivacyPolicy() {
        if let url = URL(string: "https://example.com/privacy") {
            UIApplication.shared.open(url)
        }
    }
    
    func openTermsOfService() {
        if let url = URL(string: "https://example.com/terms") {
            UIApplication.shared.open(url)
        }
    }
    
    // MARK: - Debug Methods
    
    #if DEBUG
    func generateTestData() {
        Task {
            // Generate some test workout sessions
            for i in 0..<5 {
                let startTime = Date().addingTimeInterval(TimeInterval(-i * 86400)) // i days ago
                let endTime = startTime.addingTimeInterval(Double.random(in: 1800...3600)) // 30-60 min workout
                
                let session = WorkoutSession(
                    id: "test_\(i)",
                    startTime: Instant.fromEpochMilliseconds(epochMilliseconds: Int64(startTime.timeIntervalSince1970 * 1000)),
                    endTime: Instant.fromEpochMilliseconds(epochMilliseconds: Int64(endTime.timeIntervalSince1970 * 1000)),
                    status: WorkoutStatus.completed,
                    totalDuration: Int64(endTime.timeIntervalSince(startTime)),
                    totalDistance: Double.random(in: 3000...8000),
                    averagePace: Double.random(in: 240...360),
                    averageHeartRate: Int32.random(in: 140...170),
                    maxHeartRate: Int32.random(in: 170...190),
                    minHeartRate: Int32.random(in: 120...140),
                    calories: Int32.random(in: 300...600),
                    geoPoints: [],
                    hrSamples: [],
                    notes: "Test workout \(i)"
                )
                
                let _ = await workoutRepository.createSession(session: session)
            }
        }
    }
    #endif
    
    func resetAllSettings() {
        gpsEnabled = true
        heartRateEnabled = true
        notificationsEnabled = true
        audioCuesEnabled = false
        distanceUnit = .kilometers
        paceUnit = .minPerKm
        requireAuthentication = false
        anonymousDataSharing = false
        backgroundRefreshEnabled = true
        backgroundLocationEnabled = true
        
        #if DEBUG
        debugModeEnabled = false
        #endif
        
        // Clear UserDefaults
        for key in userDefaults.dictionaryRepresentation().keys {
            if key.hasPrefix("PebbleRun.") {
                userDefaults.removeObject(forKey: key)
            }
        }
    }
    
    func dismissError() {
        showError = false
        errorMessage = ""
    }
    
    // MARK: - Private Methods
    
    private func loadAllSettings() async {
        // Load workout settings
        gpsEnabled = userDefaults.bool(forKey: "PebbleRun.gpsEnabled") 
        heartRateEnabled = userDefaults.bool(forKey: "PebbleRun.heartRateEnabled")
        notificationsEnabled = userDefaults.bool(forKey: "PebbleRun.notificationsEnabled")
        audioCuesEnabled = userDefaults.bool(forKey: "PebbleRun.audioCuesEnabled")
        
        // Load unit settings
        if let distanceString = userDefaults.string(forKey: "PebbleRun.distanceUnit"),
           let unit = DistanceUnit(rawValue: distanceString) {
            distanceUnit = unit
        }
        
        if let paceString = userDefaults.string(forKey: "PebbleRun.paceUnit"),
           let unit = PaceUnit(rawValue: paceString) {
            paceUnit = unit
        }
        
        // Load privacy settings
        requireAuthentication = userDefaults.bool(forKey: "PebbleRun.requireAuthentication")
        anonymousDataSharing = userDefaults.bool(forKey: "PebbleRun.anonymousDataSharing")
        
        // Load background settings
        backgroundRefreshEnabled = userDefaults.bool(forKey: "PebbleRun.backgroundRefreshEnabled")
        backgroundLocationEnabled = userDefaults.bool(forKey: "PebbleRun.backgroundLocationEnabled")
        
        // Update Pebble status
        updatePebbleStatus()
    }
    
    private func saveWorkoutSettings(gps: Bool, hr: Bool, notifications: Bool, audio: Bool) {
        userDefaults.set(gps, forKey: "PebbleRun.gpsEnabled")
        userDefaults.set(hr, forKey: "PebbleRun.heartRateEnabled")
        userDefaults.set(notifications, forKey: "PebbleRun.notificationsEnabled")
        userDefaults.set(audio, forKey: "PebbleRun.audioCuesEnabled")
    }
    
    private func saveUnitSettings(distance: DistanceUnit, pace: PaceUnit) {
        userDefaults.set(distance.rawValue, forKey: "PebbleRun.distanceUnit")
        userDefaults.set(pace.rawValue, forKey: "PebbleRun.paceUnit")
    }
    
    private func savePrivacySettings(auth: Bool, sharing: Bool) {
        userDefaults.set(auth, forKey: "PebbleRun.requireAuthentication")
        userDefaults.set(sharing, forKey: "PebbleRun.anonymousDataSharing")
    }
    
    private func updatePebbleStatus() {
        // This would integrate with the actual Pebble bridge
        isPebbleConnected = MockPebbleManager.shared.isConnected
        pebbleStatusText = isPebbleConnected ? "Connected" : "Disconnected"
    }
    
    private func presentShareSheet(with data: String) {
        // Present iOS share sheet with export data
        // This would be implemented using UIActivityViewController
        print("Would present share sheet with data: \(data)")
    }
    
    private func handleError(_ error: Error) {
        errorMessage = error.localizedDescription
        showError = true
        print("SettingsViewModel Error: \(error)")
    }
}

// MARK: - Mock Implementations

private class MockSettingsRepository: SettingsRepository {
    func saveSettings(settings: AppSettings) async -> DomainResult<KotlinUnit> {
        return DomainResult.Success(data: KotlinUnit())
    }
    
    func getSettings() async -> DomainResult<AppSettings> {
        // Return default settings
        let settings = AppSettings(
            gpsEnabled: true,
            heartRateEnabled: true,
            notificationsEnabled: true,
            distanceUnit: "km",
            paceUnit: "min/km"
        )
        return DomainResult.Success(data: settings)
    }
    
    func observeSettings() -> Flow<AppSettings> {
        let settings = AppSettings(
            gpsEnabled: true,
            heartRateEnabled: true,
            notificationsEnabled: true,
            distanceUnit: "km",
            paceUnit: "min/km"
        )
        return FlowKt.flowOf(settings)
    }
}

private class MockWorkoutRepository: WorkoutRepository {
    private var sessions: [WorkoutSession] = []
    
    func createSession(session: WorkoutSession) async -> DomainResult<WorkoutSession> {
        sessions.append(session)
        return DomainResult.Success(data: session)
    }
    
    func updateSession(session: WorkoutSession) async -> DomainResult<WorkoutSession> {
        if let index = sessions.firstIndex(where: { $0.id == session.id }) {
            sessions[index] = session
        }
        return DomainResult.Success(data: session)
    }
    
    func getSessionById(id: String) async -> DomainResult<WorkoutSession?> {
        let session = sessions.first { $0.id == id }
        return DomainResult.Success(data: session)
    }
    
    func getAllSessions(limit: Int32?, offset: Int32, status: WorkoutStatus?, startDate: Instant?, endDate: Instant?) async -> DomainResult<[WorkoutSession]> {
        return DomainResult.Success(data: sessions)
    }
    
    func observeSessions() -> Flow<[WorkoutSession]> {
        return FlowKt.flowOf(sessions)
    }
    
    func observeSession(id: String) -> Flow<WorkoutSession?> {
        let session = sessions.first { $0.id == id }
        return FlowKt.flowOf(session)
    }
    
    func deleteSession(id: String) async -> DomainResult<KotlinUnit> {
        sessions.removeAll { $0.id == id }
        return DomainResult.Success(data: KotlinUnit())
    }
    
    func getActiveSession() async -> DomainResult<WorkoutSession?> {
        return DomainResult.Success(data: nil)
    }
    
    func observeActiveSession() -> Flow<WorkoutSession?> {
        return FlowKt.flowOf(nil)
    }
    
    func completeSession(id: String, endTime: Instant, finalStats: WorkoutSessionStats) async -> DomainResult<WorkoutSession> {
        let session = WorkoutSession(
            id: id,
            startTime: endTime,
            endTime: endTime,
            status: WorkoutStatus.completed,
            totalDuration: finalStats.totalDuration,
            totalDistance: finalStats.totalDistance,
            averagePace: 300.0,
            averageHeartRate: finalStats.averageHeartRate,
            maxHeartRate: finalStats.maxHeartRate,
            minHeartRate: 60,
            calories: finalStats.calories,
            geoPoints: [],
            hrSamples: [],
            notes: ""
        )
        return DomainResult.Success(data: session)
    }
    
    func getSessionStats(id: String) async -> DomainResult<WorkoutSessionStats?> {
        return DomainResult.Success(data: nil)
    }
    
    func exportSessions(sessionIds: [String]) async -> DomainResult<String> {
        return DomainResult.Success(data: "mock-export-data")
    }
}

private actor MockPebbleManager {
    static let shared = MockPebbleManager()
    
    private var _isConnected = false
    
    var isConnected: Bool {
        get async {
            return _isConnected
        }
    }
    
    func connect() async throws {
        // Simulate connection delay
        try await Task.sleep(nanoseconds: 1_000_000_000) // 1 second
        _isConnected = true
    }
    
    func disconnect() {
        _isConnected = false
    }
    
    func scanForDevices() async throws {
        // Simulate scan delay
        try await Task.sleep(nanoseconds: 2_000_000_000) // 2 seconds
    }
}

// MARK: - Mock Data Structures

private struct AppSettings {
    let gpsEnabled: Bool
    let heartRateEnabled: Bool
    let notificationsEnabled: Bool
    let distanceUnit: String
    let paceUnit: String
}

private protocol SettingsRepository {
    func saveSettings(settings: AppSettings) async -> DomainResult<KotlinUnit>
    func getSettings() async -> DomainResult<AppSettings>
    func observeSettings() -> Flow<AppSettings>
}
