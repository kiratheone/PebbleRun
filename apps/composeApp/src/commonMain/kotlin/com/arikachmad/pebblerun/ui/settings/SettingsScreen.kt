package com.arikachmad.pebblerun.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Settings screen implementing Material Design 3 (GUD-003)
 * Provides comprehensive user preferences and app configuration
 * Satisfies TASK-038 (Settings screen for user preferences)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDataClearDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Top App Bar
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }
        )

        // Settings Content
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Workout Settings Section
                item {
                    SettingsSection(
                        title = "Workout Settings",
                        icon = Icons.Default.FitnessCenter
                    ) {
                        WorkoutSettingsContent(
                            uiState = uiState,
                            onHRSamplingIntervalChange = viewModel::updateHRSamplingInterval,
                            onGPSAccuracyChange = viewModel::updateGPSAccuracy,
                            onBatteryOptimizationChange = viewModel::updateBatteryOptimization,
                            onAutoPauseChange = viewModel::updateAutoPause,
                            onAutoPauseThresholdChange = viewModel::updateAutoPauseThreshold
                        )
                    }
                }

                // Pebble Connection Section
                item {
                    SettingsSection(
                        title = "Pebble Connection",
                        icon = Icons.Default.Watch
                    ) {
                        PebbleConnectionContent(
                            uiState = uiState,
                            onTestConnection = viewModel::testPebbleConnection
                        )
                    }
                }

                // Notifications Section
                item {
                    SettingsSection(
                        title = "Notifications",
                        icon = Icons.Default.Notifications
                    ) {
                        NotificationSettingsContent(
                            settings = uiState.notificationSettings,
                            onSettingsChange = viewModel::updateNotificationSettings
                        )
                    }
                }

                // Display Settings Section
                item {
                    SettingsSection(
                        title = "Display",
                        icon = Icons.Default.DisplaySettings
                    ) {
                        DisplaySettingsContent(
                            displayUnits = uiState.displayUnits,
                            onDisplayUnitsChange = viewModel::updateDisplayUnits
                        )
                    }
                }

                // Data & Privacy Section
                item {
                    SettingsSection(
                        title = "Data & Privacy",
                        icon = Icons.Default.Security
                    ) {
                        DataPrivacyContent(
                            uiState = uiState,
                            onDataRetentionChange = viewModel::updateDataRetention,
                            onExportData = viewModel::exportWorkoutData,
                            onClearData = { showDataClearDialog = true }
                        )
                    }
                }

                // App Information Section
                item {
                    SettingsSection(
                        title = "App Information",
                        icon = Icons.Default.Info
                    ) {
                        AppInformationContent()
                    }
                }
            }
        }
    }

    // Data clear confirmation dialog
    if (showDataClearDialog) {
        AlertDialog(
            onDismissRequest = { showDataClearDialog = false },
            title = { Text("Clear All Data") },
            text = { 
                Text("Are you sure you want to clear all workout data? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllData()
                        showDataClearDialog = false
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDataClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Error handling
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            // Error will be handled by parent composable with SnackbarHost
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            content()
        }
    }
}

@Composable
private fun WorkoutSettingsContent(
    uiState: SettingsUiState,
    onHRSamplingIntervalChange: (HRSamplingInterval) -> Unit,
    onGPSAccuracyChange: (GPSAccuracy) -> Unit,
    onBatteryOptimizationChange: (Boolean) -> Unit,
    onAutoPauseChange: (Boolean) -> Unit,
    onAutoPauseThresholdChange: (Double) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // HR Sampling Interval
        DropdownSetting(
            label = "Heart Rate Sampling",
            value = uiState.heartRateSamplingInterval.displayName,
            options = HRSamplingInterval.values().map { it.displayName },
            onSelectionChange = { selected ->
                val interval = HRSamplingInterval.values().find { it.displayName == selected }
                interval?.let(onHRSamplingIntervalChange)
            }
        )

        // GPS Accuracy
        DropdownSetting(
            label = "GPS Accuracy",
            value = uiState.gpsAccuracy.displayName,
            options = GPSAccuracy.values().map { it.displayName },
            onSelectionChange = { selected ->
                val accuracy = GPSAccuracy.values().find { it.displayName == selected }
                accuracy?.let(onGPSAccuracyChange)
            }
        )

        // Battery Optimization
        SwitchSetting(
            label = "Battery Optimization",
            description = "Reduce power consumption during workouts",
            checked = uiState.batteryOptimizationEnabled,
            onCheckedChange = onBatteryOptimizationChange
        )

        // Auto-Pause
        SwitchSetting(
            label = "Auto-Pause",
            description = "Automatically pause when you stop moving",
            checked = uiState.autoPauseEnabled,
            onCheckedChange = onAutoPauseChange
        )

        // Auto-Pause Threshold (only if auto-pause is enabled)
        if (uiState.autoPauseEnabled) {
            SliderSetting(
                label = "Auto-Pause Threshold",
                value = uiState.autoPauseThreshold,
                valueRange = 0.1f..2.0f,
                steps = 18,
                unit = "km/h",
                onValueChange = { onAutoPauseThresholdChange(it.toDouble()) }
            )
        }
    }
}

