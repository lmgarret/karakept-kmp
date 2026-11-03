package com.karakept.app.domain.action

/**
 * An AI job the user can ask the Karakeep server to run for a bookmark.
 *
 * Like [ServerCrawlAction] — and unlike [BookmarkActionEvent] — these have no local optimistic
 * counterpart and no undo: the work happens on the server against a model, and re-running one is
 * the only way back.
 *
 * The two differ in more than their prompt. [SUMMARIZE] is a public REST endpoint any account may
 * call, and it answers with the finished summary. [RETAG] is an admin-only tRPC route that merely
 * enqueues a job, so its result arrives on a later sync — Karakeep exposes no user-level way to
 * re-tag one bookmark.
 */
enum class AiAction(val label: String, val runningLabel: String) {
    SUMMARIZE(label = "Generate summary", runningLabel = "Generating summary"),
    RETAG(label = "Re-run AI tagging", runningLabel = "Re-running AI tagging")
}
