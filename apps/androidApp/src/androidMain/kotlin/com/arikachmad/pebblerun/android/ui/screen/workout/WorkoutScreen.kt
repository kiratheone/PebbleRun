package com.arikachmad.pebblerun.android.ui.screen.workout

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.semantics.*
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
            // Status header with animated color transition
            val statusColor by animateColorAsState(
                targetValue = if (uiState.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                animationSpec = tween(
                    durationMillis = 300,
                    easing = FastOutSlowInEasing
                ),
                label = "status_color"
            )
            
            Text(
                text = if (uiState.isActive) "Workout Active" else "Ready to Start",
                style = MaterialTheme.typography.headlineMedium,
                color = statusColor,
                modifier = Modifier.semantics {
                    contentDescription = if (uiState.isActive) {
                        "Workout is currently active and tracking your exercise"
                    } else {
                        "Ready to start a new workout session"
                    }
                    heading()
                }
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
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.semantics {
                            contentDescription = "Workout duration: ${uiState.duration}"
                            stateDescription = if (uiState.isActive) {
                                "Timer is running"
                            } else {
                                "Timer is stopped"
                            }
                            liveRegion()
                        }
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
        
        // Bottom section: Action button with animations
        val infiniteTransition = rememberInfiniteTransition(label = "button_animation")
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = if (uiState.isActive) 1.05f else 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 1000,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale_animation"
        )
        
        val buttonScale by animateFloatAsState(
            targetValue = if (uiState.isLoading) 0.9f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "button_scale"
        )
        
        FloatingActionButton(
            onClick = {
                if (uiState.isActive) {
                    viewModel.stopWorkout()
                } else {
                    viewModel.startWorkout()
                }
            },
            modifier = Modifier
                .size(80.dp)
                .scale(scale * buttonScale)
                .semantics {
                    contentDescription = if (uiState.isActive) {
                        "Stop workout session. Current duration: ${uiState.duration}"
                    } else {
                        "Start new workout session"
                    }
                    role = Role.Button
                    if (uiState.isLoading) {
                        stateDescription = "Starting workout, please wait"
                    }
                },
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
    // Animate card entrance
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }
    
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(
                durationMillis = 300,
                easing = FastOutSlowInEasing
            )
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = 300,
                easing = FastOutSlowInEasing
            )
        ),
        modifier = modifier
    ) {
        Card(
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
                
                // Animate value changes
                AnimatedContent(
                    targetState = value,
                    transitionSpec = {
                        if (targetState != initialState) {
                            slideInVertically { height -> -height } + fadeIn() togetherWith
                                    slideOutVertically { height -> height } + fadeOut()
                        } else {
                            EnterTransition.None togetherWith ExitTransition.None
                        }.using(
                            SizeTransform(clip = false)
                        )
                    },
                    label = "value_animation"
                ) { animatedValue ->
                    Text(
                        text = animatedValue,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics {
                            contentDescription = "$title: $animatedValue $unit"
                            if (title == "Heart Rate" && animatedValue != "--") {
                                stateDescription = when {
                                    animatedValue.toIntOrNull() != null -> {
                                        val hr = animatedValue.toInt()
                                        when {
                                            hr < 60 -> "Resting heart rate"
                                            hr < 100 -> "Normal heart rate"
                                            hr < 150 -> "Elevated heart rate"
                                            else -> "High intensity heart rate"
                                        }
                                    }
                                    else -> "Heart rate reading"
                                }
                            }
                        }
                    )
                }
                
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
