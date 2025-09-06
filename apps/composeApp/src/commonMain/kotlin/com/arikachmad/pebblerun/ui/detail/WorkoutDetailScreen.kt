package com.arikachmad.pebblerun.ui.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arikachmad.pebblerun.domain.entity.WorkoutSession
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Workout detail screen with comprehensive analytics and charts
 * Implements Material Design 3 (GUD-003) and provides detailed workout analysis
 * Satisfies TASK-037 (Workout detail view with charts and analytics)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(
    sessionId: String,
    viewModel: WorkoutDetailViewModel,
    onNavigateBack: () -> Unit,
    onWorkoutDeleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditNotesDialog by remember { mutableStateOf(false) }

    LaunchedEffect(sessionId) {
        viewModel.loadWorkoutDetails(sessionId)
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Top App Bar
        TopAppBar(
            title = { Text("Workout Details") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            actions = {
                IconButton(onClick = { showEditNotesDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Notes"
                    )
                }
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Workout",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        )

        // Content
        Box(modifier = Modifier.weight(1f)) {
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.workoutSession != null -> {
                    WorkoutDetailContent(
                        session = uiState.workoutSession!!,
                        analytics = uiState.analytics
                    )
                }
                uiState.error != null -> {
                    ErrorContent(
                        error = uiState.error!!,
                        onRetry = { viewModel.loadWorkoutDetails(sessionId) }
                    )
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Workout") },
            text = { Text("Are you sure you want to delete this workout? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (viewModel.deleteWorkout()) {
                            onWorkoutDeleted()
                        }
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit notes dialog
    if (showEditNotesDialog) {
        EditNotesDialog(
            currentNotes = uiState.workoutSession?.notes ?: "",
            onSave = { notes ->
                viewModel.updateNotes(notes)
                showEditNotesDialog = false
            },
            onDismiss = { showEditNotesDialog = false }
        )
    }
}

@Composable
private fun WorkoutDetailContent(
    session: WorkoutSession,
    analytics: WorkoutAnalytics?,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Workout Summary Header
        item {
            WorkoutSummaryCard(session = session)
        }

        // Key Metrics
        item {
            KeyMetricsCard(session = session, analytics = analytics)
        }

        // Heart Rate Analysis
        if (analytics?.heartRateStats?.samples ?: 0 > 0) {
            item {
                HeartRateAnalysisCard(
                    stats = analytics!!.heartRateStats,
                    zoneDistribution = analytics.hrZoneDistribution
                )
            }
        }

        // Pace Chart
        if (analytics?.paceAnalysis?.isNotEmpty() == true) {
            item {
                PaceChartCard(pacePoints = analytics.paceAnalysis)
            }
        }

        // Elevation Profile
        if (analytics?.elevationProfile?.isNotEmpty() == true) {
            item {
                ElevationChartCard(elevationPoints = analytics.elevationProfile)
            }
        }

        // Split Analysis
        if (analytics?.splitAnalysis?.isNotEmpty() == true) {
            item {
                SplitAnalysisCard(splits = analytics.splitAnalysis)
            }
        }

        // Performance Metrics
        if (analytics != null) {
            item {
                PerformanceMetricsCard(metrics = analytics.performanceMetrics)
            }
        }

        // Notes Section
        item {
            NotesCard(notes = session.notes)
        }
    }
}

@Composable
private fun WorkoutSummaryCard(
    session: WorkoutSession,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Workout Summary",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = formatWorkoutDate(session),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryMetric(
                    label = "Duration",
                    value = formatDuration(session.totalDuration),
                    color = MaterialTheme.colorScheme.primary
                )
                SummaryMetric(
                    label = "Distance",
                    value = "%.2f km".format(session.totalDistance / 1000),
                    color = MaterialTheme.colorScheme.secondary
                )
                SummaryMetric(
                    label = "Avg Pace",
                    value = formatPace(session.averagePace),
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

@Composable
private fun SummaryMetric(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun KeyMetricsCard(
    session: WorkoutSession,
    analytics: WorkoutAnalytics?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Key Metrics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    MetricChip(
                        label = "Avg HR",
                        value = "${session.averageHeartRate} BPM",
                        backgroundColor = MaterialTheme.colorScheme.errorContainer
                    )
                }
                item {
                    MetricChip(
                        label = "Max HR",
                        value = "${session.maxHeartRate} BPM",
                        backgroundColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                    )
                }
                item {
                    MetricChip(
                        label = "Calories",
                        value = "${session.calories}",
                        backgroundColor = MaterialTheme.colorScheme.primaryContainer
                    )
                }
                analytics?.performanceMetrics?.let { metrics ->
                    item {
                        MetricChip(
                            label = "Avg Speed",
                            value = "%.1f km/h".format(metrics.averageSpeed),
                            backgroundColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    }
                    if (metrics.totalElevationGain > 0) {
                        item {
                            MetricChip(
                                label = "Elevation",
                                value = "%.0f m".format(metrics.totalElevationGain),
                                backgroundColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricChip(
    label: String,
    value: String,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HeartRateAnalysisCard(
    stats: HeartRateStats,
    zoneDistribution: HRZoneDistribution,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Heart Rate Analysis",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // HR Statistics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                HRStatItem("Avg", stats.average, Color.Blue)
                HRStatItem("Min", stats.minimum, Color.Green)
                HRStatItem("Max", stats.maximum, Color.Red)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // HR Zone Distribution
            Text(
                text = "Zone Distribution",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            HRZoneChart(zoneDistribution = zoneDistribution)
        }
    }
}

@Composable
private fun HRStatItem(
    label: String,
    value: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "$value",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HRZoneChart(
    zoneDistribution: HRZoneDistribution,
    modifier: Modifier = Modifier
) {
    val zones = listOf(
        "Resting" to zoneDistribution.resting to Color(0xFF4CAF50),
        "Easy" to zoneDistribution.easy to Color(0xFF8BC34A),
        "Aerobic" to zoneDistribution.aerobic to Color(0xFFFFEB3B),
        "Tempo" to zoneDistribution.tempo to Color(0xFFFF9800),
        "Threshold" to zoneDistribution.threshold to Color(0xFFFF5722),
        "Maximum" to zoneDistribution.maximum to Color(0xFFF44336)
    )
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        zones.forEach { (name, percentage, color) ->
            if (percentage > 0) {
                HRZoneBar(
                    zoneName = name,
                    percentage = percentage,
                    color = color
                )
            }
        }
    }
}

@Composable
private fun HRZoneBar(
    zoneName: String,
    percentage: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = zoneName,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(60.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(16.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(8.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(percentage / 100f)
                    .background(color, RoundedCornerShape(8.dp))
            )
        }
        Text(
            text = "$percentage%",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(40.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun PaceChartCard(
    pacePoints: List<PacePoint>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Pace Analysis",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                drawPaceChart(pacePoints)
            }
        }
    }
}

@Composable
private fun ElevationChartCard(
    elevationPoints: List<ElevationPoint>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Elevation Profile",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                drawElevationChart(elevationPoints)
            }
        }
    }
}

@Composable
private fun SplitAnalysisCard(
    splits: List<SplitData>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Split Analysis",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            LazyColumn(
                modifier = Modifier.height(200.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(splits) { index, split ->
                    SplitRow(split = split, index = index)
                }
            }
        }
    }
}

@Composable
private fun SplitRow(
    split: SplitData,
    index: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (index % 2 == 0) Color.Transparent 
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                RoundedCornerShape(4.dp)
            )
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Km ${split.splitNumber}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(60.dp)
        )
        Text(
            text = formatPace(split.pace),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(60.dp),
            textAlign = TextAlign.Center
        )
        Text(
            text = "${split.averageHeartRate}",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(40.dp),
            textAlign = TextAlign.Center
        )
        Text(
            text = formatDuration(split.duration),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(60.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun PerformanceMetricsCard(
    metrics: PerformanceMetrics,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Performance Metrics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    MetricChip(
                        label = "Max Speed",
                        value = "%.1f km/h".format(metrics.maxSpeed),
                        backgroundColor = MaterialTheme.colorScheme.primaryContainer
                    )
                }
                item {
                    MetricChip(
                        label = "Cal/Min",
                        value = "%.1f".format(metrics.caloriesPerMinute),
                        backgroundColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun NotesCard(
    notes: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Notes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = if (notes.isBlank()) "No notes for this workout" else notes,
                style = MaterialTheme.typography.bodyMedium,
                color = if (notes.isBlank()) 
                    MaterialTheme.colorScheme.onSurfaceVariant 
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun EditNotesDialog(
    currentNotes: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var notes by remember { mutableStateOf(currentNotes) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Notes") },
        text = {
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Add workout notes...") },
                maxLines = 5
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(notes) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ErrorContent(
    error: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Error loading workout details",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

// Canvas drawing functions
private fun DrawScope.drawPaceChart(pacePoints: List<PacePoint>) {
    if (pacePoints.isEmpty()) return
    
    val minPace = pacePoints.minOfOrNull { it.pace } ?: 0.0
    val maxPace = pacePoints.maxOfOrNull { it.pace } ?: 0.0
    val paceRange = maxPace - minPace
    
    if (paceRange == 0.0) return
    
    val path = Path()
    val stepX = size.width / (pacePoints.size - 1)
    
    pacePoints.forEachIndexed { index, point ->
        val x = index * stepX
        val y = size.height - ((point.pace - minPace) / paceRange * size.height).toFloat()
        
        if (index == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    
    drawPath(
        path = path,
        color = Color.Blue,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
    )
}

private fun DrawScope.drawElevationChart(elevationPoints: List<ElevationPoint>) {
    if (elevationPoints.isEmpty()) return
    
    val minElevation = elevationPoints.minOfOrNull { it.elevation } ?: 0.0
    val maxElevation = elevationPoints.maxOfOrNull { it.elevation } ?: 0.0
    val elevationRange = maxElevation - minElevation
    
    if (elevationRange == 0.0) return
    
    val path = Path()
    val stepX = size.width / (elevationPoints.size - 1)
    
    elevationPoints.forEachIndexed { index, point ->
        val x = index * stepX
        val y = size.height - ((point.elevation - minElevation) / elevationRange * size.height).toFloat()
        
        if (index == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    
    // Fill area under the curve
    val fillPath = Path().apply {
        addPath(path)
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
    
    drawPath(
        path = fillPath,
        color = Color.Green.copy(alpha = 0.3f)
    )
    
    drawPath(
        path = path,
        color = Color.Green,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
    )
}

// Helper functions
private fun formatWorkoutDate(session: WorkoutSession): String {
    val dateTime = session.startTime.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${dateTime.date.dayOfMonth}/${dateTime.date.monthNumber}/${dateTime.date.year} at ${dateTime.time.hour}:${dateTime.time.minute.toString().padStart(2, '0')}"
}

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

private fun formatPace(paceSecondsPerKm: Double): String {
    val minutes = (paceSecondsPerKm / 60).toInt()
    val seconds = (paceSecondsPerKm % 60).toInt()
    return "%02d:%02d".format(minutes, seconds)
}
