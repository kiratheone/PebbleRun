# 📑 Product Requirement Document (PRD) – Pebble 2 HR Companion App

## 1. Background
Pebble 2 HR is an old smartwatch with an HR sensor but lacks support for modern fitness apps like Strava.  
Users still want to utilize the device for basic workout tracking (HR, Pace, Duration).  
This companion app bridges Pebble 2 HR with Android/iOS phones for a lightweight standalone experience.

---

## 2. Product Objectives
- Provide HR, Pace, and Duration tracking on Pebble 2 HR and companion mobile app.  
- Mobile app handles GPS and pace calculations.  
- Store workout data locally (JSON/SQLite/GPX).  
- Run tracking in the background for long sessions.  
- Auto-launch PebbleRun watchapp from the phone to provide a dedicated workout display.  

---

## 3. Key Features
1. **Start/Stop Session**
   - Start/Stop button in mobile app.
   - On Start: mobile launches PebbleRun watchapp.
   - On Stop: PebbleRun closes and Pebble returns to default watchface.

2. **Real-time Data Display**
   - Pebble → Mobile: HR data.
   - Mobile → Pebble: Pace and Duration.
   - Updated every second.

3. **Local Data Storage**
   - Save workout summary: distance, avg HR, avg pace, duration.
   - Save detailed logs: GPS track and HR samples.

4. **Background Tracking**
   - Android: Foreground Service (location type).
   - iOS: Background Modes (Location + BLE).

---

## 4. User Stories
- As a runner, I want to press **Start** on my phone, so Pebble switches to PebbleRun and displays HR, Pace, and Duration.  
- As a runner, I want to press **Stop** on my phone, so PebbleRun closes, Pebble returns to the default watchface, and data is saved.  
- As a runner, I want to view a workout summary on my phone after I stop a session.  

---

## 5. Technical Flow
1. User presses Start → mobile starts GPS & timer → sends Launch command → PebbleRun opens.  
2. PebbleRun subscribes to HR sensor, sets sample rate=1s, and opens AppMessage channel.  
3. Every second: Pebble sends HR → Mobile; Mobile sends Pace+Time → Pebble.  
4. User presses Stop → Mobile sends CMD=STOP → PebbleRun closes → Pebble returns to default watchface → Mobile saves session.  

---

## 6. API & Protocols

**AppMessage Keys**
- 0 = PACE (cstring `"mm:ss/km"`)  
- 1 = TIME (cstring `"HH:MM:SS"`)  
- 2 = HR (uint16 BPM)  
- 3 = CMD (uint8: 1=START, 2=STOP)  

---

## 7. Data Model
```kotlin
data class WorkoutSession(
  val startTime: Long,
  val endTime: Long?,
  val duration: Long,
  val distanceMeters: Double,
  val avgPace: String,
  val avgHR: Int,
  val gpsTrack: List<GeoPoint>,
  val hrSamples: List<HRSample>
)

data class GeoPoint(val lat: Double, val lon: Double, val timestamp: Long)
data class HRSample(val bpm: Int, val timestamp: Long)
```

---

## 8. Technical Implementation

### 8.1 Folder Structure (Minimal Clean Architecture)
```
pebble2hr/
├─ apps/
│  ├─ androidApp/          # Android (Compose UI, ViewModels, DI)
│  ├─ iosApp/              # iOS (SwiftUI, DI, bridging flows)
│  └─ pebble-watchapp/     # Pebble C SDK (PebbleRun)
│
├─ shared/
│  ├─ domain/              # Entities, ValueObjects, UseCases, Repo interfaces
│  ├─ data/                # Repo implementations, Mappers, Infra
│  ├─ bridge-pebble/       # expect/actual PebbleKit wrapper
│  ├─ bridge-location/     # expect/actual Location provider
│  ├─ storage/             # SQLDelight schemas & DAO
│  ├─ proto/               # AppMessage keys & DTOs
│  └─ util/                # Utils (logger, formatter, Result)
```

### 8.2 Dependency Rules
- apps → data, domain  
- data → domain, bridge, storage, proto, util  
- domain → no dependency  

### 8.3 Watchapp
- Type: watchapp (not watchface).  
- Auto-launched from mobile on Start.  
- Closes on Stop and returns Pebble to default watchface.  

---

## 9. Testing Strategy

### 9.1 Unit Tests
- Domain: pace calculation, HR averaging, session state transitions.  

### 9.2 Integration Tests
- Data: repositories with SQLDelight (in-memory).  
- Mock PebbleTransport: simulate AppMessage HR flow.  

### 9.3 Platform Tests
- Android: Foreground Service + PebbleKit integration (instrumented).  
- iOS: CoreLocation + PebbleKit bridging (XCTest).  

### 9.4 Watchapp Tests
- Emulator log validation for HR updates & AppMessage events.  
- Manual device test: HR accuracy, latency, battery usage.  

---

## 10. Risks & Mitigation
- **Battery drain** from HR sampling → only active during sessions.  
- **Pebble disconnection** → notify user, auto-reconnect attempt.  
- **User confusion** when PebbleRun replaces watchface → add UX note: “PebbleRun replaces your watchface during a workout and returns after session ends.”  

---

## 11. Roadmap
- **MVP**: Android+iOS apps, PebbleRun watchapp, local storage.  
- **Next**: Strava integration, advanced HR/pace charts.  
- **Future**: Training plans, AI coaching, export to health apps.  
