---
applyTo: "shared/storage/**"
---

# Storage Rules
- SQLDelight schemas only; keep migrations clear and reversible.
- Repositories in `data` own transaction boundaries; storage stays thin.
- Prefer UTC timestamps (Long epoch ms). Avoid storing derived values redundantly.