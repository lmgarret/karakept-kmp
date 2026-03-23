# Phase 06: Discussion Log

**Date:** 2026-03-23
**Workflow:** discuss-phase
**Human reference only — not consumed by downstream agents.**

---

## Areas Discussed

User selected all three gray areas for discussion.

---

### Area: Select-all strategy (FILT-01)

**Q:** When the user taps 'Select All' and there are more pages not yet loaded, what should happen?

Options presented:
- Auto-load all pages — silently load remaining DB pages then select
- Select loaded items + snackbar — select only loaded items with a count message
- DB-query all IDs directly — fetch all matching IDs from DB without loading full entities, accumulate, then select

**A:** DB-query all IDs directly

---

### Area: Quick filter counter definitions (FILT-02)

**Q1:** What should the 'All Bookmarks' counter show?
**A:** Non-archived only (matches default view)

**Q2:** What should the 'Favorites' counter show?
**A:** Starred, non-archived (matches Favorites filter)

**Q3:** Should the 'Highlights' drawer item also show a count?
**A:** Yes, show highlights count

---

### Area: Pull-to-refresh behavior (FILT-03)

**Q:** What should pulling down on Highlights do?
**A:** Re-run syncHighlights() — same as auto-sync on screen open

---

*All decisions captured in 06-CONTEXT.md*
