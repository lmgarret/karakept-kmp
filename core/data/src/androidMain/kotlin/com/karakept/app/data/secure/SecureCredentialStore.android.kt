package com.karakept.app.data.secure

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.karakept.app.data.local.AndroidContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android implementation using Android Keystore AES-GCM encryption.
 * Encrypted values are stored as Base64 in SharedPreferences.
 */
actual class SecureCredentialStore actual constructor() {

    private companion object {
        const val KEY_ALIAS = "karakept_credential_key"
        const val PREFS_NAME = "karakept_secure_credentials"
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_LENGTH = 128
    }

    private val prefs: SharedPreferences by lazy {
        AndroidContext.context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        keyStore.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(spec)
            generateKey()
        }
    }

    actual fun storeApiKey(serverId: String, apiKey: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))
        val combined = cipher.iv + encrypted
        prefs.edit().putString(serverId, Base64.encodeToString(combined, Base64.NO_WRAP)).apply()
    }

    actual fun getApiKey(serverId: String): String? {
        val encoded = prefs.getString(serverId, null) ?: return null
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    actual fun removeApiKey(serverId: String) {
        prefs.edit().remove(serverId).apply()
    }

    actual fun hasKey(serverId: String): Boolean {
        return prefs.contains(serverId)
    }
}
