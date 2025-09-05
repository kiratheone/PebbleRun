---
applyTo: "shared/domain/**"
---

# Domain Layer Rules (MUST)
- Pure Kotlin only. **No** platform types, I/O, thread, logging, or PebbleKit/CoreLocation calls.
- Define: Entities/Value Objects (e.g., Pace, Duration), UseCases, Repository **interfaces** only.
- Keep functions small and deterministic; avoid time & randomness in domain.
- Write unit tests for any new use case or domain logic.