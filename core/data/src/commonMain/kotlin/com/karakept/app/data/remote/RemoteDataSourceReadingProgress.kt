package com.karakept.app.data.remote

import com.karakept.app.data.model.Server
import com.karakept.app.utils.AppLogger
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Karakeep's wording when reading progress is pushed for a bookmark that is not a link.
 * The only push rejection that is not worth retrying.
 */
private const val NON_LINK_PROGRESS_ERROR = "reading progress can only be saved"

/**
 * Bookmarks per batched reading-progress request. Conservative on purpose: the batch travels
 * in the query string and a default nginx refuses a request line much past 8KB, which is well
 * under what Karakeep's own web client allows itself. Twenty ids is ~2KB encoded and still
 * turns a thousand-bookmark pass from a thousand requests into fifty.
 */
internal const val READING_PROGRESS_BATCH_SIZE = 20

/**
 * Batched progress requests in flight at once. A pass covers hundreds of bookmarks; issued one
 * after another the round trips are the whole cost of it, and this is the same ceiling the
 * per-bookmark pull used before batching.
 */
private const val PROGRESS_REQUEST_CONCURRENCY = 5

/**
 * Push reading progress to server via tRPC.
 *
 * Returns true when the server stored the value, false only for the one rejection that
 * can never succeed on retry: Karakeep keeps reading progress for LINK bookmarks alone
 * and answers BAD_REQUEST for every other type. Any other non-2xx throws, so the pending
 * action survives for a retry instead of being dropped as if it had synced — a rejected
 * push used to leave the progress local forever with nothing to show for it.
 *
 * tRPC mutation: bookmarks.updateReadingProgress
 * POST /api/trpc/bookmarks.updateReadingProgress?batch=1
 */
