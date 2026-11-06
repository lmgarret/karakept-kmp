package com.karakept.app.data.secure

import java.io.File
import java.security.KeyStore
import javax.crypto.spec.SecretKeySpec

/**
 * Desktop/JVM implementation using a PKCS12 KeyStore file.
 * API keys are stored as AES SecretKeyEntry values in `~/.karakept/credentials.p12`.
 */
actual class SecureCredentialStore actual constructor() {

    private val keystoreFile = File(System.getProperty("user.home"), ".karakept/credentials.p12")

    private fun derivePassword(): CharArray {
        val seed = "${System.getProperty("user.name")}:karakept-credential-store"
        return seed.hashCode().toString().toCharArray()
    }

    private fun loadKeyStore(): KeyStore {
        val password = derivePassword()
        val ks = KeyStore.getInstance("PKCS12")
        if (keystoreFile.exists()) {
            keystoreFile.inputStream().use { ks.load(it, password) }
        } else {
            ks.load(null, password)
        }
        return ks
    }

    private fun saveKeyStore(ks: KeyStore) {
        keystoreFile.parentFile?.mkdirs()
        val password = derivePassword()
        keystoreFile.outputStream().use { ks.store(it, password) }
    }

    actual fun storeApiKey(serverId: String, apiKey: String) {
        val ks = loadKeyStore()
        val secret = SecretKeySpec(apiKey.toByteArray(Charsets.UTF_8), "AES")
        val entry = KeyStore.SecretKeyEntry(secret)
        ks.setEntry(serverId, entry, KeyStore.PasswordProtection(derivePassword()))
        saveKeyStore(ks)
    }

    actual fun getApiKey(serverId: String): String? {
        val ks = loadKeyStore()
        if (!ks.containsAlias(serverId)) return null
        val entry = ks.getEntry(serverId, KeyStore.PasswordProtection(derivePassword())) as? KeyStore.SecretKeyEntry
            ?: return null
        return String(entry.secretKey.encoded, Charsets.UTF_8)
    }

    actual fun removeApiKey(serverId: String) {
        val ks = loadKeyStore()
        if (ks.containsAlias(serverId)) {
            ks.deleteEntry(serverId)
            saveKeyStore(ks)
        }
    }

    actual fun hasKey(serverId: String): Boolean {
        val ks = loadKeyStore()
        return ks.containsAlias(serverId)
    }
}
