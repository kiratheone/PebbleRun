package com.arikachmad.pebblerun.shared.di

import com.arikachmad.pebblerun.shared.viewmodel.WorkoutViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Shared DI module for ViewModels and UI bridge components
 * Used by both Android and iOS platforms
 */
val sharedViewModelModule = module {
    // ViewModels
    viewModel { WorkoutViewModel() }
    
    // TODO: Add other ViewModels
    // viewModel { HistoryViewModel() }
    // viewModel { SettingsViewModel() }
    // viewModel { OnboardingViewModel() }
}

/**
 * Complete shared module that includes all dependencies
 */
val sharedAppModule = module {
    includes(sharedViewModelModule)
    
    // TODO: Include domain and data modules
    // includes(domainModule)
    // includes(dataModule)
}
