import Foundation
import Combine
import shared

/**
 * iOS-specific ViewModel for HistoryView using ObservableObject pattern.
 * Implements TASK-019: Create iOS-specific ObservableObject classes for KMP integration.
 * Follows PAT-002: ObservableObject pattern for iOS SwiftUI integration.
 */

// MARK: - Supporting Types

struct WorkoutSessionUI: Identifiable {
    let id: String
    let dateText: String
    let timeText: String
    let durationText: String
    let distanceText: String
    let avgPaceText: String
    let avgHRText: String
    
    init(from session: WorkoutSession) {
        self.id = session.id
        
        let startDate = Date(timeIntervalSince1970: TimeInterval(session.startTime.toEpochMilliseconds() / 1000))
        
        let dateFormatter = DateFormatter()
        dateFormatter.dateStyle = .medium
        self.dateText = dateFormatter.string(from: startDate)
        
        let timeFormatter = DateFormatter()
        timeFormatter.timeStyle = .short
        self.timeText = timeFormatter.string(from: startDate)
        
        // Format duration
        let duration = session.totalDuration
        let hours = duration / 3600
        let minutes = (duration % 3600) / 60
        let seconds = duration % 60
        self.durationText = String(format: "%02d:%02d:%02d", hours, minutes, seconds)
        
        // Format distance
        let distanceKm = session.totalDistance / 1000.0
        self.distanceText = String(format: "%.2f km", distanceKm)
        
        // Format pace
        if session.averagePace > 0 {
            let minutes = Int(session.averagePace) / 60
            let seconds = Int(session.averagePace) % 60
            self.avgPaceText = String(format: "%d:%02d", minutes, seconds)
        } else {
            self.avgPaceText = "--:--"
        }
        
        // Format heart rate
        if session.averageHeartRate > 0 {
            self.avgHRText = "\(session.averageHeartRate)"
        } else {
            self.avgHRText = "--"
        }
    }
}

enum TimePeriod: CaseIterable {
    case lastWeek, lastMonth, last3Months, lastYear, allTime
    
    var displayName: String {
        switch self {
        case .lastWeek: return "Last Week"
        case .lastMonth: return "Last Month"
        case .last3Months: return "Last 3 Months"
        case .lastYear: return "Last Year"
        case .allTime: return "All Time"
        }
    }
}

enum SortOption: CaseIterable {
    case dateNewest, dateOldest, durationLongest, distanceFarthest
    
    var displayName: String {
        switch self {
        case .dateNewest: return "Date (Newest)"
        case .dateOldest: return "Date (Oldest)"
        case .durationLongest: return "Duration (Longest)"
        case .distanceFarthest: return "Distance (Farthest)"
        }
    }
}

@MainActor
class HistoryViewModel: ObservableObject {
    
    // MARK: - Published Properties
    @Published var workouts: [WorkoutSessionUI] = []
    @Published var isLoading = false
    @Published var showError = false
    @Published var errorMessage = ""
    @Published var selectedPeriod: TimePeriod = .allTime
    @Published var sortOption: SortOption = .dateNewest
    
    // MARK: - Computed Properties
    var selectedPeriodText: String {
        selectedPeriod.displayName
    }
    
    var totalWorkouts: Int {
        workouts.count
    }
    
    var totalDistance: String {
        let total = workouts.reduce(0.0) { sum, workout in
            // Extract distance from workout.distanceText (e.g., "5.23 km")
            let distanceString = workout.distanceText.replacingOccurrences(of: " km", with: "")
            return sum + (Double(distanceString) ?? 0.0)
        }
        return String(format: "%.1f km", total)
    }
    
