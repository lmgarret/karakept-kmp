package com.karakept.app.data.remote

import com.karakept.app.data.model.Server
import com.karakept.app.utils.TrpcPayloadUtils
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/** Karakeep's wording when the instance has no LLM wired up at all. */
private const val NO_INFERENCE_CLIENT_ERROR = "no inference client configured"

/** tRPC's wording for an unknown procedure — a 404 that is not a missing bookmark. */
private const val UNKNOWN_TRPC_PROCEDURE_ERROR = "No procedure found"

/**
 * Ask the server to re-crawl a link bookmark. The server enqueues a background job, so
 * success here only means the request was accepted — the resulting metadata and assets
 * appear on a later sync.
 *
 * [archiveFullPage] additionally stores a `fullPageArchive` asset, [storePdf] a `pdf` asset.
 * These map to the "Refresh" / "Preserve offline archive" / "Preserve as PDF" actions in
 * the Karakeep web UI.
 *
 * tRPC mutation: bookmarks.recrawlBookmark
 * POST /api/trpc/bookmarks.recrawlBookmark?batch=1
 *
 * @throws UnsupportedServerActionException if the server has no such tRPC route.
 */
internal suspend fun RemoteApiContext.recrawlBookmark(
    server: Server,
    bookmarkId: String,
    archiveFullPage: Boolean,
    storePdf: Boolean
): Unit = guardedCall {
    val trpcBase = getTrpcBaseUrl(server)
    val url = "$trpcBase/api/trpc/bookmarks.recrawlBookmark?batch=1"
    val response: HttpResponse = try {
        client.post(url) {
            header("Authorization", getAuth(server))
            contentType(ContentType.Application.Json)
            setBody(TrpcPayloadUtils.recrawlBookmark(bookmarkId, archiveFullPage, storePdf))
        }
    } catch (e: Exception) {
        throw ApiException("Error requesting recrawl: ${e.message}", e)
    }

    if (response.status.isSuccess()) return@guardedCall

    val errorBody = try { response.bodyAsText() } catch (_: Exception) { "" }
    // tRPC answers an unknown procedure with 404 "No procedure found on path …". A missing
    // bookmark is also a 404, so match on the message rather than the status alone.
    if (errorBody.contains("No procedure found", ignoreCase = true)) {
        throw UnsupportedServerActionException(
            "This Karakeep server doesn't support re-crawling from the app"
        )
    }
    throw ApiException("HTTP ${response.status}: $errorBody")
}

/**
 * Ask the server to generate an AI summary for a bookmark.
 *
 * Unlike the crawl jobs above this is **synchronous** — Karakeep runs the inference inline and
 * answers with the updated record — so the call can block for as long as the model takes.
 *
 * The summary lands in the bookmark's `summary` field, which is separate from `description`:
 * the crawler reads that one from the page's meta tags and the inference worker never writes it.
 *
 * POST /bookmarks/{bookmarkId}/summarize
 *
 * @throws UnsupportedServerActionException if the instance has no inference client configured.
 */
internal suspend fun RemoteApiContext.summarizeBookmark(server: Server, bookmarkId: String): SummarizeResult = guardedCall {
    try {
        val dto = bookmarksApi(server).bookmarksBookmarkIdSummarizePost(bookmarkId).checkedBody()
        SummarizeResult(
            summary = dto.summary,
            summarizationStatus = dto.summarizationStatus?.value
        )
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        if (e.message?.contains(NO_INFERENCE_CLIENT_ERROR, ignoreCase = true) == true) {
            throw UnsupportedServerActionException(
                "This Karakeep server has no AI model configured"
            )
        }
        throw ApiException("Error summarizing bookmark: ${e.message}", e)
    }
}

/**
 * Ask the server to re-run AI tagging for a bookmark. The server enqueues an inference job,
 * so success here only means the request was accepted — the new tags appear on a later sync.
 *
 * tRPC mutation: admin.adminRetagBookmark
 * POST /api/trpc/admin.adminRetagBookmark?batch=1
 *
 * @throws UnsupportedServerActionException if the route is missing or the key is not an admin's.
 */
internal suspend fun RemoteApiContext.requestAiRetag(server: Server, bookmarkId: String): Unit = guardedCall {
    val trpcBase = getTrpcBaseUrl(server)
    val url = "$trpcBase/api/trpc/admin.adminRetagBookmark?batch=1"
    val response: HttpResponse = try {
        client.post(url) {
            header("Authorization", getAuth(server))
            contentType(ContentType.Application.Json)
            setBody(TrpcPayloadUtils.adminRetagBookmark(bookmarkId))
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        throw ApiException("Error requesting AI tagging: ${e.message}", e)
    }

    if (response.status.isSuccess()) return@guardedCall

    val errorBody = try { response.bodyAsText() } catch (_: Exception) { "" }
    // Two distinct "this server won't do it" cases: the route is absent, or the key belongs to
    // a plain user. Neither is worth retrying, and both read the same to the person asking.
    if (errorBody.contains(UNKNOWN_TRPC_PROCEDURE_ERROR, ignoreCase = true)) {
        throw UnsupportedServerActionException(
            "This Karakeep server doesn't support re-running AI tagging from the app"
        )
    }
    if (response.status.value == 401 || response.status.value == 403) {
        throw UnsupportedServerActionException(
            "Re-running AI tagging needs an admin account on this server"
        )
    }
    throw ApiException("HTTP ${response.status}: $errorBody", statusCode = response.status.value)
}

/**
 * Whether this server's API key belongs to an admin.
 *
 * Neither `GET /users/me` nor tRPC `users.whoami` reports the user's role, so the only way to
 * find out is to call something only an admin may call. `admin.getAdminNoticies` is the
 * cheapest such route — its handler returns an empty object and touches no data.
 *
 * A rejected probe answers false; a transport failure throws, so a caller can leave the
 * capability unknown rather than caching a wrong "not an admin".
 */
internal suspend fun RemoteApiContext.isServerAdmin(server: Server): Boolean = guardedCall {
    val trpcBase = getTrpcBaseUrl(server)
    val response: HttpResponse = try {
        client.get("$trpcBase/api/trpc/admin.getAdminNoticies") {
            header("Authorization", getAuth(server))
            parameter("batch", "1")
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        throw ApiException("Error checking admin status: ${e.message}", e)
    }
    response.status.isSuccess()
}

/** What [RemoteDataSource.summarizeBookmark] got back from the server. */
internal data class SummarizeResult(
    val summary: String?,
    val summarizationStatus: String?
)
