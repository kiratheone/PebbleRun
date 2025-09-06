package com.arikachmad.pebblerun.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.arikachmad.pebblerun.ui.theme.LocalWindowSize
import com.arikachmad.pebblerun.ui.theme.ResponsiveLayout

/**
 * Navigation component implementing responsive design (TASK-040)
 * Adapts navigation style based on screen size
 * Satisfies GUD-003 (Material Design 3) and responsive design patterns
 */

/**
 * Navigation destinations in the app
 */
enum class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    WORKOUT("workout", "Workout", Icons.Default.DirectionsRun),
    HISTORY("history", "History", Icons.Default.History),
    SETTINGS("settings", "Settings", Icons.Default.Settings);
    
    companion object {
        val bottomNavScreens = listOf(WORKOUT, HISTORY, SETTINGS)
    }
}

/**
 * Responsive navigation bar that adapts to screen size
 */
@Composable
fun ResponsiveNavigationBar(
    currentScreen: Screen,
    onScreenSelected: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val windowSize = LocalWindowSize.current
    
    if (ResponsiveLayout.shouldUseNavigationRail()) {
        // Use navigation rail for larger screens
        NavigationRail(
            modifier = modifier.fillMaxHeight()
        ) {
            Screen.bottomNavScreens.forEach { screen ->
                NavigationRailItem(
                    icon = {
                        Icon(
                            imageVector = screen.icon,
                            contentDescription = screen.title
                        )
                    },
                    label = { Text(screen.title) },
                    selected = currentScreen == screen,
                    onClick = { onScreenSelected(screen) }
                )
            }
        }
    } else {
        // Use bottom navigation for smaller screens
        NavigationBar(
            modifier = modifier.fillMaxWidth()
        ) {
            Screen.bottomNavScreens.forEach { screen ->
                NavigationBarItem(
                    icon = {
                        Icon(
                            imageVector = screen.icon,
                            contentDescription = screen.title
                        )
                    },
                    label = { Text(screen.title) },
                    selected = currentScreen == screen,
                    onClick = { onScreenSelected(screen) }
                )
            }
        }
    }
}

/**
 * Responsive app layout that adapts to different screen sizes
 */
@Composable
fun ResponsiveAppLayout(
    currentScreen: Screen,
    onScreenSelected: (Screen) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val windowSize = LocalWindowSize.current
    
    if (ResponsiveLayout.shouldUseNavigationRail()) {
        // Side navigation layout for larger screens
        Row(modifier = modifier.fillMaxSize()) {
            ResponsiveNavigationBar(
                currentScreen = currentScreen,
                onScreenSelected = onScreenSelected
            )
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                content()
            }
        }
    } else {
        // Bottom navigation layout for smaller screens
        Column(modifier = modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                content()
            }
            
            ResponsiveNavigationBar(
                currentScreen = currentScreen,
                onScreenSelected = onScreenSelected
            )
        }
    }
}
