# Phase 05: List & Sync - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-03-23
**Phase:** 05-list-sync
**Areas discussed:** LIST-02 offline sync trigger, LIST-02 offline sync scope/staleness

---

## LIST-01: Optimistic removal

*(Not selected for discussion — Claude's discretion applied)*

**Decision:** Filter bookmark out of `_accumulatedBookmarks` entirely on remove-from-list action, consistent with the existing delete/archive pattern.

---

## LIST-02: Offline Sync Trigger

| Option | Description | Selected |
|--------|-------------|----------|
| On sync button press | Download only when user presses sync while viewing an offline-enabled list | |
| Automatically on all syncs | Any sync for a list with syncOffline=true triggers content download | ✓ |
| Separate explicit action | Dedicated "Download offline" button independent of regular sync | |

**User's choice:** Automatically on all syncs
**Notes:** Content download happens as part of any sync that processes a list with `syncOffline = true`, not just when the user is currently viewing that list.

---

## LIST-02: Offline Sync Scope & Staleness

| Option | Description | Selected |
|--------|-------------|----------|
| Incremental — only missing content | Download only bookmarks without cached content | |
| Full — all bookmarks on every sync | Re-download content for every bookmark each sync | |
| Smart incremental with staleness detection | Use `modifiedAt` metadata to detect re-crawled bookmarks; fetch only missing OR changed | ✓ |

**User's choice:** Smart incremental with staleness detection using `modifiedAt` (or `crawledAt`)
**Notes:** User raised that pure incremental misses re-crawled bookmarks. Discussion led to using `modifiedAt` (top-level on `Bookmark` DTO, available without `includeContent`) as a staleness signal. Store `modifiedAt` in `BookmarkEntity`; skip fetch only when content exists AND `modifiedAt` is unchanged. Planner must verify whether Karakeep updates `modifiedAt` on re-crawl vs. only on user-initiated changes.

---

## Claude's Discretion

- DB migration version for `modifiedAt` column
- Whether to use `fetchBookmarkContent` per-bookmark or `includeContent=true` on list sync (depends on `modifiedAt` verification)
- Error handling for individual content fetch failures

---

## Deferred Ideas

None.