@Composable
private fun PebbleConnectionContent(
    uiState: SettingsUiState,
    onTestConnection: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Connection Test Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Test Connection",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Verify Pebble device connection",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Button(
                onClick = onTestConnection,
                enabled = !uiState.isTestingConnection
            ) {
                if (uiState.isTestingConnection) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Test")
                }
            }
        }

        // Connection Test Result
        uiState.lastConnectionTest?.let { result ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (result.contains("successful")) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = result,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun NotificationSettingsContent(
    settings: NotificationSettings,
    onSettingsChange: (NotificationSettings) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SwitchSetting(
            label = "Workout Started",
            description = "Notify when workout begins",
            checked = settings.workoutStarted,
            onCheckedChange = { onSettingsChange(settings.copy(workoutStarted = it)) }
        )
        
        SwitchSetting(
            label = "Workout Paused",
            description = "Notify when workout is paused",
            checked = settings.workoutPaused,
            onCheckedChange = { onSettingsChange(settings.copy(workoutPaused = it)) }
        )
        
        SwitchSetting(
            label = "Workout Completed",
            description = "Notify when workout finishes",
            checked = settings.workoutCompleted,
            onCheckedChange = { onSettingsChange(settings.copy(workoutCompleted = it)) }
        )
        
        SwitchSetting(
            label = "Milestone Alerts",
            description = "Notify for distance milestones",
            checked = settings.milestoneAlerts,
            onCheckedChange = { onSettingsChange(settings.copy(milestoneAlerts = it)) }
        )
        
        SwitchSetting(
            label = "Pebble Disconnected",
            description = "Notify when Pebble disconnects",
            checked = settings.pebbleDisconnected,
            onCheckedChange = { onSettingsChange(settings.copy(pebbleDisconnected = it)) }
        )
        
        SwitchSetting(
            label = "Low Battery",
            description = "Notify when device battery is low",
            checked = settings.lowBattery,
            onCheckedChange = { onSettingsChange(settings.copy(lowBattery = it)) }
        )
    }
}

@Composable
private fun DisplaySettingsContent(
    displayUnits: DisplayUnits,
    onDisplayUnitsChange: (DisplayUnits) -> Unit
) {
    DropdownSetting(
        label = "Units",
        value = displayUnits.displayName,
        options = DisplayUnits.values().map { it.displayName },
        onSelectionChange = { selected ->
            val units = DisplayUnits.values().find { it.displayName == selected }
            units?.let(onDisplayUnitsChange)
        }
    )
}

@Composable
private fun DataPrivacyContent(
    uiState: SettingsUiState,
    onDataRetentionChange: (DataRetentionPeriod) -> Unit,
    onExportData: () -> Unit,
    onClearData: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Data Retention
        DropdownSetting(
            label = "Data Retention",
            value = uiState.dataRetentionPeriod.displayName,
            options = DataRetentionPeriod.values().map { it.displayName },
            onSelectionChange = { selected ->
                val period = DataRetentionPeriod.values().find { it.displayName == selected }
                period?.let(onDataRetentionChange)
            }
        )

        // Export Data
        ActionSetting(
            label = "Export Data",
            description = "Export all workout data",
            actionText = if (uiState.isExporting) "Exporting..." else "Export",
            isLoading = uiState.isExporting,
            onAction = onExportData
        )

        // Clear All Data
        ActionSetting(
            label = "Clear All Data",
            description = "Permanently delete all workout data",
            actionText = "Clear",
            isDestructive = true,
            onAction = onClearData
        )

        // Export Result
        uiState.lastExportResult?.let { result ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    text = result,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun AppInformationContent() {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        InfoItem(label = "Version", value = "1.0.0")
        InfoItem(label = "Build", value = "2025-09-06")
        InfoItem(label = "Platform", value = "Kotlin Multiplatform")
        InfoItem(label = "Target", value = "Pebble 2 HR")
    }
}

// Reusable Setting Components

@Composable
private fun SwitchSetting(
    label: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSetting(
    label: String,
    value: String,
    options: List<String>,
    onSelectionChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = { },
                readOnly = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onSelectionChange(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SliderSetting(
    label: String,
    value: Double,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    unit: String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "%.1f %s".format(value, unit),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Slider(
            value = value.toFloat(),
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )
    }
}

@Composable
private fun ActionSetting(
    label: String,
    description: String,
    actionText: String,
    isLoading: Boolean = false,
    isDestructive: Boolean = false,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Button(
            onClick = onAction,
            enabled = !isLoading,
            colors = if (isDestructive) {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            } else {
                ButtonDefaults.buttonColors()
            }
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text(actionText)
            }
        }
    }
}

@Composable
private fun InfoItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
