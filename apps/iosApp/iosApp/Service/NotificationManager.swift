import Foundation
import UserNotifications
import shared

/**
 * iOS Local Notification Manager for PebbleRun
 * Implements TASK-035: iOS local notification system for workout updates
 */
@MainActor
class NotificationManager: NSObject, ObservableObject {
    
    static let shared = NotificationManager()
    
    // Notification categories
    private let workoutCategory = "WORKOUT_CATEGORY"
    private let milestoneCategory = "MILESTONE_CATEGORY"
    private let alertCategory = "ALERT_CATEGORY"
    
    // Notification identifiers
    private let workoutNotificationId = "workout-active"
    private let milestoneNotificationId = "workout-milestone"
    private let alertNotificationId = "workout-alert"
    
    override init() {
        super.init()
        setupNotificationCategories()
    }
    
    /**
     * Requests notification permissions from user
     */
    func requestNotificationPermissions() async -> Bool {
        let center = UNUserNotificationCenter.current()
        
        do {
            let granted = try await center.requestAuthorization(options: [
                .alert, .sound, .badge, .criticalAlert
            ])
            
            if granted {
                print("Notification permissions granted")
                await setupNotificationCategories()
            } else {
                print("Notification permissions denied")
            }
            
            return granted
        } catch {
            print("Error requesting notification permissions: \(error)")
            return false
        }
    }
    
    /**
     * Sets up notification categories and actions
     */
    private func setupNotificationCategories() async {
        let center = UNUserNotificationCenter.current()
        
        // Workout notification actions
        let pauseAction = UNNotificationAction(
            identifier: "PAUSE_WORKOUT",
            title: "Pause",
            options: [.foreground]
        )
        
        let stopAction = UNNotificationAction(
            identifier: "STOP_WORKOUT", 
            title: "Stop",
            options: [.destructive]
        )
        
        let workoutCategory = UNNotificationCategory(
            identifier: self.workoutCategory,
            actions: [pauseAction, stopAction],
            intentIdentifiers: [],
            options: [.customDismissAction]
        )
        
        // Milestone notification actions
        let shareAction = UNNotificationAction(
            identifier: "SHARE_MILESTONE",
            title: "Share",
            options: [.foreground]
        )
        
        let milestoneCategory = UNNotificationCategory(
            identifier: self.milestoneCategory,
            actions: [shareAction],
            intentIdentifiers: [],
            options: []
        )
        
        // Alert notification category
        let alertCategory = UNNotificationCategory(
            identifier: self.alertCategory,
            actions: [],
            intentIdentifiers: [],
            options: [.customDismissAction]
        )
        
        await center.setNotificationCategories([
            workoutCategory, milestoneCategory, alertCategory
        ])
    }
    
    /**
     * Shows ongoing workout notification
     */
    func showWorkoutNotification(session: WorkoutSession, metrics: WorkoutMetrics? = nil) async {
        let content = UNMutableNotificationContent()
        
        // Configure notification content
        content.title = getWorkoutNotificationTitle(for: session.status)
        content.body = buildWorkoutNotificationBody(session: session, metrics: metrics)
        content.categoryIdentifier = workoutCategory
        content.sound = nil // Silent for ongoing notifications
        content.threadIdentifier = "workout-thread"
        
        // Add workout data to user info
        content.userInfo = [
            "workoutId": session.id,
            "status": session.status.name,
            "duration": session.totalDuration,
            "distance": session.totalDistance
        ]
        
        // Create request
        let request = UNNotificationRequest(
            identifier: workoutNotificationId,
            content: content,
            trigger: nil // Immediate delivery
        )
        
        do {
            try await UNUserNotificationCenter.current().add(request)
            print("Workout notification posted")
        } catch {
            print("Error posting workout notification: \(error)")
        }
    }
    
