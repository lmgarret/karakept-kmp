# Phase 16: Custom Layout Improvements - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-03-30
**Phase:** 16-custom-layout-improvements
**Areas discussed:** Layout unification strategy, Card preview content, URL/domain display, Picker navigation

---

## Layout Unification Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Shared options only | Keep CARD, LIST, COMPACT_LIST as separate types but add the same customization toggles to all | |
| Merge into fewer types | Collapse CARD and LIST into a single configurable type | |
| You decide | Claude picks the best approach | |
| *(User input)* | Merge LIST and COMPACT_LIST instead, keep CARD separate | ✓ |

**User's choice:** Merge LIST and COMPACT_LIST (not CARD and LIST as originally suggested), because they're both row-based layouts differing only in metadata density.
**Notes:** User proactively reframed the unification — LIST and COMPACT_LIST are the natural merge candidates.

### Follow-up: Migration strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Auto-migrate to LIST | Convert existing COMPACT_LIST layouts to LIST with compact-like toggle values | ✓ |
| Keep as hidden legacy | Stop offering COMPACT_LIST in editor but keep rendering for existing layouts | |
| You decide | Claude picks the migration approach | |

**User's choice:** Auto-migrate to LIST

### Follow-up: Card options

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, full parity | Card gets all the same toggles as unified List type | |
| Card gets some options | Card gets show/hide description and title position, but not description position | ✓ |
| Card stays as-is | Only unified List type gets new options | |

**User's choice:** Card gets some options (show/hide description + title position, not description position)

---

## Card Preview Content

| Option | Description | Selected |
|--------|-------------|----------|
| All toggled fields | Preview reflects exactly what the user has enabled, updates live | ✓ |
| Fixed full preview | Always show all fields regardless of toggles | |
| You decide | Claude picks based on existing pattern | |

**User's choice:** All toggled fields (live preview reflecting current toggle states)
**Notes:** None

---

## URL/Domain Display

### Display mode

| Option | Description | Selected |
|--------|-------------|----------|
| Domain only | Show just the domain (e.g., "github.com") | |
| Domain or full URL | User can choose between domain-only or full URL | ✓ |
| You decide | Claude picks based on space constraints | |

**User's choice:** Domain or full URL (user gets both options)

### Position

| Option | Description | Selected |
|--------|-------------|----------|
| Near favicon | Fixed position next to favicon in metadata row | |
| Configurable position | User picks where URL appears (below title, metadata row, beside favicon) | ✓ |
| You decide | Claude picks the most sensible position | |

**User's choice:** Configurable position
**Notes:** None

---

## Picker Navigation

### Create button

| Option | Description | Selected |
|--------|-------------|----------|
| Footer button | "Create new layout" button at bottom of per-list picker dialog | ✓ |
| Inline text link | "+ Create new" text link at end of radio button list | |
| You decide | Claude picks best UX pattern | |

**User's choice:** Footer button

### Edit access

| Option | Description | Selected |
|--------|-------------|----------|
| Create only | Per-list picker just gets create button, editing stays in global settings | ✓ |
| Create + edit | Each custom layout gets edit icon in per-list picker | |
| You decide | Claude decides based on dialog complexity | |

**User's choice:** Create only

---

## Claude's Discretion

- Migration logic for COMPACT_LIST → LIST conversion (exact default values)
- Specific urlPosition enum values
- Per-list picker navigation flow (dismiss dialog first or return to it)

## Deferred Ideas

None — discussion stayed within phase scope
