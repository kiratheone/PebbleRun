package com.arikachmad.pebblerun.domain.notification

import com.arikachmad.pebblerun.domain.entity.WorkoutSession
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Instant

/**
 * Notification system for workout status updates and alerts.
 * Satisfies TASK-032 (Notification system for workout status updates).
 * Provides cross-platform notification management for workout tracking events.
 */
interface NotificationManager {
    
    /** Current notification settings */
    val notificationSettings: StateFlow<NotificationSettings>
    
    /** Active notifications */
    val activeNotifications: StateFlow<List<ActiveNotification>>
    
    /** Notification permission status */
    val permissionStatus: StateFlow<NotificationPermissionStatus>
    
    /**
     * Requests notification permissions from the user
     * @return Result indicating if permission was granted
     */
    suspend fun requestNotificationPermissions(): Result<Boolean>
    
    /**
     * Shows workout start notification
     * @param session The workout session that started
     */
    suspend fun showWorkoutStartedNotification(session: WorkoutSession)
    
    /**
     * Shows workout pause notification
     * @param session The paused workout session
     */
    suspend fun showWorkoutPausedNotification(session: WorkoutSession)
    
    /**
     * Shows workout resume notification
     * @param session The resumed workout session
     */
    suspend fun showWorkoutResumedNotification(session: WorkoutSession)
    
    /**
     * Shows workout completion notification
     * @param session The completed workout session
     */
    suspend fun showWorkoutCompletedNotification(session: WorkoutSession)
    
    /**
     * Updates ongoing workout notification with current data
     * @param session Current workout session
     */
    suspend fun updateWorkoutProgressNotification(session: WorkoutSession)
    
    /**
     * Shows milestone achievement notification
     * @param milestone The achieved milestone
     * @param session Current workout session
     */
    suspend fun showMilestoneNotification(milestone: WorkoutMilestone, session: WorkoutSession)
    
    /**
     * Shows alert notification (low battery, Pebble disconnection, etc.)
     * @param alert The alert to show
     */
    suspend fun showAlertNotification(alert: WorkoutAlert)
    
    /**
     * Shows heart rate zone notification
     * @param zone Current heart rate zone
     * @param heartRate Current heart rate
     */
    suspend fun showHeartRateZoneNotification(zone: HeartRateZone, heartRate: Int)
    
    /**
     * Dismisses a specific notification
     * @param notificationId The notification to dismiss
     */
    suspend fun dismissNotification(notificationId: String)
    
    /**
     * Dismisses all workout-related notifications
     */
    suspend fun dismissAllWorkoutNotifications()
    
    /**
     * Updates notification settings
     * @param settings New notification settings
     */
    suspend fun updateNotificationSettings(settings: NotificationSettings)
    
    /**
     * Creates a workout action notification with interactive buttons
     * @param actions List of actions to show
     */
    suspend fun showWorkoutActionNotification(actions: List<NotificationAction>)
    
    /**
     * Schedules a delayed notification
     * @param notification The notification to schedule
     * @param delay When to show the notification
     */
    suspend fun scheduleNotification(notification: WorkoutNotification, delay: kotlin.time.Duration)
    
    /**
     * Cancels a scheduled notification
     * @param notificationId The scheduled notification to cancel
     */
    suspend fun cancelScheduledNotification(notificationId: String)
}

/**
 * Notification settings and preferences.
 */
data class NotificationSettings(
    val workoutStartEnabled: Boolean = true,
    val workoutCompleteEnabled: Boolean = true,
    val progressUpdatesEnabled: Boolean = true,
    val milestonesEnabled: Boolean = true,
    val heartRateAlertsEnabled: Boolean = true,
    val batteryAlertsEnabled: Boolean = true,
    val pebbleConnectionAlertsEnabled: Boolean = true,
    val updateFrequency: NotificationFrequency = NotificationFrequency.NORMAL,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val showOnLockScreen: Boolean = true,
    val priority: NotificationPriority = NotificationPriority.DEFAULT,
    val quietHours: QuietHours? = null
) {
    val isQuietTime: Boolean
        get() = quietHours?.isCurrentlyQuiet() ?: false
}

enum class NotificationFrequency(val intervalSeconds: Long) {
    MINIMAL(300), // 5 minutes
    NORMAL(60),   // 1 minute
    FREQUENT(30)  // 30 seconds
}

enum class NotificationPriority {
    LOW, DEFAULT, HIGH, MAX
}

data class QuietHours(
    val startHour: Int, // 0-23
    val startMinute: Int, // 0-59
    val endHour: Int, // 0-23
    val endMinute: Int, // 0-59
    val daysOfWeek: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7) // 1=Monday, 7=Sunday
) {
    fun isCurrentlyQuiet(): Boolean {
        // Implementation would check current time against quiet hours
        // This is a simplified version
        return false
    }
}

/**
 * Permission status for notifications.
 */
enum class NotificationPermissionStatus {
    GRANTED,
    DENIED,
    NOT_REQUESTED,
    RESTRICTED,
    UNKNOWN
}

/**
 * Active notification information.
 */
data class ActiveNotification(
    val id: String,
    val type: NotificationType,
    val title: String,
    val content: String,
    val timestamp: Instant,
    val priority: NotificationPriority,
    val actions: List<NotificationAction> = emptyList(),
    val isOngoing: Boolean = false,
    val progress: NotificationProgress? = null
)

