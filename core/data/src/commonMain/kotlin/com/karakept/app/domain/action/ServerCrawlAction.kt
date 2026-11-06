package com.karakept.app.domain.action

/**
 * A crawl job the user can ask the Karakeep server to run for a bookmark.
 *
 * These all map to the same tRPC mutation (`bookmarks.recrawlBookmark`) with different flags,
 * and mirror the "Refresh" / "Preserve offline archive" / "Preserve as PDF" actions in the
 * Karakeep web UI. Unlike [BookmarkActionEvent] these have no local optimistic counterpart and
 * no undo — the server enqueues a background job and the result shows up on a later sync.
 */
enum class ServerCrawlAction(val archiveFullPage: Boolean, val storePdf: Boolean) {
    REFRESH(archiveFullPage = false, storePdf = false),
    PRESERVE_ARCHIVE(archiveFullPage = true, storePdf = false),
    PRESERVE_PDF(archiveFullPage = false, storePdf = true)
}
