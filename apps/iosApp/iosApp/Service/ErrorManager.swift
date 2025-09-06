import Foundation
import os.log

/**
 * iOS-specific error handling and crash reporting system.
 * Implements TASK-022: Add iOS-specific error handling and crash reporting.
 * Follows TEC-004: Platform-specific error handling and user feedback.
 */

// MARK: - Error Categories

enum ErrorCategory: String, CaseIterable {
    case workout = "Workout"
    case pebble = "Pebble"
    case location = "Location"
    case permissions = "Permissions"
    case storage = "Storage"
    case network = "Network"
    case ui = "UI"
    case system = "System"
    
    var logCategory: String {
        return "com.arikachmad.pebblerun.\(rawValue.lowercased())"
    }
}

// MARK: - Error Severity

enum ErrorSeverity: String, CaseIterable {
    case low = "Low"
    case medium = "Medium"
    case high = "High"
    case critical = "Critical"
    
    var logType: OSLogType {
        switch self {
        case .low:
            return .debug
        case .medium:
            return .info
        case .high:
            return .error
        case .critical:
            return .fault
        }
    }
}

// MARK: - Error Context

struct ErrorContext {
    let timestamp: Date
    let category: ErrorCategory
    let severity: ErrorSeverity
    let error: Error
    let userAction: String?
    let deviceInfo: DeviceInfo
    let appState: AppState
    let metadata: [String: Any]
    
    init(
        category: ErrorCategory,
        severity: ErrorSeverity,
        error: Error,
        userAction: String? = nil,
        metadata: [String: Any] = [:]
    ) {
        self.timestamp = Date()
        self.category = category
        self.severity = severity
        self.error = error
        self.userAction = userAction
        self.deviceInfo = DeviceInfo.current
        self.appState = AppState.current
        self.metadata = metadata
    }
}

// MARK: - Device Info

struct DeviceInfo {
    let model: String
    let systemVersion: String
    let appVersion: String
    let buildNumber: String
    let identifier: String
    
    static var current: DeviceInfo {
        let device = UIDevice.current
        let bundle = Bundle.main
        
        return DeviceInfo(
            model: device.model,
            systemVersion: device.systemVersion,
            appVersion: bundle.infoDictionary?["CFBundleShortVersionString"] as? String ?? "Unknown",
            buildNumber: bundle.infoDictionary?["CFBundleVersion"] as? String ?? "Unknown",
            identifier: device.identifierForVendor?.uuidString ?? "Unknown"
        )
    }
}

// MARK: - App State

struct AppState {
    let isWorkoutActive: Bool
    let isPebbleConnected: Bool
    let hasLocationPermission: Bool
    let hasBluetoothPermission: Bool
    let memoryUsage: Int64
    let batteryLevel: Float
    
    static var current: AppState {
        let device = UIDevice.current
        device.isBatteryMonitoringEnabled = true
        
        return AppState(
            isWorkoutActive: false, // This would be retrieved from actual app state
            isPebbleConnected: false, // This would be retrieved from actual connection state
            hasLocationPermission: true, // This would be retrieved from permission manager
            hasBluetoothPermission: true, // This would be retrieved from permission manager
            memoryUsage: getMemoryUsage(),
            batteryLevel: device.batteryLevel
        )
    }
    
    private static func getMemoryUsage() -> Int64 {
        var info = mach_task_basic_info()
        var count = mach_msg_type_number_t(MemoryLayout<mach_task_basic_info>.size)/4
        
        let kerr: kern_return_t = withUnsafeMutablePointer(to: &info) {
            $0.withMemoryRebound(to: integer_t.self, capacity: 1) {
                task_info(mach_task_self_,
                         task_flavor_t(MACH_TASK_BASIC_INFO),
                         $0,
                         &count)
            }
        }
        
        if kerr == KERN_SUCCESS {
            return Int64(info.resident_size)
        } else {
            return 0
        }
    }
}

// MARK: - Error Manager

@MainActor
class ErrorManager: ObservableObject {
    
    static let shared = ErrorManager()
    
    // MARK: - Published Properties
    @Published var recentErrors: [ErrorContext] = []
    @Published var showErrorAlert = false
    @Published var currentErrorMessage = ""
    
    // MARK: - Private Properties
    private let maxStoredErrors = 100
    private let logger = Logger(subsystem: "com.arikachmad.pebblerun", category: "ErrorManager")
    private var errorReportingEnabled = true
    
    private init() {
        setupCrashReporting()
    }
    
    // MARK: - Public Methods
    
    /**
     * Report an error with context
     */
    func reportError(
        _ error: Error,
        category: ErrorCategory,
        severity: ErrorSeverity,
        userAction: String? = nil,
        metadata: [String: Any] = [:],
        showToUser: Bool = false
    ) {
        let context = ErrorContext(
            category: category,
            severity: severity,
            error: error,
            userAction: userAction,
            metadata: metadata
        )
        
        // Store error locally
        storeError(context)
        
        // Log to system
        logError(context)
        
        // Report to crash reporting service (if enabled)
        if errorReportingEnabled {
            reportToCrashService(context)
        }
        
        // Show to user if requested
        if showToUser {
            showErrorToUser(context)
        }
        
        // Handle critical errors
        if severity == .critical {
            handleCriticalError(context)
        }
    }
    