internal suspend fun RemoteApiContext.updateReadingProgress(
    server: Server,
    bookmarkId: String,
    progressPercent: Int
): Boolean = guardedCall {
    val response: HttpResponse = try {
        val trpcBase = getTrpcBaseUrl(server)
        val url = "$trpcBase/api/trpc/bookmarks.updateReadingProgress?batch=1"
        val body = TrpcPayloadUtils.updateReadingProgress(bookmarkId, progressPercent)
        client.post(url) {
            header("Authorization", getAuth(server))
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        throw ApiException("Error updating reading progress: ${e.message}", e)
    }

    if (response.status.isSuccess()) return@guardedCall true

    val errorBody = try { response.bodyAsText() } catch (_: Exception) { "" }
    if (response.status.value == 400 && errorBody.contains(NON_LINK_PROGRESS_ERROR, ignoreCase = true)) {
        AppLogger.d(
            "RemoteDataSource",
            "Server does not keep reading progress for bookmark $bookmarkId (not a link)"
        )
        return@guardedCall false
    }

    throw ApiException(
        "Error updating reading progress: HTTP ${response.status.value}: $errorBody",
        statusCode = response.status.value
    )
}

/**
 * Fetch reading progress for several bookmarks, in as few requests as the URL budget allows.
 *
 * Returns the progress percent per bookmark id. A `null` entry means the server holds no
 * progress for that bookmark; an id **missing** from the map was never answered — a chunk
 * that failed — and must be treated as "we never found out", not as "nothing stored", so
 * the caller holds its cursor rather than marking the row as asked.
 *
 * The batch travels in the query string, and how long a URL a deployment accepts is the
 * reverse proxy's business rather than the server's — Karakeep's own client caps itself at
 * 14000 characters, while a default nginx rejects rather less. [chunkSize] therefore starts
 * small and halves on the two statuses that mean "your request line is too long", so a
 * strict proxy costs a retry rather than the whole feature.
 *
 * Chunks run concurrently up to [PROGRESS_REQUEST_CONCURRENCY]: a pass covers hundreds of
 * bookmarks, and issued one after another the round trips are the whole cost of the pass.
 */
internal suspend fun RemoteApiContext.getReadingProgressBatch(
    server: Server,
    bookmarkIds: List<String>,
    chunkSize: Int
): Map<String, Int?> {
    if (bookmarkIds.isEmpty()) return emptyMap()
    var size = chunkSize.coerceAtLeast(1)
    while (true) {
        val chunks = bookmarkIds.chunked(size)
        val answers = fetchChunksConcurrently(server, chunks)
        // A proxy rejects on the request line alone, so every chunk of this size fails the
        // same way. Halving and starting over costs a round trip and gets the whole pass,
        // where retrying chunk by chunk would pay that cost once per chunk.
        if (answers == null && size > 1) {
            size /= 2
            AppLogger.d("RemoteDataSource", "Progress batch too long, retrying at $size")
            continue
        }
        return answers ?: emptyMap()
    }
}

/**
 * Runs [chunks] with bounded concurrency, merging the answers.
 *
 * Returns null when a chunk was refused for being too long — the caller retries smaller.
 * Any other failure omits just that chunk, leaving its ids out of the map: one unreachable
 * chunk must not cost the answers the rest of the pass already paid for.
 */
private suspend fun RemoteApiContext.fetchChunksConcurrently(
    server: Server,
    chunks: List<List<String>>
): Map<String, Int?>? = coroutineScope {
    val semaphore = Semaphore(PROGRESS_REQUEST_CONCURRENCY)
    val outcomes = chunks.map { chunk ->
        async {
            semaphore.withPermit {
                try {
                    ChunkOutcome(chunk.zip(fetchReadingProgressChunk(server, chunk)).toMap())
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val tooLong = e is ApiException && e.statusCode in URL_TOO_LONG_STATUSES
                    if (!tooLong) {
                        AppLogger.d("RemoteDataSource", "Progress chunk failed: ${e.message}")
                    }
                    ChunkOutcome(emptyMap(), urlTooLong = tooLong)
                }
            }
        }
    }.awaitAll()
    if (outcomes.any { it.urlTooLong }) null else outcomes.fold(emptyMap()) { acc, o -> acc + o.answers }
}

/** One chunk's answers, plus whether it was refused for being too long to send. */
private data class ChunkOutcome(
    val answers: Map<String, Int?>,
    val urlTooLong: Boolean = false
)

/** One batched request. Returns the answers positionally; entries may be null. */
private suspend fun RemoteApiContext.fetchReadingProgressChunk(
    server: Server,
    bookmarkIds: List<String>
): List<Int?> = guardedCall {
    val response: HttpResponse = try {
        val trpcBase = getTrpcBaseUrl(server)
        val path = TrpcPayloadUtils.batchPath("bookmarks.getReadingProgress", bookmarkIds.size)
        client.get("$trpcBase/api/trpc/$path") {
            header("Authorization", getAuth(server))
            parameter("batch", "1")
            parameter("input", TrpcPayloadUtils.getReadingProgressBatch(bookmarkIds))
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        throw ApiException("Error fetching reading progress: ${e.message}", e)
    }

    if (!response.status.isSuccess()) {
        val errorBody = try { response.bodyAsText() } catch (_: Exception) { "" }
        throw ApiException(
            "Error fetching reading progress: HTTP ${response.status.value}: $errorBody",
            statusCode = response.status.value
        )
    }

    try {
        val entries = trpcJson.parseToJsonElement(response.bodyAsText()).jsonArray
        // A batch answers positionally. An entry that carried an error rather than a
        // result reads as null here, which the caller treats as "not answered".
        bookmarkIds.indices.map { position ->
            entries.getOrNull(position)
                ?.jsonObject?.get("result")
                ?.jsonObject?.get("data")
                ?.jsonObject?.get("json")
                ?.jsonObject?.get("readingProgressPercent")
                ?.jsonPrimitive?.intOrNull
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        throw ApiException("Error parsing reading progress response: ${e.message}", e)
    }
}