    /**
     * Shows milestone achievement notification
     */
    func showMilestoneNotification(milestone: WorkoutMilestone) async {
        let content = UNMutableNotificationContent()
        
        content.title = "🎉 Milestone Achieved!"
        content.body = milestone.message
        content.categoryIdentifier = milestoneCategory
        content.sound = UNNotificationSound.default
        content.badge = 1
        
        content.userInfo = [
            "milestoneType": milestone.type.rawValue,
            "milestoneValue": milestone.value
        ]
        
        let request = UNNotificationRequest(
            identifier: "\(milestoneNotificationId)-\(milestone.id)",
            content: content,
            trigger: nil
        )
        
        do {
            try await UNUserNotificationCenter.current().add(request)
            print("Milestone notification posted: \(milestone.message)")
        } catch {
            print("Error posting milestone notification: \(error)")
        }
    }
    
    /**
     * Shows workout alert notification
     */
    func showAlertNotification(alert: WorkoutAlert) async {
        let content = UNMutableNotificationContent()
        
        content.title = getAlertTitle(for: alert.type)
        content.body = alert.message
        content.categoryIdentifier = alertCategory
        
        // Configure sound and criticality based on severity
        switch alert.severity {
        case .critical:
            content.sound = UNNotificationSound.defaultCritical
            content.interruptionLevel = .critical
        case .warning:
            content.sound = UNNotificationSound.default
            content.interruptionLevel = .active
        case .info:
            content.sound = nil
            content.interruptionLevel = .passive
        }
        
        content.userInfo = [
            "alertType": alert.type.rawValue,
            "severity": alert.severity.rawValue,
            "workoutId": alert.workoutId ?? ""
        ]
        
        let request = UNNotificationRequest(
            identifier: "\(alertNotificationId)-\(alert.id)",
            content: content,
            trigger: nil
        )
        
        do {
            try await UNUserNotificationCenter.current().add(request)
            print("Alert notification posted: \(alert.message)")
        } catch {
            print("Error posting alert notification: \(error)")
        }
    }
    
    /**
     * Removes ongoing workout notification
     */
    func removeWorkoutNotification() async {
        UNUserNotificationCenter.current().removeDeliveredNotifications(
            withIdentifiers: [workoutNotificationId]
        )
        print("Workout notification removed")
    }
    
    /**
     * Schedules a delayed workout reminder
     */
    func scheduleWorkoutReminder(after timeInterval: TimeInterval, message: String) async {
        let content = UNMutableNotificationContent()
        content.title = "Workout Reminder"
        content.body = message
        content.sound = UNNotificationSound.default
        
        let trigger = UNTimeIntervalNotificationTrigger(
            timeInterval: timeInterval,
            repeats: false
        )
        
        let request = UNNotificationRequest(
            identifier: "workout-reminder-\(Date().timeIntervalSince1970)",
            content: content,
            trigger: trigger
        )
        
        do {
            try await UNUserNotificationCenter.current().add(request)
            print("Workout reminder scheduled for \(timeInterval) seconds")
        } catch {
            print("Error scheduling workout reminder: \(error)")
        }
    }
    
    // MARK: - Helper Methods
    
    private func getWorkoutNotificationTitle(for status: WorkoutStatus) -> String {
        switch status {
        case .active:
            return "🏃‍♂️ Workout Active"
        case .paused:
            return "⏸️ Workout Paused"
        case .completed:
            return "✅ Workout Completed"
        default:
            return "📱 Workout Tracking"
        }
    }
    
    private func buildWorkoutNotificationBody(session: WorkoutSession, metrics: WorkoutMetrics?) -> String {
        var components: [String] = []
        
        // Duration
        components.append("⏱️ \(formatDuration(session.totalDuration))")
        
        // Distance
        if session.totalDistance > 0 {
            components.append("📍 \(formatDistance(session.totalDistance))")
        }
        
        // Heart rate
        if let metrics = metrics, metrics.currentHeartRate > 0 {
            components.append("❤️ \(metrics.currentHeartRate) bpm")
        } else if session.averageHeartRate > 0 {
            components.append("❤️ \(session.averageHeartRate) bpm")
        }
        
        // Pace
        if let metrics = metrics {
            components.append("🏃‍♂️ \(metrics.currentPace)/km")
        }
        
        return components.joined(separator: " • ")
    }
    
