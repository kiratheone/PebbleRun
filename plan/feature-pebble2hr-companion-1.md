---
goal: Implementation Plan for Pebble 2 HR Companion App with Clean Architecture
version: 1.0
date_created: 2025-09-05
last_updated: 2025-09-05
owner: Development Team
status: 'In Progress'
tags: ['feature', 'architecture', 'pebble', 'fitness', 'kmp']
---

# Implementation Plan for Pebble 2 HR Companion App

![Status: In Progress](https://img.shields.io/badge/status-In%20Progress-yellow)

A comprehensive implementation plan to transform the existing Kotlin Multiplatform Mobile project into a Pebble 2 HR companion app for workout tracking with HR monitoring, GPS tracking, and real-time data synchronization.

## 1. Requirements & Constraints

### Functional Requirements
- **REQ-001**: Real-time HR data collection from Pebble 2 HR device via AppMessage protocol
- **REQ-002**: GPS-based pace and distance calculation on mobile device
- **REQ-003**: Auto-launch PebbleRun watchapp when workout session starts
- **REQ-004**: Background tracking capability for long workout sessions
- **REQ-005**: Local storage of workout data (summary + detailed logs)
- **REQ-006**: Real-time data synchronization (Pebble → Mobile: HR, Mobile → Pebble: Pace/Duration)

### Technical Requirements
- **TEC-001**: Clean Architecture with clear separation of concerns
- **TEC-002**: Kotlin Multiplatform shared business logic
- **TEC-003**: Platform-specific implementations for PebbleKit and Location services
- **TEC-004**: SQLDelight for cross-platform local data storage
- **TEC-005**: Compose Multiplatform UI with reactive state management

### Security Requirements
- **SEC-001**: Secure local data storage with encryption
- **SEC-002**: Privacy-compliant location data handling
- **SEC-003**: Bluetooth communication security for Pebble connection

### Performance Constraints
- **CON-001**: Battery optimization - HR sampling only during active sessions
- **CON-002**: Memory efficiency for long workout sessions
- **CON-003**: 1-second update frequency for real-time data
- **CON-004**: Graceful handling of Pebble disconnections

### Platform Guidelines
- **GUD-001**: Follow Android Foreground Service best practices
- **GUD-002**: iOS Background Modes compliance (Location + BLE)
- **GUD-003**: Material Design 3 for Android, Native iOS design patterns
- **GUD-004**: Accessibility compliance for both platforms

### Architecture Patterns
- **PAT-001**: Domain-driven design with use cases
- **PAT-002**: Repository pattern for data access
- **PAT-003**: MVVM pattern for UI layer
- **PAT-004**: Dependency injection with platform-specific modules

## 2. Implementation Steps

### Implementation Phase 1: Project Architecture & Foundation

- GOAL-001: Establish clean architecture foundation and project structure

| Task | Description | Completed | Date |
|------|-------------|-----------|------|
| TASK-001 | Restructure project to match PRD folder structure with shared modules | ✅ | 2025-09-05 |
| TASK-002 | Configure SQLDelight for cross-platform database management | ✅ | 2025-09-05 |
| TASK-003 | Set up dependency injection framework (Koin) for shared and platform modules | ✅ | 2025-09-05 |
| TASK-004 | Create domain entities (WorkoutSession, GeoPoint, HRSample) | ✅ | 2025-09-05 |
| TASK-005 | Define repository interfaces in domain layer | ✅ | 2025-09-05 |
| TASK-006 | Set up logging framework and error handling utilities | ✅ | 2025-09-05 |
| TASK-007 | Configure build scripts for new module structure | ✅ | 2025-09-05 |

### Implementation Phase 2: Core Domain Logic

- GOAL-002: Implement business logic and use cases for workout management

| Task | Description | Completed | Date |
|------|-------------|-----------|------|
| TASK-008 | Create WorkoutSession entity with state management | | |
| TASK-009 | Implement pace calculation algorithms and utilities | | |
| TASK-010 | Create HR averaging and validation logic | | |
| TASK-011 | Implement StartWorkoutUseCase with session initialization | | |
| TASK-012 | Implement StopWorkoutUseCase with data persistence | | |
| TASK-013 | Create UpdateWorkoutDataUseCase for real-time updates | | |
| TASK-014 | Implement session state transition validation | | |

### Implementation Phase 3: Data Layer Implementation

- GOAL-003: Build data persistence and repository implementations

| Task | Description | Completed | Date |
|------|-------------|-----------|------|
| TASK-015 | Create SQLDelight schema for workout sessions and samples | | |
| TASK-016 | Implement WorkoutRepository with CRUD operations | | |
| TASK-017 | Create data mappers between domain and data layer | | |
| TASK-018 | Implement local storage with encryption for sensitive data | | |
| TASK-019 | Create backup and restore functionality for workout data | | |
| TASK-020 | Add data migration strategies for future schema changes | | |

### Implementation Phase 4: Platform Bridge Implementation

- GOAL-004: Implement expect/actual patterns for platform-specific functionality

| Task | Description | Completed | Date |
|------|-------------|-----------|------|
| TASK-021 | Create expect/actual PebbleKit wrapper interface | | |
| TASK-022 | Implement Android PebbleKit integration with AppMessage protocol | | |
| TASK-023 | Implement iOS PebbleKit bridging with native SDK | | |
| TASK-024 | Create expect/actual Location service interface | | |
| TASK-025 | Implement Android location provider with FusedLocationProviderClient | | |
| TASK-026 | Implement iOS CoreLocation integration | | |
| TASK-027 | Add connection state management and auto-reconnection logic | | |

### Implementation Phase 5: Background Services

- GOAL-005: Implement background processing for continuous workout tracking

| Task | Description | Completed | Date |
|------|-------------|-----------|------|
| TASK-028 | Create Android Foreground Service for workout tracking | | |
| TASK-029 | Implement iOS background processing with Background App Refresh | | |
| TASK-030 | Add service lifecycle management and cleanup | | |
| TASK-031 | Implement battery optimization strategies | | |
| TASK-032 | Create notification system for workout status updates | | |
| TASK-033 | Add error recovery mechanisms for service interruptions | | |

### Implementation Phase 6: UI Layer Development

- GOAL-006: Build reactive UI with Compose Multiplatform

| Task | Description | Completed | Date |
|------|-------------|-----------|------|
| TASK-034 | Create main workout screen with Start/Stop functionality | | |
| TASK-035 | Implement real-time data display (HR, Pace, Duration, Distance) | | |
| TASK-036 | Create workout history screen with session list | | |
| TASK-037 | Implement workout detail view with charts and analytics | | |
| TASK-038 | Add settings screen for user preferences | | |
| TASK-039 | Create onboarding flow for Pebble connection setup | | |
| TASK-040 | Implement responsive design for different screen sizes | | |

### Implementation Phase 7: Pebble Watchapp Development

- GOAL-007: Develop PebbleRun C-based watchapp

| Task | Description | Completed | Date |
|------|-------------|-----------|------|
| TASK-041 | Set up Pebble SDK development environment | | |
| TASK-042 | Create basic PebbleRun watchapp structure | | |
| TASK-043 | Implement HR sensor integration with 1-second sampling | | |
| TASK-044 | Create AppMessage communication layer | | |
| TASK-045 | Implement data display UI (HR, Pace, Duration) | | |
| TASK-046 | Add watchapp lifecycle management (launch/close commands) | | |
| TASK-047 | Test watchapp on Pebble emulator and physical device | | |

### Implementation Phase 8: Integration & Testing

- GOAL-008: Complete end-to-end integration and comprehensive testing

| Task | Description | Completed | Date |
|------|-------------|-----------|------|
| TASK-048 | Integrate all components with comprehensive error handling | | |
| TASK-049 | Implement unit tests for domain logic and calculations | | |
| TASK-050 | Create integration tests for repository and data flow | | |
| TASK-051 | Add platform-specific tests for PebbleKit and Location services | | |
| TASK-052 | Perform end-to-end testing with real Pebble device | | |
| TASK-053 | Conduct performance testing and battery usage optimization | | |
| TASK-054 | Test background service reliability and edge cases | | |

## 3. Alternatives

- **ALT-001**: Xamarin.Forms instead of KMP - Rejected due to Microsoft's focus shift to .NET MAUI and better Kotlin ecosystem for Android
- **ALT-002**: Native Android/iOS development - Rejected due to code duplication and maintenance overhead
- **ALT-003**: Flutter for cross-platform - Rejected due to lack of mature PebbleKit bindings and preference for native performance
- **ALT-004**: Cordova/PhoneGap hybrid approach - Rejected due to poor performance for real-time tracking applications
- **ALT-005**: React Native - Rejected due to JavaScript bridge overhead affecting real-time data processing

## 4. Dependencies

- **DEP-001**: Kotlin Multiplatform Mobile 2.2.10+ for cross-platform development
- **DEP-002**: Compose Multiplatform 1.8.2+ for shared UI development
- **DEP-003**: SQLDelight for cross-platform database management
- **DEP-004**: Koin for dependency injection across platforms
- **DEP-005**: Android PebbleKit SDK for Android Pebble communication
- **DEP-006**: iOS PebbleKit framework for iOS Pebble communication
- **DEP-007**: Android Play Services Location for GPS tracking
- **DEP-008**: iOS CoreLocation framework for GPS tracking
- **DEP-009**: Pebble SDK 4.3+ for watchapp development
- **DEP-010**: Ktor for potential future network communication
- **DEP-011**: Kotlinx.Serialization for data serialization
- **DEP-012**: Kotlinx.Coroutines for asynchronous operations
- **DEP-013**: Kotlinx.DateTime for cross-platform date/time handling

## 5. Files

### Shared Module Files
- **FILE-001**: `shared/domain/entity/WorkoutSession.kt` - Core workout session entity
- **FILE-002**: `shared/domain/entity/GeoPoint.kt` - GPS coordinate data class
- **FILE-003**: `shared/domain/entity/HRSample.kt` - Heart rate sample data class
- **FILE-004**: `shared/domain/repository/WorkoutRepository.kt` - Repository interface
- **FILE-005**: `shared/domain/usecase/StartWorkoutUseCase.kt` - Start workout business logic
- **FILE-006**: `shared/domain/usecase/StopWorkoutUseCase.kt` - Stop workout business logic
- **FILE-007**: `shared/data/repository/WorkoutRepositoryImpl.kt` - Repository implementation
- **FILE-008**: `shared/bridge-pebble/PebbleTransport.kt` - Pebble communication interface
- **FILE-009**: `shared/bridge-location/LocationProvider.kt` - Location service interface
- **FILE-010**: `shared/storage/WorkoutDatabase.sq` - SQLDelight schema

### Android Platform Files
- **FILE-011**: `androidApp/src/main/kotlin/service/WorkoutTrackingService.kt` - Foreground service
- **FILE-012**: `androidApp/src/main/kotlin/pebble/AndroidPebbleTransport.kt` - PebbleKit implementation
- **FILE-013**: `androidApp/src/main/kotlin/location/AndroidLocationProvider.kt` - Location service impl
- **FILE-014**: `androidApp/src/main/AndroidManifest.xml` - Permissions and service declarations

### iOS Platform Files
- **FILE-015**: `iosApp/iosApp/Service/WorkoutTrackingService.swift` - Background processing
- **FILE-016**: `iosApp/iosApp/Pebble/IOSPebbleTransport.swift` - PebbleKit bridging
- **FILE-017**: `iosApp/iosApp/Location/IOSLocationProvider.swift` - CoreLocation implementation
- **FILE-018**: `iosApp/iosApp/Info.plist` - Background modes and permissions

### Pebble Watchapp Files
- **FILE-019**: `pebble-watchapp/src/c/main.c` - Main watchapp entry point
- **FILE-020**: `pebble-watchapp/src/c/hr_monitor.c` - HR sensor management
- **FILE-021**: `pebble-watchapp/src/c/app_message.c` - Communication protocol
- **FILE-022**: `pebble-watchapp/appinfo.json` - Watchapp configuration

### UI Files
- **FILE-023**: `composeApp/src/commonMain/kotlin/ui/workout/WorkoutScreen.kt` - Main workout UI
- **FILE-024**: `composeApp/src/commonMain/kotlin/ui/history/HistoryScreen.kt` - Workout history
- **FILE-025**: `composeApp/src/commonMain/kotlin/ui/detail/WorkoutDetailScreen.kt` - Session details

## 6. Testing

### Unit Tests
- **TEST-001**: Domain entity validation and business logic testing
- **TEST-002**: Pace calculation algorithm verification with various GPS inputs
- **TEST-003**: HR averaging and validation logic testing
- **TEST-004**: Use case testing with mocked dependencies
- **TEST-005**: Data mapper testing for domain/data layer conversion

### Integration Tests
- **TEST-006**: Repository testing with in-memory SQLDelight database
- **TEST-007**: End-to-end data flow testing from UI to persistence
- **TEST-008**: Mock PebbleTransport testing for AppMessage simulation
- **TEST-009**: Location provider testing with simulated GPS coordinates

### Platform Tests
- **TEST-010**: Android Foreground Service lifecycle and notification testing
- **TEST-011**: iOS background processing and CoreLocation integration testing
- **TEST-012**: PebbleKit integration testing on both platforms
- **TEST-013**: Permission handling and user authorization flow testing

### Performance Tests
- **TEST-014**: Battery usage measurement during extended workout sessions
- **TEST-015**: Memory leak detection for long-running services
- **TEST-016**: Real-time data processing latency measurement
- **TEST-017**: Pebble communication reliability and reconnection testing

### End-to-End Tests
- **TEST-018**: Complete workout session from start to finish with real Pebble device
- **TEST-019**: Data persistence verification across app restarts
- **TEST-020**: Background service continuation during device sleep/wake cycles

## 7. Risks & Assumptions

### Technical Risks
- **RISK-001**: PebbleKit SDK compatibility issues with modern Android/iOS versions - Mitigation: Thorough compatibility testing and fallback strategies
- **RISK-002**: Bluetooth connectivity reliability affecting real-time data - Mitigation: Implement robust reconnection logic and offline data buffering
- **RISK-003**: Battery drain impact on user experience - Mitigation: Extensive optimization and user-configurable power saving modes
- **RISK-004**: GPS accuracy variations affecting pace calculations - Mitigation: Implement filtering algorithms and use multiple location sources

### Business Risks
- **RISK-005**: Limited Pebble device availability in market - Mitigation: Clear documentation for device compatibility and sourcing guidance
- **RISK-006**: User adoption challenges due to setup complexity - Mitigation: Streamlined onboarding flow and comprehensive user guides

### Development Risks
- **RISK-007**: KMP platform-specific implementation complexity - Mitigation: Incremental development with early platform testing
- **RISK-008**: Pebble C SDK learning curve for team - Mitigation: Dedicated training time and community resource utilization

### Assumptions
- **ASSUMPTION-001**: Target devices have Bluetooth Low Energy support for Pebble connectivity
- **ASSUMPTION-002**: Users grant location permissions for GPS tracking functionality
- **ASSUMPTION-003**: Pebble 2 HR devices remain functional and supported by community resources
- **ASSUMPTION-004**: Background processing limitations on iOS will not significantly impact user experience
- **ASSUMPTION-005**: SQLDelight provides sufficient performance for real-time data storage requirements

## 8. Related Specifications / Further Reading

- [Kotlin Multiplatform Mobile Documentation](https://kotlinlang.org/docs/multiplatform-mobile-getting-started.html)
- [Compose Multiplatform Documentation](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-multiplatform-getting-started.html)
- [Pebble SDK Documentation](https://developer.rebble.io/developer.pebble.com/guides/index.html)
- [SQLDelight Documentation](https://cashapp.github.io/sqldelight/)
- [Android Foreground Services Guide](https://developer.android.com/guide/components/foreground-services)
- [iOS Background App Refresh Guide](https://developer.apple.com/documentation/backgroundtasks)
- [Clean Architecture Principles](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)
- [Domain-Driven Design Patterns](https://martinfowler.com/tags/domain%20driven%20design.html)
