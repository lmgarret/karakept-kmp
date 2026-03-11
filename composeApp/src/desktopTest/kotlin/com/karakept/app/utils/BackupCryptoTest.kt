package com.karakept.app.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/**
 * Tests for [BackupCrypto] – runs on the desktop (JVM) actual implementation.
 */
class BackupCryptoTest {

    // ── encrypt / decrypt ─────────────────────────────────────────────────────

    @Test
    fun `encrypt then decrypt round-trips plaintext`() {
        val plaintext = "Hello, KMP backup!".encodeToByteArray()
        val pin = "1234"
        val encrypted = BackupCrypto.encrypt(plaintext, pin)
        val decrypted = BackupCrypto.decrypt(encrypted, pin)
        assertEquals("Hello, KMP backup!", decrypted.decodeToString())
    }

    @Test
    fun `encrypt produces different ciphertext on each call (random IV)`() {
        val plaintext = "same plaintext".encodeToByteArray()
        val pin = "9999"
        val enc1 = BackupCrypto.encrypt(plaintext, pin)
        val enc2 = BackupCrypto.encrypt(plaintext, pin)
        assertNotEquals(enc1, enc2, "Two encryptions of the same data should differ due to random IV/salt")
    }

    @Test
    fun `decrypt throws on wrong PIN`() {
        val plaintext = "secret".encodeToByteArray()
        val encrypted = BackupCrypto.encrypt(plaintext, "1234")
        assertFailsWith<Exception> {
            BackupCrypto.decrypt(encrypted, "5678")
        }
    }

    @Test
    fun `decrypt throws on corrupted ciphertext`() {
        val plaintext = "secret".encodeToByteArray()
        val encrypted = BackupCrypto.encrypt(plaintext, "1234")
        // Flip a byte in the middle of the Base64 string
        val corrupted = encrypted.replaceRange(encrypted.length / 2, encrypted.length / 2 + 1, "X")
        assertFailsWith<Exception> {
            BackupCrypto.decrypt(corrupted, "1234")
        }
    }

    @Test
    fun `encrypt handles empty plaintext`() {
        val plaintext = ByteArray(0)
        val pin = "123456"
        val encrypted = BackupCrypto.encrypt(plaintext, pin)
        val decrypted = BackupCrypto.decrypt(encrypted, pin)
        assertEquals(0, decrypted.size)
    }

    @Test
    fun `encrypt handles large plaintext`() {
        val plaintext = "A".repeat(100_000).encodeToByteArray()
        val pin = "2468"
        val encrypted = BackupCrypto.encrypt(plaintext, pin)
        val decrypted = BackupCrypto.decrypt(encrypted, pin)
        assertEquals(100_000, decrypted.size)
        assertTrue(decrypted.decodeToString().all { it == 'A' })
    }

    @Test
    fun `encrypt accepts 4-digit PIN`() {
        val plaintext = "test".encodeToByteArray()
        val encrypted = BackupCrypto.encrypt(plaintext, "1234")
        val decrypted = BackupCrypto.decrypt(encrypted, "1234")
        assertEquals("test", decrypted.decodeToString())
    }

    @Test
    fun `encrypt accepts 6-digit PIN`() {
        val plaintext = "test".encodeToByteArray()
        val encrypted = BackupCrypto.encrypt(plaintext, "123456")
        val decrypted = BackupCrypto.decrypt(encrypted, "123456")
        assertEquals("test", decrypted.decodeToString())
    }

    // ── hashPin / verifyPin ───────────────────────────────────────────────────

    @Test
    fun `hashPin produces non-empty string with expected format`() {
        val hash = BackupCrypto.hashPin("1234")
        assertTrue(hash.contains(":"), "Hash should contain ':' separator between salt and key")
        val parts = hash.split(":")
        assertEquals(2, parts.size)
        assertTrue(parts[0].isNotEmpty())
        assertTrue(parts[1].isNotEmpty())
    }

    @Test
    fun `hashPin produces different results each call (random salt)`() {
        val hash1 = BackupCrypto.hashPin("1234")
        val hash2 = BackupCrypto.hashPin("1234")
        assertNotEquals(hash1, hash2, "Each hashPin call should produce a unique salt")
    }

    @Test
    fun `verifyPin returns true for correct PIN`() {
        val hash = BackupCrypto.hashPin("5678")
        assertTrue(BackupCrypto.verifyPin("5678", hash))
    }

    @Test
    fun `verifyPin returns false for wrong PIN`() {
        val hash = BackupCrypto.hashPin("5678")
        assertFalse(BackupCrypto.verifyPin("1234", hash))
    }

    @Test
    fun `verifyPin returns false for malformed hash`() {
        assertFalse(BackupCrypto.verifyPin("1234", "not-a-valid-hash"))
        assertFalse(BackupCrypto.verifyPin("1234", ""))
        assertFalse(BackupCrypto.verifyPin("1234", "abc"))
    }

    @Test
    fun `verifyPin is consistent across multiple calls`() {
        val hash = BackupCrypto.hashPin("9999")
        assertTrue(BackupCrypto.verifyPin("9999", hash))
        assertTrue(BackupCrypto.verifyPin("9999", hash))
        assertFalse(BackupCrypto.verifyPin("0000", hash))
    }
}
