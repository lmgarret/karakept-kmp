# Phase 14: UI Interaction Fixes - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-03-27
**Phase:** 14-ui-interaction-fixes
**Areas discussed:** Highlights pull-to-refresh scope, Scroll-to-top fix approach, Pull-to-refresh API consistency

---

## Highlights Pull-to-Refresh Scope

| Option | Description | Selected |
|--------|-------------|----------|
| Add to HighlightsListContent | The expanded layout's highlights pane should also get pull-to-refresh | |
| Investigate first | Not sure where the gap is — verify on device before deciding | |
| Only fix if broken | If standalone HighlightsScreen already works, maybe skip | |

**User's choice:** Free-text — confirmed that on mobile/compact, pulling does nothing despite no refresh button being present either.

**Follow-up question:** Where to add PullToRefreshBox — inside HighlightsListContent or at the call site?

| Option | Description | Selected |
|--------|-------------|----------|
| Inside HighlightsListContent (Recommended) | Add PullToRefreshBox inside the composable — both layouts benefit | |
| At call site in MainScreen | Only compact gets PTR, expanded keeps refresh button only | |

**User's choice:** "I don't know, what seems best" — deferred to Claude's discretion.
**Notes:** User confirmed the bug exists on mobile compact. The standalone HighlightsScreen with PullToRefreshBox is never reached on compact — compact uses HighlightsListContent via MainScreen.kt:364.

---

## Scroll-to-Top Fix Approach

| Option | Description | Selected |
|--------|-------------|----------|
| Only MainScreen bookmark list (Recommended) | Fix the reported bug only — MainScreen scroll-to-top FAB | |
| Audit all scroll-to-top sites | Check BookmarkViewerContent and others, fix all | ✓ |

**User's choice:** Audit all scroll-to-top sites.

**Follow-up question:** Whether to reset saved scroll position on scroll-to-top.

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, reset saved position (Recommended) | Explicitly set savedScrollIndex=0 and savedScrollOffset=0 | |
| No, let snapshotFlow handle it | Let existing observer naturally update | |

**User's choice:** "whatever seems best" — deferred to Claude's discretion.
**Notes:** Root cause identified: animateScrollToItem(0) without scrollOffset=0.

---

## Pull-to-Refresh API Consistency

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, unify now (Recommended) | Migrate BookmarkListContent to PullToRefreshBox | ✓ |
| No, leave it | Deprecated API still works, defer migration | |

**User's choice:** Yes, unify now.
**Notes:** After this phase, entire app uses MD3 PullToRefreshBox consistently.

---

## Claude's Discretion

- Where to add PullToRefreshBox for HighlightsListContent (inside vs call site)
- Saved scroll position reset strategy on scroll-to-top

## Deferred Ideas

None — discussion stayed within phase scope.
