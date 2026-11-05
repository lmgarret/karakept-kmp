package com.karakept.app.data.model

import kotlinx.serialization.Serializable

/**
 * The asset name karhtmlscrub attaches its cleaned HTML under.
 *
 * This string is the whole contract for *reading* sanitized content: the sidecar uploads the asset
 * to Karakeep, so the app finds it by name in `assets[]` and needs no sidecar configuration to show
 * it. Only asking for a clean on demand talks to the service.
 */
const val SANITIZED_ASSET_NAME = "karhtmlscrub-sanitized.html"

/**
 * The asset type Karakeep reports a sidecar's upload as.
 *
 * `userUploaded` is the only type attachable over the API, so it covers every asset anyone attached
 * by hand. The file name is what distinguishes the sidecar's own — see [isSanitizedAsset].
 */
const val SANITIZED_ASSET_TYPE = "userUploaded"

/**
 * Whether a bookmark asset is a sidecar's sanitized copy.
 *
 * Judged by name rather than by id: Karakeep's asset replacement deletes the old row and points a
 * **new** id at the new blob, so the id changes every time an article is re-cleaned. The name is
 * the only stable handle.
 */
fun isSanitizedAsset(assetType: String?, fileName: String?): Boolean =
    assetType == SANITIZED_ASSET_TYPE && fileName == SANITIZED_ASSET_NAME

/**
 * Where a sidecar service lives, for one Karakeep server.
 *
 * The bearer token is deliberately absent: it lives in `SecureCredentialStore`, never in DataStore
 * or the database. Configuration is per server because a sidecar talks to one Karakeep instance —
 * pointing two servers at the same sidecar would have it upload assets to the wrong one.
 */
@Serializable
data class SidecarConfig(
    val serverId: String,
    val kind: String,
    val baseUrl: String = "",
    val enabled: Boolean = false
)

/** The map key sidecar configuration is stored under: one entry per (server, kind) pair. */
fun sidecarKey(serverId: String, kind: String): String = "$serverId:$kind"

/**
 * What a sidecar deployment says it can do, from `GET /api/v1/capabilities`.
 *
 * `/healthz` answers 200 whether or not a model is configured, and a karhtmlscrub running without
 * one silently produces nothing forever — [llmEnabled] is the flag that separates the two, and so
 * the one an action gates on.
 */
data class SidecarCapabilities(
    val service: String? = null,
    val llmEnabled: Boolean = false,
    val assetName: String = SANITIZED_ASSET_NAME,
    val maxContentSize: String? = null,
    val dryRun: Boolean = false
) {
    /** Whether offering a sanitize action to the user could produce anything. */
    val usable: Boolean get() = llmEnabled
}

/**
 * Why a sanitize run produced an asset, or why it did not.
 *
 * [terminal] carries the service's own meaning: retrying this bookmark unchanged could never give a
 * different answer, so the poll loop stops and says why instead of timing out. [UPLOADED] is the
 * success ending rather than a terminal refusal, which is why it is not marked terminal.
 */
enum class SanitizeOutcome(val wire: String, val terminal: Boolean, val message: String) {
    UPLOADED("uploaded", false, "Article cleaned up"),
    FILTERED("skipped_filtered", true, "The service's rules exclude this bookmark"),
    NO_CONTENT("skipped_no_content", false, "Nothing crawled to clean up yet"),
    LLM_DISABLED("skipped_llm_disabled", true, "The service has no model configured"),
    TOO_LARGE("skipped_too_large", true, "This article is too long for the service"),
    FAILED("failed", false, "The service couldn't clean up this article");

    companion object {
        fun fromWire(value: String?): SanitizeOutcome? = entries.firstOrNull { it.wire == value }
    }
}

/** How far a sanitize run has got. `queued` means accepted and waiting for a slot, not lost. */
enum class SanitizeJobStatus(val wire: String) {
    QUEUED("queued"),
    RUNNING("running"),
    DONE("done");

    companion object {
        fun fromWire(value: String?): SanitizeJobStatus? = entries.firstOrNull { it.wire == value }
    }
}

/** One sanitize run, as the sidecar reports it. */
data class SanitizeJob(
    val jobId: String,
    val bookmarkId: String,
    val status: SanitizeJobStatus,
    val outcome: SanitizeOutcome? = null,
    val error: String? = null
) {
    val isFinished: Boolean get() = status == SanitizeJobStatus.DONE
}
