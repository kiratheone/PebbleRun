---
applyTo: "apps/pebble-watchapp/**"
---

# Pebble Watchapp (C) Rules
- Use Health API (filtered BPM). Subscribe to `HealthEventHeartRateUpdate`.
- Open AppMessage with small buffers; handle inbox/outbox callbacks robustly.
- On STOP CMD: `window_stack_pop_all()` and reset HR sample period.
- Keep modules short: `ui.c`, `hr.c`, `appmsg.c`. Check return codes.