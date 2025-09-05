---
applyTo: "shared/bridge-location/**"
---

# Bridge – Location (expect/actual)
- Provide `LocationProvider` expect/actual. Android: FusedLocation; iOS: CoreLocation.
- Expose Flow of positions with timestamps; handle permissions outside domain.
- Smoothing & pace calc live in domain/data, not here.