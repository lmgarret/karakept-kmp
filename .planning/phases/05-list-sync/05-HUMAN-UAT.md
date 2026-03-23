---
status: partial
phase: 05-list-sync
source: [05-VERIFICATION.md]
started: 2026-03-23T19:55:00Z
updated: 2026-03-23T19:55:00Z
---

## Current Test

[awaiting human testing]

## Tests

### 1. Remove bookmark from list while viewing that list (LIST-01)
expected: The bookmark disappears from the list view immediately without requiring a refresh
result: [pending]

### 2. Remove bookmark while viewing All Bookmarks context (LIST-01)
expected: The bookmark stays visible (it still exists), only its listIds change (the list association is removed)
result: [pending]

### 3. Enable sync offline on a list and trigger sync (LIST-02)
expected: Bookmarks in that list with no content have their content downloaded (readingTimeMinutes > 0 after sync)
result: [pending]

### 4. Run test suite on JDK 17-21
expected: All 7 tests pass — 3 in RemoveBookmarkFromListTest + 4 in SyncContentOfflineTest
result: [pending]

## Summary

total: 4
passed: 0
issues: 0
pending: 4
skipped: 0
blocked: 0

## Gaps
