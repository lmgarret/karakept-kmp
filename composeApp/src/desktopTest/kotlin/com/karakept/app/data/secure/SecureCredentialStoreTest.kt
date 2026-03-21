package com.karakept.app.data.secure

import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the JVM/Desktop [SecureCredentialStore] PKCS12 implementation.
 *
 * Each test uses a fresh keystore file which is cleaned up in @After.
 */
class SecureCredentialStoreTest {

    private val keystoreFile = File(System.getProperty("user.home"), ".karakept/credentials.p12")
    private lateinit var store: SecureCredentialStore

    @Before
    fun setUp() {
        // Remove any leftover keystore from previous test runs
        keystoreFile.delete()
        store = SecureCredentialStore()
    }

    @After
    fun tearDown() {
        keystoreFile.delete()
    }

    @Test
    fun testStoreAndRetrieveApiKey() {
        store.storeApiKey("server-1", "my-secret-key-123")
        val retrieved = store.getApiKey("server-1")
        assertEquals("my-secret-key-123", retrieved)
    }

    @Test
    fun testRetrieveNonExistentKey() {
        val result = store.getApiKey("non-existent-server")
        assertNull(result)
    }

    @Test
    fun testRemoveApiKey() {
        store.storeApiKey("server-2", "key-to-remove")
        store.removeApiKey("server-2")
        val result = store.getApiKey("server-2")
        assertNull(result)
    }

    @Test
    fun testHasKey() {
        assertFalse(store.hasKey("server-3"))
        store.storeApiKey("server-3", "some-key")
        assertTrue(store.hasKey("server-3"))
        store.removeApiKey("server-3")
        assertFalse(store.hasKey("server-3"))
    }

    @Test
    fun testOverwriteApiKey() {
        store.storeApiKey("server-4", "original-key")
        store.storeApiKey("server-4", "updated-key")
        val retrieved = store.getApiKey("server-4")
        assertEquals("updated-key", retrieved)
    }
}
