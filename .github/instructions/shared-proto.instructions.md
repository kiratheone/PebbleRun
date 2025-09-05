---
applyTo: "shared/proto/**"
---

# Protocol Single Source of Truth
- Define **only** DTOs/constants used across platforms (AppKeys).
- Never duplicate AppMessage keys in other modules. Generate/update Pebble C header from here.