package com.karakept.app.data.remote

import com.karakept.app.data.model.SanitizeJob
import com.karakept.app.data.model.SanitizeJobStatus
import com.karakept.app.data.model.SanitizeOutcome
import com.karakept.app.data.model.SidecarCapabilities
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The sidecar answered, but says it cannot do this work — no model configured, or it is off. */
class SidecarUnavailableException(message: String) : Exception(message)

/**
 * Talks to a sidecar service at a user-supplied base URL.
 *
 * Deliberately not a method on [RemoteDataSource]: that class wraps every call in an offline-mode
 * guard aimed at Karakeep and rewrites every URL through Karakeep's `/api/v1` path rules. A sidecar
 * is a different host with its own token, so it gets its own data source — but it shares the one
 * injected [HttpClient], and with it the app's timeouts, JSON setup and GET-only retry policy.
 */
class SidecarDataSource(private val client: HttpClient) {

    suspend fun fetchCapabilities(baseUrl: String, token: String?): SidecarCapabilities {
        val response = request(baseUrl, token, "capabilities", post = false)
        if (!response.status.isSuccess()) {
            throw ApiException(
                "Sidecar capabilities failed: HTTP ${response.status.value}",
                statusCode = response.status.value
            )
        }
        val dto: CapabilitiesDto = decode(response) { "Unreadable sidecar capabilities: $it" }
        return SidecarCapabilities(
            service = dto.service,
            llmEnabled = dto.llmEnabled,
            assetName = dto.assetName ?: com.karakept.app.data.model.SANITIZED_ASSET_NAME,
            maxContentSize = dto.maxContentSize,
            dryRun = dto.dryRun
        )
    }

    /**
     * Ask for [bookmarkId] to be cleaned now.
     *
     * The service answers 202 with a fresh job, or 200 with the one already running for that
     * bookmark — both are "it is being worked on", so both return a job to poll. A 503 means this
     * deployment has no model and would never produce anything, which is worth telling the user
     * rather than polling for an asset that cannot arrive.
     */
    suspend fun requestSanitize(baseUrl: String, token: String?, bookmarkId: String): SanitizeJob {
        val response = request(baseUrl, token, "sanitize/$bookmarkId", post = true)
        if (response.status.value == 503) {
            throw SidecarUnavailableException(
                "The cleanup service has no model configured, so it can't clean anything"
            )
        }
        if (!response.status.isSuccess()) {
            throw ApiException(
                "Sidecar sanitize failed: HTTP ${response.status.value}",
                statusCode = response.status.value
            )
        }
        return decodeJob(response, bookmarkId)
    }

    /**
     * The most recent job for [bookmarkId], or null when the service has not been asked about it.
     *
     * Null is not the same as "nothing happened": jobs are held in memory for half an hour and lost
     * on restart, so a 404 after a sidecar restart means the answer is simply unknown now.
     */
    suspend fun fetchSanitizeStatus(baseUrl: String, token: String?, bookmarkId: String): SanitizeJob? {
        val response = request(baseUrl, token, "sanitize/$bookmarkId", post = false)
        if (response.status.value == 404) return null
        if (!response.status.isSuccess()) {
            throw ApiException(
                "Sidecar job lookup failed: HTTP ${response.status.value}",
                statusCode = response.status.value
            )
        }
        return decodeJob(response, bookmarkId)
    }

    private suspend fun request(
        baseUrl: String,
        token: String?,
        path: String,
        post: Boolean
    ): HttpResponse {
        val url = "${apiBase(baseUrl)}/$path"
        return try {
            if (post) {
                client.post(url) { token?.let { header("Authorization", "Bearer $it") } }
            } else {
                client.get(url) { token?.let { header("Authorization", "Bearer $it") } }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ApiException("Couldn't reach the cleanup service: ${e.message}", e)
        }
    }

    private suspend fun decodeJob(response: HttpResponse, bookmarkId: String): SanitizeJob {
        val dto: JobDto = decode(response) { "Unreadable sidecar job: $it" }
        return SanitizeJob(
            jobId = dto.jobId ?: "",
            bookmarkId = dto.bookmarkId ?: bookmarkId,
            // An unrecognised status is treated as still running: the poll loop then keeps asking
            // rather than reporting a result the service never gave.
            status = SanitizeJobStatus.fromWire(dto.status) ?: SanitizeJobStatus.RUNNING,
            outcome = SanitizeOutcome.fromWire(dto.outcome),
            error = dto.error
        )
    }

    private suspend inline fun <reified T> decode(
        response: HttpResponse,
        message: (String?) -> String
    ): T = try {
        response.body()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw ApiException(message(e.message), e)
    }

    private companion object {
        /**
         * The sidecar serves its client API under `/api/v1`. Accept a base URL given either way —
         * a user who pastes the full API URL should not get `/api/v1/api/v1`.
         */
        fun apiBase(baseUrl: String): String {
            val trimmed = baseUrl.trim().trimEnd('/')
            return if (trimmed.endsWith("/api/v1")) trimmed else "$trimmed/api/v1"
        }
    }
}

@Serializable
private data class CapabilitiesDto(
    val service: String? = null,
    @SerialName("llmEnabled") val llmEnabled: Boolean = false,
    @SerialName("maxContentSize") val maxContentSize: String? = null,
    @SerialName("assetName") val assetName: String? = null,
    @SerialName("dryRun") val dryRun: Boolean = false
)

@Serializable
private data class JobDto(
    @SerialName("jobId") val jobId: String? = null,
    @SerialName("bookmarkId") val bookmarkId: String? = null,
    val status: String? = null,
    val outcome: String? = null,
    val error: String? = null
)
