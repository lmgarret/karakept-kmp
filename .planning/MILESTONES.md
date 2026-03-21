# Milestones

## v1.7.0 Tech Debt Cleanup (Shipped: 2026-03-21)

**Phases completed:** 2 phases, 3 plans, 6 tasks

**Key accomplishments:**

- Replaced 60 println calls with consolidated AppLogger logging in BookmarkActionsRepositorySync.kt (53) and BookmarkActionsRepository.kt (7)
- Replaced 37 println calls across 5 files with structured AppLogger logging and removed 1 hot-path per-bookmark detail line
- Removed redundant koinInject<ServerRepository>() shadow in App.kt and verified both large files under 500-line target

---
