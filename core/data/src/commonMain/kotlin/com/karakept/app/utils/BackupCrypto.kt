package com.karakept.app.utils

/**
 * Platform-provided AES-256-GCM encryption/decryption for backup files.
 *
 * Key derivation: PBKDF2WithHmacSHA256, 100,000 iterations, 256-bit key.
 * Encryption:     AES-256-GCM, random 12-byte IV, 128-bit authentication tag.
 *
 * Binary layout returned by [encrypt]: `[16-byte salt | 12-byte IV | N-byte ciphertext+tag]`,
 * Base64-encoded as a single string.  Pass the same string to [decrypt].
 */
expect object BackupCrypto {
    /**
     * Encrypts [plaintext] using [pin] (4–6 ASCII digits).
     * Returns a Base64 string encoding `salt(16) || iv(12) || ciphertext+GCM-tag`.
     */
    fun encrypt(plaintext: ByteArray, pin: String): String

    /**
     * Decrypts [encoded] (produced by [encrypt]) using [pin].
     *
     * @throws IllegalArgumentException if the encoded data is malformed.
     * @throws Exception (e.g. `AEADBadTagException`) when the PIN is wrong or the data is corrupted.
     */
    fun decrypt(encoded: String, pin: String): ByteArray

    /**
     * Returns a storable hash string for [pin].
     * Format: `"<base64salt>:<base64hash>"` – both components are 32 bytes derived via
     * PBKDF2WithHmacSHA256 with 100,000 iterations.
     */
    fun hashPin(pin: String): String

    /**
     * Returns `true` iff [pin] matches the [storedHash] produced by [hashPin].
     * Uses a constant-time comparison to prevent timing side-channel attacks.
     */
    fun verifyPin(pin: String, storedHash: String): Boolean
}
