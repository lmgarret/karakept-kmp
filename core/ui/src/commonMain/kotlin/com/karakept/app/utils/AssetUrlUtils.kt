package com.karakept.app.utils

object AssetUrlUtils {
    /**
     * Constructs the full URL for accessing an asset via the API.
     * Format: {serverUrl}/api/v1/assets/{assetId}
     */
    fun getAssetUrl(serverUrl: String, assetId: String): String {
        val baseUrl = serverUrl.trimEnd('/')
        return "$baseUrl/api/v1/assets/$assetId"
    }

    /**
     * Checks if a URL is an asset URL that requires authentication
     */
    fun isAssetUrl(url: String): Boolean {
        return url.contains("/api/v1/assets/")
    }
}
