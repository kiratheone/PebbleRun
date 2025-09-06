package com.arikachmad.pebblerun.android.ui.screen.workout

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arikachmad.pebblerun.android.ui.theme.PebbleRunTheme
import com.arikachmad.pebblerun.shared.viewmodel.WorkoutViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * Android-specific workout screen using Jetpack Compose
 * Consumes shared WorkoutViewModel from composeApp module
 * Implements Material Design 3 patterns (GUD-001)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    viewModel: WorkoutViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    
    // Handle events
    LaunchedEffect(events) {
        events?.let { event ->
            // Handle platform-specific actions based on events
            // TODO: Show notifications, start services, etc.
            viewModel.clearEvent()
        }
    }
    
    // Handle errors
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            // TODO: Show error snackbar
            viewModel.clearError()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top section: Status and data
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status header
            Text(
                text = if (uiState.isActive) "Workout Active" else "Ready to Start",
                style = MaterialTheme.typography.headlineMedium,
                color = if (uiState.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            
            // Duration display
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Duration",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = uiState.duration,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
        
        // Middle section: Workout metrics
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Heart Rate
            MetricCard(
                title = "Heart Rate",
                value = if (uiState.currentHeartRate > 0) "${uiState.currentHeartRate}" else "--",
                unit = "BPM",
                modifier = Modifier.weight(1f)
            )
            
            // Pace
            MetricCard(
                title = "Pace",
                value = uiState.currentPace,
                unit = "",
                modifier = Modifier.weight(1f)
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Distance
            MetricCard(
                title = "Distance",
                value = String.format("%.2f", uiState.distanceMeters / 1000),
                unit = "KM",
                modifier = Modifier.weight(1f)
            )
            
            // Placeholder for future metric
            MetricCard(
                title = "Calories",
                value = "--",
                unit = "KCAL",
                modifier = Modifier.weight(1f)
            )
        }
        
        // Bottom section: Action button
        FloatingActionButton(
            onClick = {
                if (uiState.isActive) {
                    viewModel.stopWorkout()
                } else {
                    viewModel.startWorkout()
                }
            },
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
            containerColor = if (uiState.isActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 3.dp
                )
            } else {
                Icon(
                    imageVector = if (uiState.isActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (uiState.isActive) "Stop Workout" else "Start Workout",
                    modifier = Modifier.size(36.dp),
                    tint = if (uiState.isActive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            if (unit.isNotEmpty()) {
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WorkoutScreenPreview() {
    PebbleRunTheme {
        // Preview with mock data - actual ViewModel will be injected
        Surface {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Workout Active",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Duration",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = "00:15:23",
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                FloatingActionButton(
                    onClick = { },
                    modifier = Modifier.size(80.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop Workout",
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}
