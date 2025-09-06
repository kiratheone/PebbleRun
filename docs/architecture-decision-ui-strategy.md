# Architecture Decision: UI Strategy for PebbleRun

## Decision
**HYBRID APPROACH**: Keep `composeApp` for shared business logic and ViewModels, implement platform-specific native UI layers.

## Context
- PRD expects native platform experiences (Android Compose + iOS SwiftUI)
- Current project has both `composeApp` (KMP shared UI) and separate `androidApp`/`iosApp`
- Need to balance code reuse with native platform optimization

## Chosen Architecture

```
PebbleRun/
├─ apps/
│  ├─ androidApp/          # Native Android Jetpack Compose UI
│  │  ├─ src/main/kotlin/
│  │  │  ├─ ui/            # Android-specific Compose screens
│  │  │  ├─ service/       # Android Foreground Service
│  │  │  ├─ di/            # Android DI setup
│  │  │  └─ MainActivity.kt
│  │  └─ AndroidManifest.xml
│  │
│  ├─ iosApp/              # Native iOS SwiftUI UI  
│  │  ├─ UI/               # SwiftUI views
│  │  ├─ Service/          # iOS Background processing
│  │  ├─ DI/               # iOS DI container
│  │  └─ Info.plist        # Background modes
│  │
│  ├─ composeApp/          # SHARED: Business Logic + ViewModels
│  │  ├─ src/commonMain/
│  │  │  ├─ viewmodel/     # Shared ViewModels with Flow/StateFlow
│  │  │  ├─ ui/bridge/     # Platform UI integration interfaces
│  │  │  └─ di/            # Shared DI modules
│  │  └─ src/androidMain/  # Android-specific ViewModel extensions
│  │      src/iosMain/     # iOS-specific ViewModel bridges
│  └─ pebble-watchapp/     # Pebble C SDK
│
├─ shared/                 # Pure KMP Business Logic (NO UI)
│  ├─ domain/              # Entities, UseCases, Repository interfaces
│  ├─ data/                # Repository implementations
│  ├─ bridge-pebble/       # Platform-specific PebbleKit
│  ├─ bridge-location/     # Platform-specific Location
│  ├─ storage/             # SQLDelight
│  └─ util/                # Utilities
```

## Data Flow

1. **Shared ViewModels** (`composeApp`) expose `StateFlow`/`Flow` from domain use cases
2. **Android UI** (`androidApp`) uses Compose with `collectAsStateWithLifecycle()`
3. **iOS UI** (`iosApp`) uses SwiftUI with `ObservableObject` bridge to Kotlin Flow
4. **Platform Services** integrate with shared business logic through dependency injection

## Benefits
- ✅ Maximum code reuse for business logic
- ✅ Native platform UI experience  
- ✅ Platform-specific optimizations (Foreground Service, Background Modes)
- ✅ Easier maintenance than full native
- ✅ Clear separation of concerns

## Trade-offs
- ⚠️ Some UI logic duplication between platforms
- ⚠️ Need KMP-NativeCoroutines for iOS Flow integration
- ⚠️ More complex build setup than pure Compose Multiplatform

## Implementation Priority
1. Setup shared ViewModels in `composeApp`
2. Implement Android native UI in `androidApp`
3. Implement iOS native UI in `iosApp`
4. Integrate platform-specific services
5. Testing and optimization
