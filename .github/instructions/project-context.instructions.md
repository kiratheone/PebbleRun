---
applyTo: "**"
---
# PebbleRun - Pebble 2 HR Companion App Project Context

## Project Overview
This is a Kotlin Multiplatform Mobile (KMP) project that creates a companion app for the Pebble 2 HR smartwatch. The project bridges the gap between the legacy Pebble 2 HR device and modern mobile phones to provide workout tracking capabilities.

## Core Purpose
- **Problem**: Pebble 2 HR is an old smartwatch with HR sensor but lacks support for modern fitness apps like Strava
- **Solution**: Companion mobile app (Android/iOS) + custom Pebble watchapp for workout tracking
- **Key Features**: Real-time HR, Pace, Duration tracking with background GPS and local data storage

## Technical Architecture

### Clean Architecture (Minimal)
```
PebbleRun/
├─ apps/                   # Platform-specific implementations
│  ├─ androidApp/          # Android Compose UI, ViewModels, DI, Foreground Service
│  ├─ iosApp/              # iOS SwiftUI, DI, Background Modes (Location + BLE)
│  └─ pebble-watchapp/     # Pebble C SDK watchapp (PebbleRun app)
│
├─ shared/                 # Kotlin Multiplatform shared code
│  ├─ domain/              # Pure Kotlin: Entities, UseCases, Repository interfaces
│  ├─ data/                # Repository implementations, Mappers, Infrastructure
│  ├─ bridge-pebble/       # expect/actual PebbleKit wrapper for BLE communication
│  ├─ bridge-location/     # expect/actual Location provider (GPS)
│  ├─ storage/             # SQLDelight schemas & DAO for local data persistence
│  ├─ proto/               # AppMessage protocol definitions & DTOs
│  └─ util/                # Common utilities (logger, formatter, Result types)
```

### Dependency Rules (STRICT)
- `apps/` → `data/`, `domain/`
- `data/` → `domain/`, `bridge-*/`, `storage/`, `proto/`, `util/`
- `domain/` → **NO DEPENDENCIES** (pure business logic)
- `bridge-*/`, `storage/`, `proto/`, `util/` → may depend on each other but not on `domain/` or `data/`

## Core Data Models
```kotlin
// Primary workout session entity
data class WorkoutSession(
    val startTime: Long,
    val endTime: Long?,
    val duration: Long,
    val distanceMeters: Double,
    val avgPace: String,           // Format: "mm:ss/km"
    val avgHR: Int,
    val gpsTrack: List<GeoPoint>,
    val hrSamples: List<HRSample>
)

data class GeoPoint(val lat: Double, val lon: Double, val timestamp: Long)
data class HRSample(val bpm: Int, val timestamp: Long)
```

## Communication Protocol
**AppMessage Keys** (Pebble ↔ Mobile):
- `0` = PACE (cstring "mm:ss/km")
- `1` = TIME (cstring "HH:MM:SS") 
- `2` = HR (uint16 BPM)
- `3` = CMD (uint8: 1=START, 2=STOP)

## Workflow
1. **Start**: User presses Start → mobile starts GPS & timer → sends Launch command → PebbleRun watchapp opens
2. **Active**: Every 1 second: Pebble sends HR → Mobile; Mobile sends Pace+Time → Pebble
3. **Stop**: User presses Stop → Mobile sends CMD=STOP → PebbleRun closes → returns to default watchface → saves session

## Key Technical Constraints
- **HR Sampling**: 1-second intervals during active sessions only (battery optimization)
- **Watchapp Type**: `watchapp` (not watchface) - auto-launched from mobile, auto-closes on stop
- **Background Services**: 
  - Android: Foreground Service (location type)
  - iOS: Background Modes (Location + BLE)
- **Data Storage**: Local only (SQLite via SQLDelight), no cloud sync in MVP

## Platform-Specific Notes
- **Android**: Uses PebbleKit Android SDK for BLE communication
- **iOS**: Uses PebbleKit iOS framework with bridging for SwiftUI
- **Pebble**: C SDK with Health API for HR sensor, AppMessage for communication

## Testing Strategy
- **Unit Tests**: Domain logic (pace calculation, HR averaging, session state)
- **Integration Tests**: Data repositories with in-memory SQLDelight
- **Platform Tests**: Android instrumented tests, iOS XCTests
- **Watchapp Tests**: Emulator validation + manual device testing

## Code Style Reminders
- Follow clean architecture dependency rules strictly
- Keep domain layer pure (no I/O, threading, logging)
- Use expect/actual pattern for platform-specific implementations
- Prefer composition over inheritance
- Write descriptive test names and comprehensive coverage for business logic


