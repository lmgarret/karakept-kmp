---
status: diagnosed
phase: 16-custom-layout-improvements
source: [16-01-SUMMARY.md, 16-02-SUMMARY.md, 16-03-SUMMARY.md]
started: 2026-03-30T16:30:00Z
updated: 2026-03-30T16:30:00Z
---

## Current Test

[testing complete]

## Tests

### 1. URL display on bookmarks
expected: Create or edit a layout, enable "Show URL" toggle, apply it to a list. Bookmarks with URLs should show a globe icon + the domain (e.g. "example.com") below the title. Bookmarks without a URL show nothing extra.
result: pass

### 2. URL Display Mode — Domain only vs Full URL
expected: In the layout editor with URL toggle ON, two "URL display" sub-options appear inline: "Domain only" and "Full URL". Selecting "Domain only" shows "example.com" in the preview; selecting "Full URL" shows the full URL string (e.g. "https://example.com/article"). Applied to a list, actual bookmarks reflect the selected mode.
result: pass

### 3. URL Position — Below title vs In metadata row
expected: In the layout editor with URL toggle ON, a "URL Position" section appears below the URL toggle. Options: "Below title" and "In metadata row". Preview updates to show the URL in the corresponding position. In metadata row places it alongside the date and reading time.
result: pass
note: "User feedback: globe icon hardcoded next to URL. Wants configurable favicon placement — favicon on thumbnail OR next to URL, with globe as fallback when no favicon. Logged as gap."

### 4. Description toggle — hide/show in editor and live list
expected: In the layout editor, toggle "Show description" OFF. The live preview hides the description text. Save the layout and apply it to a list — bookmarks no longer show their description. Toggle back ON — description reappears in both preview and live list.
result: pass

### 5. Description Position section (List type only)
expected: In the layout editor with List type and "Show description" ON, a "Description position" section appears. Options: "Below title" and "Above metadata". Selecting "Above metadata" moves the description above the tags/date row in the preview. Card type should NOT show this section.
result: pass

### 6. COMPACT_LIST removed from editor type selector
expected: Open Settings → Layouts → create or edit any layout. The layout type selector shows only "Card" and "List" — there is NO "Compact List" option.
result: pass

### 7. Existing Compact preset still works
expected: Open Settings → Layouts. The built-in "Compact" preset is still listed. Applying it to a list shows bookmarks in a compact list style (small thumbnail, no description, metadata beside thumbnail). It should function normally even though it's now backed by the List type internally.
result: pass

### 8. Live preview reflects all new fields
expected: In the layout editor, toggling Description/URL on and off, changing positions, and switching Display Mode all update the preview card/item immediately — no save required to see the preview reflect the current settings.
result: pass

### 9. "Create new layout" in per-list picker
expected: Open any list → tap the gear icon (per-list settings) → tap "Layout". At the bottom of the layout picker dialog, a "Create new layout" button (with + icon) is visible. Tapping it opens the Layout Editor. Saving the new layout returns to the picker, which now includes the new layout in its list.
result: pass

## Summary

total: 9
passed: 9
issues: 1
pending: 0
skipped: 0
blocked: 0

## Gaps

- truth: "URL display should support configurable favicon placement: favicon shown on thumbnail OR next to URL, with globe icon as fallback when no favicon is available"
  status: failed
  reason: "User reported: globe is always shown next to the URL instead of the favicon. Should be configurable — favicon on thumbnail or by the URL, globe as default when no favicon."
  severity: minor
  test: 3
  artifacts: []
  missing:
    - "New UrlIconMode or similar option in BookmarkLayout (e.g. FAVICON_BY_URL, FAVICON_ON_THUMBNAIL, GLOBE_ONLY)"
    - "Editor UI: icon mode selector in the URL section"
    - "UrlDisplay composable: render favicon (from existing FaviconUtils) instead of globe when favicon available and mode = FAVICON_BY_URL"
