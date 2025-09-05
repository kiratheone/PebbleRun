---
applyTo: "**/*Test.kt,**/*Spec.kt,**/test/**"
---

# Tests (Kotlin)
- Each non-trivial change must add/adjust tests (domain first).
- Use fakes for bridges; avoid real I/O in unit tests.
- Make tests deterministic; avoid sleeps/timeouts where possible.