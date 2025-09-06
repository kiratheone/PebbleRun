package com.arikachmad.pebblerun

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arikachmad.pebblerun.ui.navigation.*
import com.arikachmad.pebblerun.ui.theme.*
import com.arikachmad.pebblerun.ui.onboarding.*
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Main app composable implementing responsive design (TASK-040)
 * Integrates all UI screens with Material Design 3 (GUD-003)
 * Provides complete navigation flow for the PebbleRun app
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun App() {
    // App state management
    var currentScreen by remember { mutableStateOf(Screen.WORKOUT) }
    var showOnboarding by remember { mutableStateOf(true) } // Enable onboarding by default
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    
    // Apply responsive design wrapper
    ProvideWindowSize {
        MaterialTheme {
            // Snackbar host state
            val snackbarHostState = remember { SnackbarHostState() }
            
            // Show snackbar when message is set
            LaunchedEffect(snackbarMessage) {
                snackbarMessage?.let { message ->
                    snackbarHostState.showSnackbar(message)
                    snackbarMessage = null
                }
            }
            
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    if (showOnboarding) {
                        OnboardingFlow(
                            onOnboardingComplete = { showOnboarding = false }
                        )
                    } else {
                        MainAppFlow(
                            currentScreen = currentScreen,
                            onScreenSelected = { currentScreen = it },
                            onError = { error -> snackbarMessage = error }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MainAppFlow(
    currentScreen: Screen,
    onScreenSelected: (Screen) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    ResponsiveAppLayout(
        currentScreen = currentScreen,
        onScreenSelected = onScreenSelected,
        modifier = modifier
    ) {
        when (currentScreen) {
            Screen.WORKOUT -> {
                WorkoutContent()
            }
            Screen.HISTORY -> {
                HistoryContent()
            }
            Screen.SETTINGS -> {
                SettingsContent()
            }
        }
    }
}

@Composable
private fun WorkoutContent(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(ResponsiveSpacing.medium()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Workout Screen",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(ResponsiveSpacing.medium()))
        Text(
            text = "Pebble heart rate tracking and GPS workout features will be available here.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun HistoryContent(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(ResponsiveSpacing.medium()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Workout History",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(ResponsiveSpacing.medium()))
        Text(
            text = "Your completed workouts and analytics will be displayed here.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun SettingsContent(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(ResponsiveSpacing.medium()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(ResponsiveSpacing.medium()))
        Text(
            text = "App preferences and Pebble connection settings will be configured here.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun OnboardingFlow(
    onOnboardingComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = remember { OnboardingViewModel() }
    
    OnboardingScreen(
        viewModel = viewModel,
        onOnboardingComplete = onOnboardingComplete,
        modifier = modifier
    )
}