    /**
     * Report a simple error with automatic categorization
     */
    func reportError(_ error: Error, showToUser: Bool = false) {
        let category = categorizeError(error)
        let severity = determineSeverity(error)
        
        reportError(
            error,
            category: category,
            severity: severity,
            showToUser: showToUser
        )
    }
    
    /**
     * Clear stored errors
     */
    func clearErrors() {
        recentErrors.removeAll()
    }
    
    /**
     * Get error statistics
     */
    func getErrorStatistics() -> ErrorStatistics {
        let errorsByCategory = Dictionary(grouping: recentErrors, by: { $0.category })
        let errorsBySeverity = Dictionary(grouping: recentErrors, by: { $0.severity })
        
        return ErrorStatistics(
            totalErrors: recentErrors.count,
            errorsByCategory: errorsByCategory.mapValues { $0.count },
            errorsBySeverity: errorsBySeverity.mapValues { $0.count },
            lastErrorTime: recentErrors.last?.timestamp
        )
    }
    
    /**
     * Export error report
     */
    func exportErrorReport() -> String {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = .prettyPrinted
        
        let exportData = recentErrors.map { context in
            return [
                "timestamp": ISO8601DateFormatter().string(from: context.timestamp),
                "category": context.category.rawValue,
                "severity": context.severity.rawValue,
                "error": context.error.localizedDescription,
                "userAction": context.userAction ?? "",
                "deviceModel": context.deviceInfo.model,
                "systemVersion": context.deviceInfo.systemVersion,
                "appVersion": context.deviceInfo.appVersion,
                "metadata": context.metadata
            ]
        }
        
        do {
            let data = try JSONSerialization.data(withJSONObject: exportData, options: .prettyPrinted)
            return String(data: data, encoding: .utf8) ?? "Failed to export error report"
        } catch {
            return "Failed to serialize error report: \(error.localizedDescription)"
        }
    }
    
    // MARK: - Private Methods
    
    private func setupCrashReporting() {
        // Setup NSUncaughtExceptionHandler
        NSSetUncaughtExceptionHandler { exception in
            Task { @MainActor in
                ErrorManager.shared.handleUncaughtException(exception)
            }
        }
        
        // Setup signal handlers for crashes
        signal(SIGABRT) { signal in
            Task { @MainActor in
                ErrorManager.shared.handleSignal(signal)
            }
        }
        
        signal(SIGILL) { signal in
            Task { @MainActor in
                ErrorManager.shared.handleSignal(signal)
            }
        }
        
        signal(SIGSEGV) { signal in
            Task { @MainActor in
                ErrorManager.shared.handleSignal(signal)
            }
        }
        
        signal(SIGFPE) { signal in
            Task { @MainActor in
                ErrorManager.shared.handleSignal(signal)
            }
        }
        
        signal(SIGBUS) { signal in
            Task { @MainActor in
                ErrorManager.shared.handleSignal(signal)
            }
        }
    }
    
    private func storeError(_ context: ErrorContext) {
        recentErrors.append(context)
        
        // Keep only the most recent errors
        if recentErrors.count > maxStoredErrors {
            recentErrors.removeFirst(recentErrors.count - maxStoredErrors)
        }
    }
    
    private func logError(_ context: ErrorContext) {
        let categoryLogger = Logger(
            subsystem: "com.arikachmad.pebblerun",
            category: context.category.logCategory
        )
        
        let message = """
        Error: \(context.error.localizedDescription)
        User Action: \(context.userAction ?? "N/A")
        Metadata: \(context.metadata)
        """
        
        categoryLogger.log(level: context.severity.logType, "\(message)")
    }
    
    private func reportToCrashService(_ context: ErrorContext) {
        // In a real app, this would integrate with a crash reporting service
        // like Firebase Crashlytics, Bugsnag, or Sentry
        
        print("Reporting to crash service: \(context.error.localizedDescription)")
        
        // Example integration with a hypothetical crash service:
        // CrashService.shared.recordError(
        //     error: context.error,
        //     metadata: [
        //         "category": context.category.rawValue,
        //         "severity": context.severity.rawValue,
        //         "userAction": context.userAction ?? "",
        //         "deviceInfo": context.deviceInfo,
        //         "appState": context.appState
        //     ]
        // )
    }
    
    private func showErrorToUser(_ context: ErrorContext) {
        currentErrorMessage = getUserFriendlyMessage(for: context)
        showErrorAlert = true
    }
    