    private func getAlertTitle(for type: WorkoutAlertType) -> String {
        switch type {
        case .heartRateHigh:
            return "⚠️ High Heart Rate"
        case .heartRateLow:
            return "⚠️ Low Heart Rate"
        case .gpsLost:
            return "📍 GPS Signal Lost"
        case .pebbleDisconnected:
            return "⌚ Pebble Disconnected"
        case .lowBattery:
            return "🔋 Low Battery"
        case .backgroundRestricted:
            return "🚫 Background Restricted"
        }
    }
    
    private func formatDuration(_ seconds: Int64) -> String {
        let hours = seconds / 3600
        let minutes = (seconds % 3600) / 60
        let secs = seconds % 60
        
        if hours > 0 {
            return String(format: "%d:%02d:%02d", hours, minutes, secs)
        } else {
            return String(format: "%d:%02d", minutes, secs)
        }
    }
    
    private func formatDistance(_ meters: Double) -> String {
        let km = meters / 1000.0
        if km >= 1.0 {
            return String(format: "%.2f km", km)
        } else {
            return String(format: "%.0f m", meters)
        }
    }
}

// MARK: - Supporting Types

struct WorkoutMetrics {
    let currentPace: String
    let avgPace: String
    let currentHeartRate: Int
    let avgHeartRate: Int
    let calories: Int
    let elevationGain: Double
}

struct WorkoutMilestone {
    let id: String
    let type: MilestoneType
    let value: Double
    let message: String
    
    enum MilestoneType: String, CaseIterable {
        case distance = "distance"
        case time = "time"
        case calories = "calories"
        case pace = "pace"
        case heartRate = "heartRate"
    }
}

struct WorkoutAlert {
    let id: String
    let type: WorkoutAlertType
    let severity: AlertSeverity
    let message: String
    let workoutId: String?
    
    enum AlertSeverity: String, CaseIterable {
        case info = "info"
        case warning = "warning"
        case critical = "critical"
    }
}

enum WorkoutAlertType: String, CaseIterable {
    case heartRateHigh = "heartRateHigh"
    case heartRateLow = "heartRateLow"
    case gpsLost = "gpsLost"
    case pebbleDisconnected = "pebbleDisconnected"
    case lowBattery = "lowBattery"
    case backgroundRestricted = "backgroundRestricted"
}

// MARK: - UNUserNotificationCenterDelegate

extension NotificationManager: UNUserNotificationCenterDelegate {
    
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let actionIdentifier = response.actionIdentifier
        let userInfo = response.notification.request.content.userInfo
        
        Task {
            await handleNotificationAction(actionIdentifier, userInfo: userInfo)
        }
        
        completionHandler()
    }
    
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        // Show notification even when app is in foreground
        completionHandler([.banner, .sound, .badge])
    }
    
    private func handleNotificationAction(_ actionIdentifier: String, userInfo: [AnyHashable: Any]) async {
        switch actionIdentifier {
        case "PAUSE_WORKOUT":
            await handlePauseWorkoutAction(userInfo)
        case "STOP_WORKOUT":
            await handleStopWorkoutAction(userInfo)
        case "SHARE_MILESTONE":
            await handleShareMilestoneAction(userInfo)
        default:
            print("Unknown notification action: \(actionIdentifier)")
        }
    }
    
    private func handlePauseWorkoutAction(_ userInfo: [AnyHashable: Any]) async {
        print("Pause workout action triggered from notification")
        // Implementation would call workout service to pause
    }
    
    private func handleStopWorkoutAction(_ userInfo: [AnyHashable: Any]) async {
        print("Stop workout action triggered from notification")
        // Implementation would call workout service to stop
    }
    
    private func handleShareMilestoneAction(_ userInfo: [AnyHashable: Any]) async {
        print("Share milestone action triggered from notification")
        // Implementation would open share sheet
    }
}
