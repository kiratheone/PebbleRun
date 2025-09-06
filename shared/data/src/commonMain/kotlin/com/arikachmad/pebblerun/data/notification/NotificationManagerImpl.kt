package com.arikachmad.pebblerun.data.notification

import com.arikachmad.pebblerun.domain.entity.WorkoutSession
import com.arikachmad.pebblerun.domain.entity.WorkoutStatus
import com.arikachmad.pebblerun.domain.notification.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Implementation of NotificationManager for workout status updates.
 * Satisfies TASK-032 (Notification system for workout status updates).
 * Provides cross-platform notification management with platform-specific implementations.
 */
class NotificationManagerImpl(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : NotificationManager {

    // State flows
    private val _notificationSettings = MutableStateFlow(NotificationSettings())
    override val notificationSettings: StateFlow<NotificationSettings> = _notificationSettings.asStateFlow()

    private val _activeNotifications = MutableStateFlow<List<ActiveNotification>>(emptyList())
    override val activeNotifications: StateFlow<List<ActiveNotification>> = _activeNotifications.asStateFlow()

    private val _permissionStatus = MutableStateFlow(NotificationPermissionStatus.NOT_REQUESTED)
    override val permissionStatus: StateFlow<NotificationPermissionStatus> = _permissionStatus.asStateFlow()

    // Internal state
    private val scheduledNotifications = mutableMapOf<String, Job>()
    private val notificationHistory = mutableListOf<NotificationHistoryEntry>()
    private var lastProgressUpdate = Clock.System.now()

    // Constants
    companion object {
        const val WORKOUT_CHANNEL_ID = "workout_tracking"
        const val ALERT_CHANNEL_ID = "workout_alerts"
        const val MILESTONE_CHANNEL_ID = "workout_milestones"
        
        const val ONGOING_NOTIFICATION_ID = "workout_ongoing"
        const val PROGRESS_NOTIFICATION_ID = "workout_progress"
    }

    data class NotificationHistoryEntry(
        val notification: WorkoutNotification,
        val timestamp: Instant,
        val shown: Boolean,
        val dismissed: Boolean = false
    )

    override suspend fun requestNotificationPermissions(): Result<Boolean> {
        return try {
            // This would call platform-specific permission request
            val granted = requestPlatformNotificationPermissions()
            _permissionStatus.value = if (granted) {
                NotificationPermissionStatus.GRANTED
            } else {
                NotificationPermissionStatus.DENIED
            }
            Result.success(granted)
        } catch (e: Exception) {
            _permissionStatus.value = NotificationPermissionStatus.UNKNOWN
            Result.failure(NotificationException.PermissionDenied())
        }
    }

    override suspend fun showWorkoutStartedNotification(session: WorkoutSession) {
        if (!_notificationSettings.value.workoutStartEnabled) return

        val notification = WorkoutNotification(
            id = "workout_started_${session.id}",
            type = NotificationType.WORKOUT_STARTED,
            title = "Workout Started",
            content = "PebbleRun workout session active. Tap to view details.",
            priority = NotificationPriority.HIGH,
            actions = listOf(
                NotificationAction(
                    id = "pause",
                    title = "Pause",
                    description = "Pause the current workout",
                    action = { /* Platform-specific pause action */ }
                ),
                NotificationAction(
                    id = "stop",
                    title = "Stop",
                    description = "Stop the current workout",
                    destructive = true,
                    action = { /* Platform-specific stop action */ }
                )
            ),
            isOngoing = true,
            autoCancel = false
        )

        showNotification(notification)
    }

    override suspend fun showWorkoutPausedNotification(session: WorkoutSession) {
        if (!_notificationSettings.value.workoutStartEnabled) return

        val notification = WorkoutNotification(
            id = "workout_paused_${session.id}",
            type = NotificationType.WORKOUT_PAUSED,
            title = "Workout Paused",
            content = buildString {
                append("Duration: ${formatDuration(session.totalDuration)}")
                if (session.totalDistance > 0) {
                    append(" • Distance: ${formatDistance(session.totalDistance)}")
                }
            },
            priority = NotificationPriority.DEFAULT,
            actions = listOf(
                NotificationAction(
                    id = "resume",
                    title = "Resume",
                    description = "Resume the paused workout",
                    action = { /* Platform-specific resume action */ }
                ),
                NotificationAction(
                    id = "stop",
                    title = "Stop",
                    description = "Stop the workout",
                    destructive = true,
                    action = { /* Platform-specific stop action */ }
                )
            ),
            isOngoing = true,
            autoCancel = false
        )

        showNotification(notification)
    }

    override suspend fun showWorkoutResumedNotification(session: WorkoutSession) {
        val notification = WorkoutNotification(
            id = "workout_resumed_${session.id}",
            type = NotificationType.WORKOUT_RESUMED,
            title = "Workout Resumed",
            content = "Workout tracking has resumed.",
            priority = NotificationPriority.DEFAULT,
            autoCancel = true,
            soundEnabled = false // Don't make noise for resume
        )

        showNotification(notification)

        // Auto-dismiss after 3 seconds
        delay(3.seconds)
        dismissNotification(notification.id)
    }

    override suspend fun showWorkoutCompletedNotification(session: WorkoutSession) {
        if (!_notificationSettings.value.workoutCompleteEnabled) return

        // Dismiss ongoing notifications first
        dismissNotification(ONGOING_NOTIFICATION_ID)
        dismissNotification(PROGRESS_NOTIFICATION_ID)

        val notification = WorkoutNotification(
            id = "workout_completed_${session.id}",
            type = NotificationType.WORKOUT_COMPLETED,
            title = "Workout Completed! 🎉",
            content = buildWorkoutSummary(session),
            priority = NotificationPriority.HIGH,
            actions = listOf(
                NotificationAction(
                    id = "view_details",
                    title = "View Details",
                    description = "View complete workout summary",
                    action = { /* Platform-specific view action */ }
                ),
                NotificationAction(
                    id = "share",
                    title = "Share",
                    description = "Share workout results",
                    action = { /* Platform-specific share action */ }
                )
            ),
            autoCancel = true
        )

        showNotification(notification)
    }

    override suspend fun updateWorkoutProgressNotification(session: WorkoutSession) {
        if (!_notificationSettings.value.progressUpdatesEnabled) return
        if (session.status != WorkoutStatus.ACTIVE) return

        val now = Clock.System.now()
        val timeSinceLastUpdate = now.minus(lastProgressUpdate)
        val updateInterval = when (_notificationSettings.value.updateFrequency) {
            NotificationFrequency.MINIMAL -> 5.minutes
            NotificationFrequency.NORMAL -> 1.minutes
            NotificationFrequency.FREQUENT -> 30.seconds
        }

        if (timeSinceLastUpdate < updateInterval) return

        val notification = WorkoutNotification(
            id = PROGRESS_NOTIFICATION_ID,
            type = NotificationType.WORKOUT_PROGRESS,
            title = "Workout in Progress",
            content = buildProgressContent(session),
            priority = NotificationPriority.LOW,
            actions = listOf(
                NotificationAction(
                    id = "pause",
                    title = "Pause",
                    action = { /* Platform-specific pause action */ }
                ),
                NotificationAction(
                    id = "stop",
                    title = "Stop",
                    destructive = true,
                    action = { /* Platform-specific stop action */ }
                )
            ),
            isOngoing = true,
            autoCancel = false,
            soundEnabled = false,
            vibrationEnabled = false
        )

        showNotification(notification)
        lastProgressUpdate = now
    }

    override suspend fun showMilestoneNotification(milestone: WorkoutMilestone, session: WorkoutSession) {
        if (!_notificationSettings.value.milestonesEnabled) return

        val (title, content) = when (milestone) {
            is WorkoutMilestone.DistanceMilestone -> 
                "Distance Milestone! 🏃" to "You've completed ${milestone.distance} ${milestone.unit}!"
            is WorkoutMilestone.DurationMilestone -> 
                "Time Milestone! ⏱️" to "You've been working out for ${formatDuration(milestone.duration.inWholeSeconds)}!"
            is WorkoutMilestone.CaloriesMilestone -> 
                "Calorie Milestone! 🔥" to "You've burned ${milestone.calories} calories!"
            is WorkoutMilestone.HeartRateMilestone -> 
                "Heart Rate Milestone! ❤️" to "${milestone.type.capitalize()} heart rate: ${milestone.heartRate} bpm"
            is WorkoutMilestone.PaceMilestone -> 
                "Pace Milestone! 🏃‍♂️" to "${milestone.type.capitalize()} pace: ${formatPace(milestone.pace)}"
        }

        val notification = WorkoutNotification(
            id = "milestone_${milestone.hashCode()}",
            type = NotificationType.MILESTONE_ACHIEVED,
            title = title,
            content = content,
            priority = NotificationPriority.HIGH,
            autoCancel = true
        )

        showNotification(notification)

        // Auto-dismiss after 5 seconds
        delay(5.seconds)
        dismissNotification(notification.id)
    }

    override suspend fun showAlertNotification(alert: WorkoutAlert) {
        val shouldShow = when (alert) {
            is WorkoutAlert.LowBatteryAlert -> _notificationSettings.value.batteryAlertsEnabled
            is WorkoutAlert.CriticalBatteryAlert -> _notificationSettings.value.batteryAlertsEnabled
            is WorkoutAlert.PebbleDisconnectedAlert -> _notificationSettings.value.pebbleConnectionAlertsEnabled
            is WorkoutAlert.PebbleReconnectedAlert -> _notificationSettings.value.pebbleConnectionAlertsEnabled
            else -> true
        }

        if (!shouldShow) return

        val priority = when (alert.severity) {
            AlertSeverity.INFO -> NotificationPriority.LOW
            AlertSeverity.WARNING -> NotificationPriority.DEFAULT
            AlertSeverity.ERROR -> NotificationPriority.HIGH
            AlertSeverity.CRITICAL -> NotificationPriority.MAX
        }

        val actions = mutableListOf<NotificationAction>()
        when (alert) {
            is WorkoutAlert.LowBatteryAlert -> {
                actions.add(NotificationAction(
                    id = "enable_power_saving",
                    title = "Enable Power Saving",
                    action = { /* Enable power saving mode */ }
                ))
            }
            is WorkoutAlert.PebbleDisconnectedAlert -> {
                actions.add(NotificationAction(
                    id = "reconnect_pebble",
                    title = "Reconnect",
                    action = { /* Attempt to reconnect */ }
                ))
            }
            is WorkoutAlert.HeartRateAnomalyAlert -> {
                actions.add(NotificationAction(
                    id = "pause_workout",
                    title = "Pause Workout",
                    action = { /* Pause for safety */ }
                ))
            }
            else -> {
                actions.add(NotificationAction(
                    id = "dismiss",
                    title = "Dismiss",
                    action = { /* Dismiss alert */ }
                ))
            }
        }

        val notification = WorkoutNotification(
            id = "alert_${alert.hashCode()}",
            type = getNotificationTypeForAlert(alert),
            title = alert.title,
            content = alert.message,
            priority = priority,
            actions = actions,
            autoCancel = !alert.isActionRequired
        )

        showNotification(notification)
    }

    override suspend fun showHeartRateZoneNotification(zone: HeartRateZone, heartRate: Int) {
        if (!_notificationSettings.value.heartRateAlertsEnabled) return

        val notification = WorkoutNotification(
            id = "hr_zone_${zone.name}",
            type = NotificationType.HEART_RATE_ZONE,
            title = "Heart Rate Zone: ${zone.displayName}",
            content = "$heartRate bpm - ${zone.description}",
            priority = NotificationPriority.LOW,
            autoCancel = true,
            soundEnabled = false
        )

        showNotification(notification)

        // Auto-dismiss after 3 seconds
        delay(3.seconds)
        dismissNotification(notification.id)
    }

    override suspend fun dismissNotification(notificationId: String) {
        try {
            dismissPlatformNotification(notificationId)
            
            // Update active notifications
            val currentNotifications = _activeNotifications.value
            val updatedNotifications = currentNotifications.filterNot { it.id == notificationId }
            _activeNotifications.value = updatedNotifications
            
            // Cancel any scheduled notifications
            scheduledNotifications[notificationId]?.cancel()
            scheduledNotifications.remove(notificationId)
            
        } catch (e: Exception) {
            println("Failed to dismiss notification $notificationId: ${e.message}")
        }
    }

    override suspend fun dismissAllWorkoutNotifications() {
        try {
            val workoutNotifications = _activeNotifications.value.filter { 
                it.type in setOf(
                    NotificationType.WORKOUT_STARTED,
                    NotificationType.WORKOUT_PAUSED,
                    NotificationType.WORKOUT_RESUMED,
                    NotificationType.WORKOUT_PROGRESS,
                    NotificationType.MILESTONE_ACHIEVED
                )
            }
            
            workoutNotifications.forEach { notification ->
                dismissNotification(notification.id)
            }
            
        } catch (e: Exception) {
            println("Failed to dismiss workout notifications: ${e.message}")
        }
    }

    override suspend fun updateNotificationSettings(settings: NotificationSettings) {
        _notificationSettings.value = settings
        // Apply settings to platform notification channels if needed
        updatePlatformNotificationChannels(settings)
    }

    override suspend fun showWorkoutActionNotification(actions: List<NotificationAction>) {
        val notification = WorkoutNotification(
            id = "workout_actions",
            type = NotificationType.ACTION_REQUEST,
            title = "Workout Actions",
            content = "Choose an action for your workout",
            priority = NotificationPriority.HIGH,
            actions = actions,
            autoCancel = true
        )

        showNotification(notification)
    }

    override suspend fun scheduleNotification(notification: WorkoutNotification, delay: Duration) {
        val job = scope.launch {
            try {
                delay(delay)
                showNotification(notification)
            } catch (e: CancellationException) {
                // Notification was cancelled
            } catch (e: Exception) {
                println("Failed to show scheduled notification: ${e.message}")
            }
        }
        
        scheduledNotifications[notification.id] = job
    }

    override suspend fun cancelScheduledNotification(notificationId: String) {
        scheduledNotifications[notificationId]?.cancel()
        scheduledNotifications.remove(notificationId)
    }

    // Private helper methods

    private suspend fun showNotification(notification: WorkoutNotification) {
        try {
            // Check if we're in quiet hours
            if (_notificationSettings.value.isQuietTime && notification.priority != NotificationPriority.MAX) {
                return
            }

            // Check permission
            if (_permissionStatus.value != NotificationPermissionStatus.GRANTED) {
                println("Notification permission not granted, cannot show notification")
                return
            }

            // Show platform-specific notification
            showPlatformNotification(notification)

            // Update active notifications
            val activeNotification = ActiveNotification(
                id = notification.id,
                type = notification.type,
                title = notification.title,
                content = notification.content,
                timestamp = Clock.System.now(),
                priority = notification.priority,
                actions = notification.actions,
                isOngoing = notification.isOngoing,
                progress = notification.progress
            )

            val currentNotifications = _activeNotifications.value.filterNot { it.id == notification.id }
            _activeNotifications.value = currentNotifications + activeNotification

            // Add to history
            notificationHistory.add(
                NotificationHistoryEntry(
                    notification = notification,
                    timestamp = Clock.System.now(),
                    shown = true
                )
            )

            // Keep history size manageable
            if (notificationHistory.size > 100) {
                notificationHistory.removeAt(0)
            }

        } catch (e: Exception) {
            throw NotificationException.NotificationFailed(e)
        }
    }

    private fun buildWorkoutSummary(session: WorkoutSession): String {
        return buildString {
            append("Duration: ${formatDuration(session.totalDuration)}")
            if (session.totalDistance > 0) {
                append(" • Distance: ${formatDistance(session.totalDistance)}")
            }
            if (session.averageHeartRate > 0) {
                append(" • Avg HR: ${session.averageHeartRate} bpm")
            }
            if (session.calories > 0) {
                append(" • Calories: ${session.calories}")
            }
        }
    }

    private fun buildProgressContent(session: WorkoutSession): String {
        return buildString {
            append("${formatDuration(session.totalDuration)}")
            if (session.totalDistance > 0) {
                append(" • ${formatDistance(session.totalDistance)}")
            }
            if (session.averageHeartRate > 0) {
                append(" • ${session.averageHeartRate} bpm")
            }
            if (session.averagePace > 0) {
                append(" • ${formatPace(session.averagePace)}")
            }
        }
    }

    private fun getNotificationTypeForAlert(alert: WorkoutAlert): NotificationType {
        return when (alert) {
            is WorkoutAlert.PebbleDisconnectedAlert -> NotificationType.PEBBLE_DISCONNECTED
            is WorkoutAlert.PebbleReconnectedAlert -> NotificationType.PEBBLE_RECONNECTED
            is WorkoutAlert.LowBatteryAlert -> NotificationType.LOW_BATTERY_ALERT
            is WorkoutAlert.CriticalBatteryAlert -> NotificationType.LOW_BATTERY_ALERT
            is WorkoutAlert.GPSSignalLostAlert -> NotificationType.GPS_SIGNAL_LOST
            is WorkoutAlert.GPSSignalRestoredAlert -> NotificationType.GPS_SIGNAL_RESTORED
            else -> NotificationType.ERROR_ALERT
        }
    }

    private fun formatDuration(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%d:%02d", minutes, secs)
        }
    }

    private fun formatDistance(meters: Double): String {
        return if (meters >= 1000) {
            String.format("%.2f km", meters / 1000)
        } else {
            String.format("%.0f m", meters)
        }
    }

    private fun formatPace(secondsPerKm: Double): String {
        val minutes = (secondsPerKm / 60).toInt()
        val seconds = (secondsPerKm % 60).toInt()
        return String.format("%d:%02d/km", minutes, seconds)
    }

    // Platform-specific methods (to be implemented in platform modules)
    private suspend fun requestPlatformNotificationPermissions(): Boolean {
        // Platform-specific implementation
        return true
    }

    private suspend fun showPlatformNotification(notification: WorkoutNotification) {
        // Platform-specific implementation
        println("Showing notification: ${notification.title} - ${notification.content}")
    }

    private suspend fun dismissPlatformNotification(notificationId: String) {
        // Platform-specific implementation
        println("Dismissing notification: $notificationId")
    }

    private suspend fun updatePlatformNotificationChannels(settings: NotificationSettings) {
        // Platform-specific implementation
        println("Updating notification channels with settings: $settings")
    }
}
