---
applyTo: "apps/androidApp/**"
---

# Android App (Compose) Rules
- UI only: ViewModels expose StateFlow; business logic in KMP use cases.
- Use Foreground Service (type=location) as the only long-running GPS place.
- No PebbleKit calls directly in UI; use domain/data abstractions.
- Avoid `!!`. Respect lifecycle; no blocking calls on main.