/**
 * Types of workout notifications.
 */
enum class NotificationType {
    WORKOUT_STARTED,
    WORKOUT_PAUSED,
    WORKOUT_RESUMED,
    WORKOUT_COMPLETED,
    WORKOUT_PROGRESS,
    MILESTONE_ACHIEVED,
    HEART_RATE_ZONE,
    LOW_BATTERY_ALERT,
    PEBBLE_DISCONNECTED,
    PEBBLE_RECONNECTED,
    GPS_SIGNAL_LOST,
    GPS_SIGNAL_RESTORED,
    ERROR_ALERT,
    ACTION_REQUEST
}

/**
 * Workout milestone achievements.
 */
sealed class WorkoutMilestone {
    data class DistanceMilestone(val distance: Double, val unit: String = "km") : WorkoutMilestone()
    data class DurationMilestone(val duration: kotlin.time.Duration) : WorkoutMilestone()
    data class CaloriesMilestone(val calories: Int) : WorkoutMilestone()
    data class HeartRateMilestone(val heartRate: Int, val type: String) : WorkoutMilestone() // "max", "average", etc.
    data class PaceMilestone(val pace: Double, val type: String) : WorkoutMilestone() // "best", "target", etc.
}

/**
 * Workout alerts for important events.
 */
sealed class WorkoutAlert(
    val title: String,
    val message: String,
    val severity: AlertSeverity,
    val isActionRequired: Boolean = false
) {
    data class LowBatteryAlert(val batteryLevel: Double) : WorkoutAlert(
        title = "Low Battery",
        message = "Battery at ${batteryLevel.toInt()}%. Consider enabling power saving mode.",
        severity = AlertSeverity.WARNING,
        isActionRequired = true
    )
    
    data class CriticalBatteryAlert(val batteryLevel: Double) : WorkoutAlert(
        title = "Critical Battery",
        message = "Battery at ${batteryLevel.toInt()}%. Workout may be interrupted.",
        severity = AlertSeverity.CRITICAL,
        isActionRequired = true
    )
    
    object PebbleDisconnectedAlert : WorkoutAlert(
        title = "Pebble Disconnected",
        message = "Heart rate monitoring paused. Check Pebble connection.",
        severity = AlertSeverity.WARNING,
        isActionRequired = true
    )
    
    object PebbleReconnectedAlert : WorkoutAlert(
        title = "Pebble Reconnected",
        message = "Heart rate monitoring resumed.",
        severity = AlertSeverity.INFO
    )
    
    object GPSSignalLostAlert : WorkoutAlert(
        title = "GPS Signal Lost",
        message = "Location tracking paused. Move to an open area.",
        severity = AlertSeverity.WARNING,
        isActionRequired = true
    )
    
    object GPSSignalRestoredAlert : WorkoutAlert(
        title = "GPS Signal Restored",
        message = "Location tracking resumed.",
        severity = AlertSeverity.INFO
    )
    
    data class HeartRateAnomalyAlert(val heartRate: Int, val threshold: Int) : WorkoutAlert(
        title = "Heart Rate Alert",
        message = "Heart rate is ${heartRate} bpm, above safe threshold of ${threshold} bpm.",
        severity = AlertSeverity.WARNING,
        isActionRequired = true
    )
    
    data class WorkoutErrorAlert(val error: String) : WorkoutAlert(
        title = "Workout Error",
        message = error,
        severity = AlertSeverity.ERROR,
        isActionRequired = true
    )
}

enum class AlertSeverity {
    INFO, WARNING, ERROR, CRITICAL
}

/**
 * Heart rate training zones.
 */
enum class HeartRateZone(val displayName: String, val description: String, val color: String) {
    RESTING("Resting", "Very light activity", "#808080"),
    FAT_BURN("Fat Burn", "Light activity, fat burning", "#00FF00"),
    AEROBIC("Aerobic", "Moderate activity, aerobic base", "#FFFF00"),
    ANAEROBIC("Anaerobic", "Hard activity, lactate threshold", "#FFA500"),
    NEUROMUSCULAR("Neuromuscular", "Maximum effort", "#FF0000")
}

/**
 * Interactive notification actions.
 */
data class NotificationAction(
    val id: String,
    val title: String,
    val description: String? = null,
    val icon: String? = null,
    val destructive: Boolean = false,
    val authenticationRequired: Boolean = false,
    val action: suspend () -> Unit
)

/**
 * Progress information for ongoing notifications.
 */
data class NotificationProgress(
    val current: Int,
    val max: Int,
    val isIndeterminate: Boolean = false
)

/**
 * Workout notification data class.
 */
data class WorkoutNotification(
    val id: String,
    val type: NotificationType,
    val title: String,
    val content: String,
    val priority: NotificationPriority = NotificationPriority.DEFAULT,
    val actions: List<NotificationAction> = emptyList(),
    val isOngoing: Boolean = false,
    val progress: NotificationProgress? = null,
    val autoCancel: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Exception types for notification system.
 */
sealed class NotificationException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class PermissionDenied : NotificationException("Notification permission denied")
    class NotificationFailed(cause: Throwable) : NotificationException("Failed to show notification", cause)
    class UnsupportedNotificationType(type: NotificationType) : 
        NotificationException("Notification type not supported: $type")
    class SchedulingFailed(cause: Throwable) : NotificationException("Failed to schedule notification", cause)
}