    var averageDuration: String {
        guard !workouts.isEmpty else { return "--:--" }
        
        let totalSeconds = workouts.reduce(0) { sum, workout in
            // Parse duration text (HH:MM:SS)
            let components = workout.durationText.split(separator: ":").compactMap { Int($0) }
            guard components.count == 3 else { return sum }
            return sum + (components[0] * 3600 + components[1] * 60 + components[2])
        }
        
        let avgSeconds = totalSeconds / workouts.count
        let hours = avgSeconds / 3600
        let minutes = (avgSeconds % 3600) / 60
        let seconds = avgSeconds % 60
        
        if hours > 0 {
            return String(format: "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            return String(format: "%02d:%02d", minutes, seconds)
        }
    }
    
    // MARK: - Private Properties
    private var cancellables = Set<AnyCancellable>()
    private var allWorkouts: [WorkoutSessionUI] = []
    
    // KMP Integration
    private let workoutRepository: WorkoutRepository
    
    // MARK: - Initialization
    init() {
        // Use dependency injection from IOSContainer
        self.workoutRepository = IOSContainer.shared.workoutRepository
        
        setupBindings()
    }
    
    // MARK: - Setup
    private func setupBindings() {
        // React to filter changes
        Publishers.CombineLatest($selectedPeriod, $sortOption)
            .debounce(for: .milliseconds(300), scheduler: RunLoop.main)
            .sink { [weak self] period, sort in
                self?.applyFiltersAndSort()
            }
            .store(in: &cancellables)
    }
    
    // MARK: - Public Methods
    func loadWorkouts() {
        Task {
            await performLoad()
        }
    }
    
    func refreshWorkouts() async {
        await performLoad()
    }
    
    func deleteWorkout(_ workout: WorkoutSessionUI) {
        Task {
            do {
                let result = await workoutRepository.deleteSession(id: workout.id)
                switch result {
                case .success:
                    await MainActor.run {
                        allWorkouts.removeAll { $0.id == workout.id }
                        applyFiltersAndSort()
                    }
                case .error(let error):
                    handleError(NSError(domain: "HistoryViewModel", code: -1, userInfo: [NSLocalizedDescriptionKey: error.localizedDescription]))
                }
            }
        }
    }
    
    func exportWorkouts() {
        Task {
            do {
                let sessionIds = workouts.map { $0.id }
                let result = await workoutRepository.exportSessions(sessionIds: sessionIds)
                
                switch result {
                case .success(let exportData):
                    // Handle export data - in a real app, this would trigger a share sheet
                    print("Export data: \(exportData)")
                case .error(let error):
                    handleError(NSError(domain: "HistoryViewModel", code: -1, userInfo: [NSLocalizedDescriptionKey: error.localizedDescription]))
                }
            }
        }
    }
    
    func resetFilters() {
        selectedPeriod = .allTime
        sortOption = .dateNewest
    }
    
    func dismissError() {
        showError = false
        errorMessage = ""
    }
    
    // MARK: - Private Methods
    private func performLoad() async {
        await MainActor.run {
            isLoading = true
        }
        
        do {
            let result = await workoutRepository.getAllSessions(
                limit: nil,
                offset: 0,
                status: nil,
                startDate: nil,
                endDate: nil
            )
            
            switch result {
            case .success(let sessions):
                let uiSessions = sessions.map { WorkoutSessionUI(from: $0) }
                
                await MainActor.run {
                    allWorkouts = uiSessions
                    applyFiltersAndSort()
                    isLoading = false
                }
                
            case .error(let error):
                await MainActor.run {
                    isLoading = false
                    handleError(NSError(domain: "HistoryViewModel", code: -1, userInfo: [NSLocalizedDescriptionKey: error.localizedDescription]))
                }
            }
            
        }
    }
    
    private func applyFiltersAndSort() {
        var filtered = allWorkouts
        
        // Apply time period filter
        if selectedPeriod != .allTime {
            let cutoffDate = getCutoffDate(for: selectedPeriod)
            filtered = filtered.filter { workout in
                // Parse workout date and compare
                let dateFormatter = DateFormatter()
                dateFormatter.dateStyle = .medium
                if let workoutDate = dateFormatter.date(from: workout.dateText) {
                    return workoutDate >= cutoffDate
                }
                return true
            }
        }
        
        // Apply sort
        switch sortOption {
        case .dateNewest:
            filtered.sort { workout1, workout2 in
                let dateFormatter = DateFormatter()
                dateFormatter.dateStyle = .medium
                let date1 = dateFormatter.date(from: workout1.dateText) ?? Date.distantPast
                let date2 = dateFormatter.date(from: workout2.dateText) ?? Date.distantPast
                return date1 > date2
            }
            
        case .dateOldest:
            filtered.sort { workout1, workout2 in
                let dateFormatter = DateFormatter()
                dateFormatter.dateStyle = .medium
                let date1 = dateFormatter.date(from: workout1.dateText) ?? Date.distantPast
                let date2 = dateFormatter.date(from: workout2.dateText) ?? Date.distantPast
                return date1 < date2
            }
            
        case .durationLongest:
            filtered.sort { workout1, workout2 in
                let duration1 = parseDuration(workout1.durationText)
                let duration2 = parseDuration(workout2.durationText)
                return duration1 > duration2
            }
            
        case .distanceFarthest:
            filtered.sort { workout1, workout2 in
                let distance1 = parseDistance(workout1.distanceText)
                let distance2 = parseDistance(workout2.distanceText)
                return distance1 > distance2
            }
        }
        
        workouts = filtered
    }
    
    private func getCutoffDate(for period: TimePeriod) -> Date {
        let calendar = Calendar.current
        let now = Date()
        
        switch period {
        case .lastWeek:
            return calendar.date(byAdding: .weekOfYear, value: -1, to: now) ?? now
        case .lastMonth:
            return calendar.date(byAdding: .month, value: -1, to: now) ?? now
        case .last3Months:
            return calendar.date(byAdding: .month, value: -3, to: now) ?? now
        case .lastYear:
            return calendar.date(byAdding: .year, value: -1, to: now) ?? now
        case .allTime:
            return Date.distantPast
        }
    }
    
    private func parseDuration(_ durationText: String) -> Int {
        let components = durationText.split(separator: ":").compactMap { Int($0) }
        guard components.count == 3 else { return 0 }
        return components[0] * 3600 + components[1] * 60 + components[2]
    }
    
    private func parseDistance(_ distanceText: String) -> Double {
        let distanceString = distanceText.replacingOccurrences(of: " km", with: "")
        return Double(distanceString) ?? 0.0
    }
    
    private func handleError(_ error: Error) {
        errorMessage = error.localizedDescription
        showError = true
        print("HistoryViewModel Error: \(error)")
    }
}

// MARK: - Mock Implementation
private class MockWorkoutRepository: WorkoutRepository {
    private var mockSessions: [WorkoutSession] = []
    
