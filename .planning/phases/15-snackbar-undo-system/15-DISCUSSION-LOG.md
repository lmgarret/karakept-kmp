# Phase 15: Snackbar Undo System - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-03-28
**Phase:** 15-snackbar-undo-system
**Areas discussed:** Undo scope, Undo mechanics, Snackbar feedback gap, Batch actions

---

## Undo Scope

### Q1: Which reversible actions should get Undo snackbars?

| Option | Description | Selected |
|--------|-------------|----------|
| All reversible (Recommended) | Every toggle/move action gets undo: swipe tag add/remove, swipe list add/remove, bottom sheet archive, favorite, read, move-to-list, update-tags. Only Delete keeps confirm dialog. | ✓ |
| Swipe actions only | Only quick swipe actions get undo. Bottom sheet actions stay as-is. | |
| You decide | Claude picks based on codebase and UX-01 requirement. | |

**User's choice:** All reversible (Recommended)
**Notes:** None

### Q2: Should the viewer screen also get undo snackbars?

| Option | Description | Selected |
|--------|-------------|----------|
| Both screens (Recommended) | Viewer already has snackbar infrastructure. Actions taken in viewer should also show undo. | ✓ |
| Main list only | Only wire undo on main bookmark list. | |
| You decide | Claude decides based on effort vs. consistency. | |

**User's choice:** Both screens (Recommended)
**Notes:** None

---

## Undo Mechanics

### Q3: How should undo work when the server has already been called?

| Option | Description | Selected |
|--------|-------------|----------|
| Re-call reverse API (Recommended) | Action fires immediately. Undo calls the reverse API. Simple, uses existing ScreenModel methods. | ✓ |
| Optimistic delay | Delay server call until snackbar expires. Undo cancels pending call. More complex, risk of lost actions. | |
| You decide | Claude picks based on codebase complexity. | |

**User's choice:** Re-call reverse API (Recommended)
**Notes:** None

### Q4: What snackbar duration for undo actions?

| Option | Description | Selected |
|--------|-------------|----------|
| Short (4s) (Recommended) | MD3 default. Quick enough not to block, long enough to tap Undo. | ✓ |
| Long (10s) | More time to decide. May feel intrusive for quick toggles. | |
| You decide | Claude picks per-action type. | |

**User's choice:** Short (4s) (Recommended)
**Notes:** None

---

## Snackbar Feedback Gap

### Q5: Should all bottom sheet actions get snackbars?

| Option | Description | Selected |
|--------|-------------|----------|
| All get snackbars (Recommended) | Every action gets confirmation snackbar with undo. | ✓ |
| Only destructive-feeling ones | Archive, move-to-list, update-tags get snackbars. Favorite/read skip (visual feedback from icon). | |
| You decide | Claude picks based on visual feedback analysis. | |

**User's choice:** All get snackbars (Recommended)
**Notes:** None

### Q6: Snackbar message tense?

| Option | Description | Selected |
|--------|-------------|----------|
| Past tense (Recommended) | "Archived", "Added to favorites". Action already fired. | ✓ |
| Present tense | "Archiving...", "Adding to favorites...". Suggests async. | |
| You decide | Claude picks wording. | |

**User's choice:** Past tense (Recommended)
**Notes:** None

---

## Batch Actions

### Q7: Should batch operations get undo snackbars?

| Option | Description | Selected |
|--------|-------------|----------|
| Batch archive: yes, batch delete: keep dialog (Recommended) | Batch archive gets undo snackbar. Batch delete keeps confirm dialog (permanent action). | ✓ |
| Both batch actions get undo | Replace batch delete confirm dialog with undo snackbar too. | |
| No batch undo | Batch operations stay as-is. Only single-item actions. | |

**User's choice:** Batch archive: yes, batch delete: keep dialog (Recommended)
**Notes:** None

### Q8: Other batch actions in selection mode?

| Option | Description | Selected |
|--------|-------------|----------|
| You decide | Claude audits selection mode and applies undo pattern to any other reversible batch actions. | ✓ |
| Only archive and delete | Don't worry about other batch actions. | |

**User's choice:** You decide
**Notes:** Claude has discretion to audit and wire additional batch actions.

---

## Claude's Discretion

- Whether to create a shared helper for the undo snackbar pattern (if 3+ sites)
- Audit additional batch actions in selection mode beyond archive/delete

## Deferred Ideas

None — discussion stayed within phase scope
