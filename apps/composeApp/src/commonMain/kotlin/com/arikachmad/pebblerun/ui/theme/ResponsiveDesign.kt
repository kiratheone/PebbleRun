package com.arikachmad.pebblerun.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Responsive design utilities for different screen sizes
 * Implements responsive design patterns (TASK-040)
 * Satisfies GUD-003 (Material Design 3) and GUD-004 (Accessibility compliance)
 */

/**
 * Screen size categories based on Material Design breakpoints
 */
enum class WindowSizeClass {
    COMPACT,    // < 600dp width (phones in portrait)
    MEDIUM,     // 600-839dp width (tablets in portrait, phones in landscape)
    EXPANDED    // >= 840dp width (tablets in landscape, desktop)
}

/**
 * Window size data class containing width and height classifications
 */
data class WindowSize(
    val width: WindowSizeClass,
    val height: WindowSizeClass,
    val widthDp: Dp,
    val heightDp: Dp
) {
    val isCompact: Boolean
        get() = width == WindowSizeClass.COMPACT
    
    val isMedium: Boolean
        get() = width == WindowSizeClass.MEDIUM
    
    val isExpanded: Boolean
        get() = width == WindowSizeClass.EXPANDED
    
    val isLandscape: Boolean
        get() = widthDp > heightDp
}

/**
 * CompositionLocal for accessing window size throughout the composition
 */
val LocalWindowSize = compositionLocalOf<WindowSize> {
    error("WindowSize not provided")
}

/**
 * Provides window size classification based on current screen dimensions
 */
@Composable
fun ProvideWindowSize(
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    
    // TODO: Get actual window size from platform-specific implementation
    // For now, using default compact size (typical phone)
    val windowSize = WindowSize(
        width = WindowSizeClass.COMPACT,
        height = WindowSizeClass.COMPACT,
        widthDp = 360.dp, // Typical phone width
        heightDp = 640.dp  // Typical phone height
    )
    
    CompositionLocalProvider(
        LocalWindowSize provides windowSize,
        content = content
    )
}

/**
 * Responsive spacing values based on screen size
 */
object ResponsiveSpacing {
    @Composable
    fun small(): Dp {
        val windowSize = LocalWindowSize.current
        return when (windowSize.width) {
            WindowSizeClass.COMPACT -> 8.dp
            WindowSizeClass.MEDIUM -> 12.dp
            WindowSizeClass.EXPANDED -> 16.dp
        }
    }
    
    @Composable
    fun medium(): Dp {
        val windowSize = LocalWindowSize.current
        return when (windowSize.width) {
            WindowSizeClass.COMPACT -> 16.dp
            WindowSizeClass.MEDIUM -> 20.dp
            WindowSizeClass.EXPANDED -> 24.dp
        }
    }
    
    @Composable
    fun large(): Dp {
        val windowSize = LocalWindowSize.current
        return when (windowSize.width) {
            WindowSizeClass.COMPACT -> 24.dp
            WindowSizeClass.MEDIUM -> 32.dp
            WindowSizeClass.EXPANDED -> 40.dp
        }
    }
    
    @Composable
    fun extraLarge(): Dp {
        val windowSize = LocalWindowSize.current
        return when (windowSize.width) {
            WindowSizeClass.COMPACT -> 32.dp
            WindowSizeClass.MEDIUM -> 48.dp
            WindowSizeClass.EXPANDED -> 64.dp
        }
    }
}

/**
 * Responsive content width for optimal reading and interaction
 */
object ResponsiveLayout {
    @Composable
    fun maxContentWidth(): Dp {
        val windowSize = LocalWindowSize.current
        return when (windowSize.width) {
            WindowSizeClass.COMPACT -> Dp.Infinity // Use full width on phones
            WindowSizeClass.MEDIUM -> 600.dp
            WindowSizeClass.EXPANDED -> 840.dp
        }
    }
    
    @Composable
    fun shouldUseNavigationRail(): Boolean {
        val windowSize = LocalWindowSize.current
        return windowSize.width == WindowSizeClass.EXPANDED
    }
    
    @Composable
    fun shouldUseTwoPane(): Boolean {
        val windowSize = LocalWindowSize.current
        return windowSize.width != WindowSizeClass.COMPACT
    }
    
    @Composable
    fun cardColumns(): Int {
        val windowSize = LocalWindowSize.current
        return when (windowSize.width) {
            WindowSizeClass.COMPACT -> 1
            WindowSizeClass.MEDIUM -> 2
            WindowSizeClass.EXPANDED -> 3
        }
    }
}
