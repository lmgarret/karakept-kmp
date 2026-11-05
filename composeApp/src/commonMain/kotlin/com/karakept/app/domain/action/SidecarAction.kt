package com.karakept.app.domain.action

/**
 * A job the user can ask a configured sidecar service to run for a bookmark.
 *
 * Like [AiAction] and [ServerCrawlAction] — and unlike [BookmarkActionEvent] — these have no local
 * optimistic counterpart and no undo: the work happens in another process against a model, and
 * re-running one is the only way back. What is different here is that the backend is optional and
 * user-supplied, so every surface must hide the action until a sidecar has said it will run it.
 */
enum class SidecarAction(val label: String, val runningLabel: String) {
    SANITIZE_HTML(label = "Clean up article", runningLabel = "Cleaning up article")
}
