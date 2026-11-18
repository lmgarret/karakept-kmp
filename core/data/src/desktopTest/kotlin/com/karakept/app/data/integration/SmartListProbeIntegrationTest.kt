package com.karakept.app.data.integration

import com.karakept.api.model.KarakeepList
import com.karakept.app.utils.parseIsoToEpochMillis
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The per-bookmark smart-list probe against a real Karakeep: a smart list excluding a manual list
 * holds a bookmark until the bookmark joins that list, and holds it again once it leaves.
 *
 * Requires a running Karakeep backend (managed by [BaseDockerIntegrationTest]).
 */
class SmartListProbeIntegrationTest : BaseDockerIntegrationTest() {

    private fun seedSmartList(name: String, query: String): String {
        val body = """{"0": {"json": {"name": "$name", "icon": "🧠", "type": "smart", "query": "$query"}}}"""
        val output = postJson("$baseUrl/api/trpc/lists.create?batch=1", body, apiKey)
        return "\"id\":\\s*\"([^\"]+)\"".toRegex().find(output)?.groupValues?.get(1)
            ?: throw IllegalStateException("Failed to seed smart list: $output")
    }

    private suspend fun listIdsOf(remoteId: String): Set<String> =
        db.bookmarkDao().getBookmarkByRemoteId(remoteId, testServer.id)!!
            .listIds.split(",").filter { it.isNotBlank() }.toSet()

    @Test
    fun probeFollowsAnExclusionAcrossAnAddAndItsUndo() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")
        val stamp = System.currentTimeMillis()
        val manualName = "Work$stamp"
        val manualId = seedListViaTrpc(baseUrl, apiKey, manualName)
        val smartId = seedSmartList("NotWork$stamp", "-list:$manualName")
        val lists = listOf(
            KarakeepList(id = manualId, name = manualName, type = KarakeepList.Type.MANUAL),
            KarakeepList(id = smartId, name = "NotWork$stamp", type = KarakeepList.Type.SMART, query = "-list:$manualName")
        )

        val remoteId = seedBookmarkViaTrpc(baseUrl, apiKey, "https://example.com/probe-$stamp")
        val dto = remoteDataSource.fetchBookmark(testServer, remoteId, includeContent = false)
        val createdAt = assertNotNull(parseIsoToEpochMillis(dto.createdAt))
        assertEquals(0L, createdAt % 1000, "Karakeep reports createdAt in whole seconds")
        val local = insertLocalBookmark(remoteId)
        db.bookmarkDao().insertBookmark(local.copy(createdAt = createdAt, listIds = ""))

        // Not cached as a member yet, but the server's query includes it.
        bookmarkActionsRepository.reconcileSmartListsAfterEdit(testServer.id, listOf(remoteId), lists).join()
        assertEquals(setOf(smartId), listIdsOf(remoteId))

        bookmarkActionsRepository.addToList(remoteId, testServer.id, manualId)
        bookmarkActionsRepository.reconcileSmartListsAfterEdit(testServer.id, listOf(remoteId), lists).join()
        assertEquals(setOf(manualId), listIdsOf(remoteId))

        bookmarkActionsRepository.removeFromList(remoteId, testServer.id, manualId)
        bookmarkActionsRepository.reconcileSmartListsAfterEdit(testServer.id, listOf(remoteId), lists).join()
        assertEquals(setOf(smartId), listIdsOf(remoteId))

        assertTrue(bookmarkActionsRepository.smartListsNeedingRefresh.value.isEmpty())
    }
}
