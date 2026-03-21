# Phase 5: Security Hardening - Context

**Gathered:** 2026-03-21
**Status:** Ready for planning

<domain>
## Phase Boundary

This phase hardens the app against untrusted content execution and protects credentials at rest. Two concerns: (1) HTML from Karakeep API rendered in Android WebView with JS enabled, and (2) API credentials stored in cleartext in Room database.

</domain>

<decisions>
## Implementation Decisions

### HTML Sanitization
- Scope: Android WebView path is the real risk — JS is enabled (line 798 of HtmlRenderer.android.kt despite comment claiming otherwise). Desktop NativeHtmlRenderer uses pure Compose composables via Ksoup, no JS execution, not vulnerable to XSS.
- Strip: script tags, event handlers (`on*` attributes), iframes, forms, `<object>`/`<embed>` — keep structural HTML + CSS for reader formatting
- Location: Sanitize in common code before passing to `HtmlRenderer` — single sanitization point for both platforms
- Implementation: Use Ksoup (already a dependency in NativeHtmlRenderer) to parse and strip dangerous nodes/attributes

### Credential Storage
- Android: EncryptedSharedPreferences (Jetpack Security) — simpler than direct Keystore, hardware-backed on supported devices
- Desktop: Java KeyStore (JKS) file with password — standard JVM approach
- Migration: Transparent on first launch — read from old DB location, write to new secure storage, clear old entries. No user action needed.
- Fallback: Fall back to existing DB storage with logged warning if secure storage unavailable

### Claude's Discretion
- Migration safety details: timing (startup vs lazy), per-server error handling, backup/restore interaction — user deferred these decisions

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- Ksoup already imported in `NativeHtmlRenderer.kt` (line 32) — available for HTML parsing/sanitization
- `ServerRepository.kt` manages server CRUD — migration logic goes here
- `ServerEntity.kt` / `ServerDao.kt` — current cleartext storage in Room
- `BackupRepository.kt` — exports/imports credentials, needs to remain compatible

### Established Patterns
- Platform-specific implementations via `expect`/`actual` — used for HtmlRenderer, can use for secure storage
- Error handling via AppLogger (Phase 1 pattern) — use for migration warnings
- Extension functions for repository concerns (Phase 3 pattern)

### Integration Points
- `HtmlRenderer` expect/actual — sanitization goes before the `html` parameter reaches renderers
- `ServerRepository` — migration runs at init, before API calls
- `BookmarkViewerScreenModel` — calls HtmlRenderer with content from API
- `RemoteDataSource` — reads credentials from ServerRepository

</code_context>

<specifics>
## Specific Ideas

- Fix the misleading comment in HtmlRenderer.android.kt that says "JavaScript disabled" when it's actually enabled (line 798)
- The JS is needed for highlight functionality — can't simply disable it

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>