    init() {
        // Generate some mock data for demonstration
        generateMockData()
    }
    
    private func generateMockData() {
        let now = Date()
        let calendar = Calendar.current
        
        for i in 0..<10 {
            let startDate = calendar.date(byAdding: .day, value: -i * 3, to: now) ?? now
            let startTime = Instant.fromEpochMilliseconds(epochMilliseconds: Int64(startDate.timeIntervalSince1970 * 1000))
            let endTime = Instant.fromEpochMilliseconds(epochMilliseconds: Int64((startDate.timeIntervalSince1970 + Double.random(in: 1800...3600)) * 1000))
            
            let session = WorkoutSession(
                id: "mock_\(i)",
                startTime: startTime,
                endTime: endTime,
                status: WorkoutStatus.completed,
                totalDuration: Int64.random(in: 1800...3600),
                totalDistance: Double.random(in: 3000...8000),
                averagePace: Double.random(in: 240...360),
                averageHeartRate: Int32.random(in: 140...170),
                maxHeartRate: Int32.random(in: 170...190),
                minHeartRate: Int32.random(in: 120...140),
                calories: Int32.random(in: 300...600),
                geoPoints: [],
                hrSamples: [],
                notes: ""
            )
            
            mockSessions.append(session)
        }
    }
    
    func createSession(session: WorkoutSession) async -> DomainResult<WorkoutSession> {
        mockSessions.append(session)
        return DomainResult.Success(data: session)
    }
    
    func updateSession(session: WorkoutSession) async -> DomainResult<WorkoutSession> {
        if let index = mockSessions.firstIndex(where: { $0.id == session.id }) {
            mockSessions[index] = session
        }
        return DomainResult.Success(data: session)
    }
    
    func getSessionById(id: String) async -> DomainResult<WorkoutSession?> {
        let session = mockSessions.first { $0.id == id }
        return DomainResult.Success(data: session)
    }
    
    func getAllSessions(limit: Int32?, offset: Int32, status: WorkoutStatus?, startDate: Instant?, endDate: Instant?) async -> DomainResult<[WorkoutSession]> {
        return DomainResult.Success(data: mockSessions)
    }
    
    func observeSessions() -> Flow<[WorkoutSession]> {
        return FlowKt.flowOf(mockSessions)
    }
    
    func observeSession(id: String) -> Flow<WorkoutSession?> {
        let session = mockSessions.first { $0.id == id }
        return FlowKt.flowOf(session)
    }
    
    func deleteSession(id: String) async -> DomainResult<KotlinUnit> {
        mockSessions.removeAll { $0.id == id }
        return DomainResult.Success(data: KotlinUnit())
    }
    
    func getActiveSession() async -> DomainResult<WorkoutSession?> {
        return DomainResult.Success(data: nil)
    }
    
    func observeActiveSession() -> Flow<WorkoutSession?> {
        return FlowKt.flowOf(nil)
    }
    
    func completeSession(id: String, endTime: Instant, finalStats: WorkoutSessionStats) async -> DomainResult<WorkoutSession> {
        // Mock implementation
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
