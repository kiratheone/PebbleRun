---
applyTo: "shared/bridge-pebble/**"
---

# Bridge – Pebble (expect/actual)
- Provide `PebbleTransport` expect/actual. Android actual uses PebbleKit; iOS actual uses PebbleKit iOS.
- Honor AppMessage keys from `shared/proto`. Validate send result; retry with backoff on failure.
- Expose HR as a Flow<Int>. Do not leak platform callbacks to domain.
- Ensure START/STOP commands map correctly and are idempotent.