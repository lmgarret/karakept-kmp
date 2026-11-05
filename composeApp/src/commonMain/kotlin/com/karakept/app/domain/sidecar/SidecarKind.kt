package com.karakept.app.domain.sidecar

/**
 * The sidecar services the app knows how to talk to.
 *
 * A sidecar is an optional self-hosted companion to Karakeep: the app assumes none is deployed and
 * offers nothing until one is configured. Adding a second kind is a constant here, a label and
 * description below, and a capabilities parser — the settings screen renders one card per entry in
 * [ALL] and the storage layer keys everything by kind.
 */
object SidecarKind {
    /** karhtmlscrub: sanitizes crawled article HTML and attaches the result as a named asset. */
    const val HTML_SCRUB = "htmlScrub"

    val ALL: List<String> = listOf(HTML_SCRUB)

    fun label(kind: String): String = when (kind) {
        HTML_SCRUB -> "karhtmlscrub"
        else -> kind
    }

    fun description(kind: String): String = when (kind) {
        HTML_SCRUB -> "Cleans up crawled article HTML — strips ad nags, sign-in banners and broken embeds"
        else -> ""
    }
}
