package com.karakept.app.data.integration

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for [com.karakept.app.data.repository.ListRepository].
 *
 * Verifies that the repository correctly fetches lists from the remote backend,
 * persists them to the local database, and exposes them via StateFlow.
 *
 * Requires a running Karakeep backend (managed by [BaseDockerIntegrationTest]).
 */
class ListRepositoryIntegrationTest : BaseDockerIntegrationTest() {

    @Test
    fun testRefreshLists_fetchesFromRemoteAndPersistsLocally() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val listName = "Refresh Test List ${System.currentTimeMillis()}"
        seedListViaTrpc(baseUrl, apiKey, listName)

        listRepository.refreshLists(testServer)

        val lists = listRepository.getListsOnce(testServer.id)
        assertTrue(lists.isNotEmpty(), "Lists should be loaded after refresh")
        assertTrue(lists.any { it.name == listName }, "Seeded list '$listName' should be in local DB")
    }

    @Test
    fun testRefreshLists_updatesStateFlow() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val listName = "StateFlow Test List ${System.currentTimeMillis()}"
        seedListViaTrpc(baseUrl, apiKey, listName)

        listRepository.refreshLists(testServer)

        val lists = listRepository.lists.value
        assertTrue(lists.isNotEmpty(), "StateFlow should be updated after refresh")
        assertTrue(lists.any { it.name == listName }, "Seeded list should be in StateFlow")
    }

    @Test
    fun testRefreshLists_multipleTimes_convergesWithServer() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val firstName = "First List ${System.currentTimeMillis()}"
        seedListViaTrpc(baseUrl, apiKey, firstName)

        listRepository.refreshLists(testServer)
        val afterFirstRefresh = listRepository.getListsOnce(testServer.id)
        val countAfterFirst = afterFirstRefresh.size

        val secondName = "Second List ${System.currentTimeMillis()}"
        seedListViaTrpc(baseUrl, apiKey, secondName)

        listRepository.refreshLists(testServer)
        val afterSecondRefresh = listRepository.getListsOnce(testServer.id)

        assertTrue(afterSecondRefresh.size >= countAfterFirst, "List count should not decrease on re-refresh")
        assertTrue(afterSecondRefresh.any { it.name == secondName }, "New list should appear after second refresh")
    }

    @Test
    fun testGetListsOnce_withNoLists_returnsEmpty() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        // Query a fresh server ID that has no lists yet
        val emptyServerId = "nonexistent-server-id"
        val lists = listRepository.getListsOnce(emptyServerId)

        assertTrue(lists.isEmpty(), "Should return empty list for unknown server ID")
    }

    @Test
    fun testClearListsForServer_removesLocalLists() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        seedListViaTrpc(baseUrl, apiKey, "List to clear ${System.currentTimeMillis()}")
        listRepository.refreshLists(testServer)

        val before = listRepository.getListsOnce(testServer.id)
        assertTrue(before.isNotEmpty(), "Should have lists before clearing")

        listRepository.clearListsForServer(testServer.id)

        val after = listRepository.getListsOnce(testServer.id)
        assertTrue(after.isEmpty(), "Lists should be empty after clearing")
    }

    @Test
    fun testRefreshLists_removesDeletedListsFromLocalDb() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        // Seed a list remotely
        val listName = "To Be Deleted ${System.currentTimeMillis()}"
        val listId = seedListViaTrpc(baseUrl, apiKey, listName)

        listRepository.refreshLists(testServer)
        val before = listRepository.getListsOnce(testServer.id)
        assertTrue(before.any { it.id == listId }, "List should exist locally after first refresh")

        // Delete the list via tRPC
        val deleteUrl = "$baseUrl/api/trpc/lists.deleteList?batch=1"
        val deleteBody = """{"0": {"json": {"listId": "$listId"}}}"""
        postJson(deleteUrl, deleteBody, apiKey)

        // Refresh again – the deleted list should be removed locally
        listRepository.refreshLists(testServer)
        val after = listRepository.getListsOnce(testServer.id)
        assertTrue(after.none { it.id == listId }, "Deleted list should be removed from local DB after re-sync")
    }
}
