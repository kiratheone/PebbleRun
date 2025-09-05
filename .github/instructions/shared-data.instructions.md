---
applyTo: "shared/data/**"
---

# Data Layer Rules (MUST)
- Implements domain repositories. No UI or lifecycle.
- Delegate platform work to `shared/bridge-*`. Do not import platform SDKs directly here.
- Map between domain and storage/DTO cleanly (separate mappers).
- For tests, support in-memory SQLDelight and fake bridges.