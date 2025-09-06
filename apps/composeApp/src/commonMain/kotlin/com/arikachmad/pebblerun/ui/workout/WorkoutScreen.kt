package com.arikachmad.pebblerun.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arikachmad.pebblerun.domain.entity.WorkoutStatus

/**
 * Main workout screen implementing Material Design 3 (GUD-003)
 * Provides Start/Stop functionality and real-time data display
 * Satisfies REQ-035 (Real-time data display) and TASK-034 (Main workout screen)
 */
@Composable
fun WorkoutScreen(
    viewModel: WorkoutViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Show error snackbar if present
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            // Error handling will be managed by parent composable
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top section - Workout status and connection info
        WorkoutStatusCard(
            isConnected = true, // TODO: Get from Pebble connection state
            currentSession = uiState.currentSession
        )

        // Middle section - Real-time data display
        RealTimeDataSection(
            heartRate = uiState.currentHeartRate,
            pace = uiState.currentPace,
            duration = uiState.workoutDuration,
            distance = uiState.totalDistance
        )

        // Bottom section - Control buttons
        WorkoutControlSection(
            uiState = uiState,
            onStartWorkout = viewModel::startWorkout,
            onPauseWorkout = viewModel::pauseWorkout,
            onResumeWorkout = viewModel::resumeWorkout,
            onStopWorkout = viewModel::stopWorkout
        )
    }

    // Loading overlay
    if (uiState.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun WorkoutStatusCard(
    isConnected: Boolean,
    currentSession: com.arikachmad.pebblerun.domain.entity.WorkoutSession?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Connection status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            if (isConnected) Color.Green else Color.Red
                        )
                )
                Text(
                    text = if (isConnected) "Pebble Connected" else "Pebble Disconnected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isConnected) Color.Green else Color.Red
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Workout status
            Text(
                text = when (currentSession?.status) {
                    WorkoutStatus.ACTIVE -> "Workout Active"
                    WorkoutStatus.PAUSED -> "Workout Paused"
                    WorkoutStatus.PENDING -> "Workout Starting..."
                    WorkoutStatus.COMPLETED -> "Workout Completed"
                    null -> "No Active Workout"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun RealTimeDataSection(
    heartRate: Int,
    pace: Double,
    duration: Long,
    distance: Double,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Live Data",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            // Top row - HR and Pace
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DataItem(
                    label = "Heart Rate",
                    value = if (heartRate > 0) "$heartRate" else "--",
                    unit = "BPM",
                    color = MaterialTheme.colorScheme.error
                )
                DataItem(
                    label = "Pace",
                    value = if (pace > 0) formatPace(pace) else "--:--",
                    unit = "min/km",
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Bottom row - Duration and Distance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DataItem(
                    label = "Duration",
                    value = formatDuration(duration),
                    unit = "",
                    color = MaterialTheme.colorScheme.secondary
                )
                DataItem(
                    label = "Distance",
                    value = if (distance > 0) "%.2f".format(distance / 1000) else "0.00",
                    unit = "km",
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

@Composable
private fun DataItem(
    label: String,
    value: String,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            fontSize = 32.sp
        )
        if (unit.isNotEmpty()) {
            Text(
                text = unit,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WorkoutControlSection(
    uiState: WorkoutUiState,
    onStartWorkout: () -> Unit,
    onPauseWorkout: () -> Unit,
    onResumeWorkout: () -> Unit,
    onStopWorkout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Primary action button (Start/Pause/Resume)
        when {
            uiState.canStartWorkout -> {
                FilledTonalButton(
                    onClick = onStartWorkout,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    enabled = !uiState.isLoading
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Start Workout",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Text(
                    text = "Start Workout",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            uiState.canPauseWorkout -> {
                FilledTonalButton(
                    onClick = onPauseWorkout,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    ),
                    enabled = !uiState.isLoading
                ) {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = "Pause Workout",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSecondary
                    )
                }
                Text(
                    text = "Pause",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            uiState.canResumeWorkout -> {
                FilledTonalButton(
                    onClick = onResumeWorkout,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    enabled = !uiState.isLoading
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Resume Workout",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Text(
                    text = "Resume",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Stop button (shown when workout is active or paused)
        if (uiState.canStopWorkout) {
            OutlinedButton(
                onClick = onStopWorkout,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                enabled = !uiState.isLoading
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop Workout",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Stop Workout",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

/**
 * Formats duration in seconds to HH:MM:SS format
 */
private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    
    return if (hours > 0) {
        "%02d:%02d:%02d".format(hours, minutes, secs)
    } else {
        "%02d:%02d".format(minutes, secs)
    }
}

/**
 * Formats pace in seconds per km to MM:SS format
 */
private fun formatPace(paceSecondsPerKm: Double): String {
    val minutes = (paceSecondsPerKm / 60).toInt()
    val seconds = (paceSecondsPerKm % 60).toInt()
    return "%02d:%02d".format(minutes, seconds)
}
