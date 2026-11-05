package com.karakept.app.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine

// The production client config with no wait between retries: the retry policy is still
// exercised, but each retried request no longer sleeps through seconds of real backoff.
internal fun karakeptTestClient(engine: MockEngine): HttpClient =
    HttpClient(engine) { applyKarakeptClientDefaults(retryDelay = { delayMillis { 0L } }) }
