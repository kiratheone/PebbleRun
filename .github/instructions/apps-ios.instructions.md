---
applyTo: "apps/iosApp/**"
---

# iOS App (SwiftUI) Rules
- UI only: ObservableObject bridges KMP Flows (KMP-NativeCoroutines or callbacks).
- Configure Background Modes (Location + BLE) in app target; keep business logic in KMP.
- Do not block main thread; prefer async/await for bridging.