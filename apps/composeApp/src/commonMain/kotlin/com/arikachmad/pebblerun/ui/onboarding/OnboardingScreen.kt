package com.arikachmad.pebblerun.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Onboarding screen implementing Material Design 3 (GUD-003)
 * Provides guided setup for Pebble connection and app permissions
 * Satisfies TASK-039 (Onboarding flow for Pebble connection setup)
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onOnboardingComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Handle onboarding completion
    LaunchedEffect(uiState.isCompleted) {
        if (uiState.isCompleted) {
            onOnboardingComplete()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Progress indicator
        OnboardingProgressIndicator(
            progress = uiState.progressPercentage,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Main content area
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            when (uiState.currentStep) {
                OnboardingStep.WELCOME -> WelcomeStep()
                OnboardingStep.PERMISSIONS -> PermissionsStep(
                    permissions = uiState.permissions,
                    onPermissionGranted = { permission, granted ->
                        viewModel.updatePermissionStatus(permission, granted)
                    }
                )
                OnboardingStep.PEBBLE_SETUP -> PebbleSetupStep()
                OnboardingStep.PEBBLE_CONNECTION -> PebbleConnectionStep(
                    isConnecting = uiState.isConnecting,
                    isConnected = uiState.pebbleConnected,
                    error = uiState.connectionError,
                    onStartConnection = viewModel::startPebbleConnection,
                    onRetryConnection = viewModel::retryConnection
                )
                OnboardingStep.COMPLETE -> CompleteStep()
            }
        }

        // Navigation buttons
        OnboardingNavigationButtons(
            uiState = uiState,
            onNext = viewModel::nextStep,
            onPrevious = viewModel::previousStep,
            onSkip = viewModel::skipOnboarding,
            onComplete = viewModel::completeOnboarding
        )
    }
}

@Composable
private fun OnboardingProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "${(progress * 100).toInt()}% Complete",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.End)
        )
    }
}

@Composable
private fun WelcomeStep(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App icon/logo placeholder
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(
                    MaterialTheme.colorScheme.primary,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.DirectionsRun,
                contentDescription = "PebbleRun Logo",
                modifier = Modifier.size(60.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Welcome to PebbleRun",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Transform your Pebble 2 HR into the ultimate workout companion. Track your runs with real-time heart rate monitoring, GPS pace tracking, and seamless data synchronization.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Feature highlights
        FeatureHighlight(
            icon = Icons.Default.FavoriteSharp,
            title = "Heart Rate Monitoring",
            description = "Real-time HR data from your Pebble 2 HR"
        )

        Spacer(modifier = Modifier.height(16.dp))

        FeatureHighlight(
            icon = Icons.Default.GpsFixed,
            title = "GPS Tracking",
            description = "Accurate pace and distance calculation"
        )

        Spacer(modifier = Modifier.height(16.dp))

        FeatureHighlight(
            icon = Icons.Default.CloudSync,
            title = "Data Synchronization",
            description = "Seamless data sync between devices"
        )
    }
}

@Composable
private fun FeatureHighlight(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PermissionsStep(
    permissions: Map<Permission, Boolean>,
    onPermissionGranted: (Permission, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Security,
            contentDescription = "Permissions",
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Grant Permissions",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "PebbleRun needs these permissions to provide the best workout tracking experience:",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Permission items
        Permission.values().forEach { permission ->
            PermissionItem(
                permission = permission,
                isGranted = permissions[permission] ?: false,
                onToggle = { granted ->
                    onPermissionGranted(permission, granted)
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PermissionItem(
    permission: Permission,
    isGranted: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) 
                MaterialTheme.colorScheme.primaryContainer 
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = permission.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = permission.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Switch(
                checked = isGranted,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
private fun PebbleSetupStep(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Watch,
            contentDescription = "Pebble Setup",
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Pebble Setup",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Before connecting, make sure your Pebble 2 HR is ready:",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Setup checklist
        SetupChecklistItem(
            number = "1",
            title = "Charge Your Pebble",
            description = "Ensure your Pebble 2 HR has sufficient battery"
        )

        Spacer(modifier = Modifier.height(16.dp))

        SetupChecklistItem(
            number = "2",
            title = "Enable Bluetooth",
            description = "Turn on Bluetooth on your mobile device"
        )

        Spacer(modifier = Modifier.height(16.dp))

        SetupChecklistItem(
            number = "3",
            title = "Keep Devices Close",
            description = "Keep your Pebble within 3 feet of your phone"
        )

        Spacer(modifier = Modifier.height(16.dp))

        SetupChecklistItem(
            number = "4",
            title = "Install PebbleRun Watchapp",
            description = "The watchapp will be installed automatically"
        )
    }
}

@Composable
private fun SetupChecklistItem(
    number: String,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    MaterialTheme.colorScheme.primary,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold
            )
        }
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PebbleConnectionStep(
    isConnecting: Boolean,
    isConnected: Boolean,
    error: String?,
    onStartConnection: () -> Unit,
    onRetryConnection: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Connection status icon
        val (icon, iconColor) = when {
            isConnecting -> Icons.Default.BluetoothSearching to MaterialTheme.colorScheme.primary
            isConnected -> Icons.Default.BluetoothConnected to Color.Green
            error != null -> Icons.Default.BluetoothDisabled to MaterialTheme.colorScheme.error
            else -> Icons.Default.Bluetooth to MaterialTheme.colorScheme.onSurfaceVariant
        }

        Icon(
            imageVector = icon,
            contentDescription = "Connection Status",
            modifier = Modifier.size(80.dp),
            tint = iconColor
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = when {
                isConnecting -> "Connecting..."
                isConnected -> "Connected!"
                error != null -> "Connection Failed"
                else -> "Connect Your Pebble"
            },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = when {
                isConnected -> Color.Green
                error != null -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = when {
                isConnecting -> "Searching for your Pebble 2 HR..."
                isConnected -> "Your Pebble 2 HR is successfully connected and ready for workouts!"
                error != null -> error
                else -> "Tap the button below to start connecting to your Pebble 2 HR."
            },
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Connection button
        if (!isConnected) {
            if (error != null) {
                Button(
                    onClick = onRetryConnection,
                    enabled = !isConnecting
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Retry Connection")
                }
            } else {
                Button(
                    onClick = onStartConnection,
                    enabled = !isConnecting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Connecting...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Connect to Pebble")
                    }
                }
            }
        }
    }
}

@Composable
private fun CompleteStep(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Complete",
            modifier = Modifier.size(80.dp),
            tint = Color.Green
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "All Set!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = Color.Green
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "PebbleRun is ready to track your workouts. Start your first run and experience real-time heart rate monitoring with GPS tracking!",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Quick Tip",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "For the best experience, keep your Pebble charged and within Bluetooth range during workouts.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun OnboardingNavigationButtons(
    uiState: OnboardingUiState,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSkip: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back/Skip button
        if (uiState.currentStep == OnboardingStep.WELCOME) {
            TextButton(onClick = onSkip) {
                Text("Skip")
            }
        } else {
            TextButton(
                onClick = onPrevious,
                enabled = uiState.currentStep != OnboardingStep.WELCOME
            ) {
                Text("Back")
            }
        }

        // Step indicator
        Text(
            text = "${uiState.currentStep.ordinal + 1} of ${OnboardingStep.values().size}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Next/Complete button
        if (uiState.currentStep == OnboardingStep.COMPLETE) {
            Button(onClick = onComplete) {
                Text("Get Started")
            }
        } else {
            Button(
                onClick = onNext,
                enabled = uiState.canProceed
            ) {
                Text("Next")
            }
        }
    }
}
