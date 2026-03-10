package com.karakept.app.utils

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

actual object BackupCrypto {
    private const val PBKDF2_ITERATIONS = 100_000
    private const val KEY_LENGTH_BYTES = 32
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val GCM_TAG_BITS = 128

    actual fun encrypt(plaintext: ByteArray, pin: String): String {
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(pin, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)
        return Base64.getEncoder().encodeToString(salt + iv + ciphertext)
    }

    actual fun decrypt(encoded: String, pin: String): ByteArray {
        val combined = Base64.getDecoder().decode(encoded)
        require(combined.size > SALT_LENGTH + IV_LENGTH) { "Invalid encrypted data: too short" }
        val salt = combined.copyOfRange(0, SALT_LENGTH)
        val iv = combined.copyOfRange(SALT_LENGTH, SALT_LENGTH + IV_LENGTH)
        val ciphertext = combined.copyOfRange(SALT_LENGTH + IV_LENGTH, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(pin, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    actual fun hashPin(pin: String): String {
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(pin, salt)
        val enc = Base64.getEncoder()
        return "${enc.encodeToString(salt)}:${enc.encodeToString(hash)}"
    }

    actual fun verifyPin(pin: String, storedHash: String): Boolean {
        val parts = storedHash.split(":")
        if (parts.size != 2) return false
        return try {
            val dec = Base64.getDecoder()
            val salt = dec.decode(parts[0])
            val expected = dec.decode(parts[1])
            MessageDigest.isEqual(expected, pbkdf2(pin, salt))
        } catch (e: Exception) {
            false
        }
    }

    private fun deriveKey(pin: String, salt: ByteArray): SecretKeySpec =
        SecretKeySpec(pbkdf2(pin, salt), "AES")

    private fun pbkdf2(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BYTES * 8)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }
}
