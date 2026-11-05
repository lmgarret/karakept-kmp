package com.karakept.app.data.model

/**
 * Which copy of a bookmark's content the reader is showing.
 *
 * [SANITIZED] is a sidecar's cleaned copy of the extracted article (see `docs/sidecars.md`). Its
 * payload is a `<body>` fragment, like extracted content and unlike a full-page archive, so it is
 * rendered through the Reader path only and is never handed to the web view as a local file.
 */
enum class ContentSource {
    EXTRACTED,
    FULL_PAGE_ARCHIVE,
    SANITIZED
}