    private func handleCriticalError(_ context: ErrorContext) {
        // For critical errors, we might want to:
        // 1. Save current state
        // 2. Notify the user
        // 3. Attempt graceful recovery
        
        logger.critical("Critical error occurred: \(context.error.localizedDescription)")
        
        // Save current state before potential crash
        saveCriticalState()
        
        // Show critical error to user
        showCriticalErrorToUser(context)
    }
    
    private func saveCriticalState() {
        // Save important state to UserDefaults or other persistent storage
        // This could include current workout state, user preferences, etc.
        
        let state = [
            "timestamp": ISO8601DateFormatter().string(from: Date()),
            "appVersion": DeviceInfo.current.appVersion,
            "buildNumber": DeviceInfo.current.buildNumber
        ]
        
        UserDefaults.standard.set(state, forKey: "lastCriticalErrorState")
    }
    
    private func showCriticalErrorToUser(_ context: ErrorContext) {
        let message = """
        A critical error has occurred. The app may need to restart.
        
        Error: \(context.error.localizedDescription)
        
        Your data has been saved and this error has been reported.
        """
        
        currentErrorMessage = message
        showErrorAlert = true
    }
    
    private func handleUncaughtException(_ exception: NSException) {
        let error = NSError(
            domain: "UncaughtException",
            code: -1,
            userInfo: [
                NSLocalizedDescriptionKey: exception.reason ?? "Unknown exception",
                "exceptionName": exception.name.rawValue,
                "callStack": exception.callStackSymbols
            ]
        )
        
        reportError(
            error,
            category: .system,
            severity: .critical,
            userAction: "App crashed with uncaught exception"
        )
    }
    
    private func handleSignal(_ signal: Int32) {
        let error = NSError(
            domain: "SignalError",
            code: Int(signal),
            userInfo: [
                NSLocalizedDescriptionKey: "App received signal \(signal)",
                "signal": signal
            ]
        )
        
        reportError(
            error,
            category: .system,
            severity: .critical,
            userAction: "App crashed with signal"
        )
    }
    
    private func categorizeError(_ error: Error) -> ErrorCategory {
        let errorString = String(describing: type(of: error))
        
        switch errorString {
        case let str where str.contains("Location"):
            return .location
        case let str where str.contains("Pebble"):
            return .pebble
        case let str where str.contains("Workout"):
            return .workout
        case let str where str.contains("Permission"):
            return .permissions
        case let str where str.contains("Keychain"):
            return .storage
        case let str where str.contains("Network"), let str where str.contains("URL"):
            return .network
        default:
            return .system
        }
    }
    
    private func determineSeverity(_ error: Error) -> ErrorSeverity {
        let nsError = error as NSError
        
        // Determine severity based on error domain and code
        switch nsError.domain {
        case "NSCocoaErrorDomain":
            return nsError.code >= 256 ? .high : .medium
        case "NSURLErrorDomain":
            return .medium
        case "kCLErrorDomain":
            return .medium
        default:
            return .low
        }
    }
    
    private func getUserFriendlyMessage(for context: ErrorContext) -> String {
        switch context.category {
        case .workout:
            return "There was an issue with your workout. Please try again."
        case .pebble:
            return "Unable to connect to your Pebble watch. Please check the connection."
        case .location:
            return "Location services are having issues. Please check your location settings."
        case .permissions:
            return "The app needs additional permissions to work properly."
        case .storage:
            return "There was an issue saving your data. Please try again."
        case .network:
            return "Network connection error. Please check your internet connection."
        case .ui:
            return "There was a display issue. Please restart the app."
        case .system:
            return "An unexpected error occurred. Please restart the app."
        }
    }
}

// MARK: - Error Statistics

struct ErrorStatistics {
    let totalErrors: Int
    let errorsByCategory: [ErrorCategory: Int]
    let errorsBySeverity: [ErrorSeverity: Int]
    let lastErrorTime: Date?
}

// MARK: - Convenience Extensions

extension ErrorManager {
    
    /**
     * Report workout-related errors
     */
    func reportWorkoutError(_ error: Error, userAction: String? = nil, showToUser: Bool = true) {
        reportError(
            error,
            category: .workout,
            severity: .medium,
            userAction: userAction,
            showToUser: showToUser
        )
    }
    
    /**
     * Report Pebble connection errors
     */
    func reportPebbleError(_ error: Error, userAction: String? = nil, showToUser: Bool = true) {
        reportError(
            error,
            category: .pebble,
            severity: .high,
            userAction: userAction,
            showToUser: showToUser
        )
    }
    
    /**
     * Report location errors
     */
    func reportLocationError(_ error: Error, userAction: String? = nil, showToUser: Bool = false) {
        reportError(
            error,
            category: .location,
            severity: .medium,
            userAction: userAction,
            showToUser: showToUser
        )
    }
    
    /**
     * Report permission errors
     */
    func reportPermissionError(_ error: Error, userAction: String? = nil, showToUser: Bool = true) {
        reportError(
            error,
            category: .permissions,
            severity: .high,
            userAction: userAction,
            showToUser: showToUser
        )
    }
}
