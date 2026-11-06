package com.karakept.app.data.secure

/**
 * Platform-specific encrypted credential store for API keys.
 *
 * Android: AES-GCM via Android Keystore + SharedPreferences.
 * Desktop: PKCS12 KeyStore file.
 */
expect class SecureCredentialStore() {
    fun getApiKey(serverId: String): String?
    fun storeApiKey(serverId: String, apiKey: String)
    fun removeApiKey(serverId: String)
    fun hasKey(serverId: String): Boolean
}
