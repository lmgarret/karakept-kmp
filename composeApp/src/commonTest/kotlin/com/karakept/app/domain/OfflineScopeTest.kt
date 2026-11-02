package com.karakept.app.domain

import com.karakept.api.model.KarakeepList
import com.karakept.app.data.model.ListSettings
import com.karakept.app.data.model.ListSyncConfig
import com.karakept.app.data.model.SyncStrategy
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfflineScopeTest {

    private val lists = listOf(
        KarakeepList(id = "parent", name = "parent"),
        KarakeepList(id = "child", name = "child", parentId = "parent"),
        KarakeepList(id = "grandchild", name = "grandchild", parentId = "child"),
        KarakeepList(id = "other", name = "other")
    )

    private fun scope(
        strategy: SyncStrategy,
        selected: Set<String> = emptySet(),
        withChildren: Set<String> = emptySet(),
        listSettings: Map<String, ListSettings> = emptyMap()
    ) = OfflineScope.resolve(strategy, ListSyncConfig(selected, withChildren), listSettings, lists)

    @Test
    fun `all covers every bookmark, in a list or not`() {
        val scope = scope(SyncStrategy.ALL)
        assertTrue(scope.coversEverything)
        assertTrue(scope.covers("", opened = false))
        assertTrue(scope.covers("other", opened = false))
    }

    @Test
    fun `per list covers the selected lists and the descendants of a with-children one`() {
        val scope = scope(SyncStrategy.PER_LIST, selected = setOf("parent"), withChildren = setOf("parent"))
        assertTrue(scope.covers("parent", opened = false))
        assertTrue(scope.covers("grandchild", opened = false))
        assertTrue(scope.covers("other,child", opened = false))
        assertFalse(scope.covers("other", opened = false))
        assertFalse(scope.covers("", opened = true), "opening is not a list")
    }

    @Test
    fun `per list without children mode stops at the selected list`() {
        val scope = scope(SyncStrategy.PER_LIST, selected = setOf("parent"))
        assertFalse(scope.covers("child", opened = false))
    }

    @Test
    fun `per bookmark covers what was opened`() {
        val scope = scope(SyncStrategy.PER_BOOKMARK)
        assertTrue(scope.covers("", opened = true))
        assertFalse(scope.covers("", opened = false))
    }

    @Test
    fun `never covers only the lists set to sync offline, under any strategy`() {
        val settings = mapOf(
            "parent" to ListSettings(syncOffline = true, includeChildListBookmarks = true),
            "other" to ListSettings(syncOffline = false)
        )
        for (strategy in listOf(SyncStrategy.NEVER, SyncStrategy.PER_BOOKMARK, SyncStrategy.PER_LIST)) {
            val scope = scope(strategy, listSettings = settings)
            assertTrue(scope.covers("grandchild", opened = false), "$strategy")
            assertFalse(scope.covers("other", opened = false), "$strategy")
        }
        assertFalse(scope(SyncStrategy.NEVER).covers("parent", opened = true))
    }

    @Test
    fun `an offline list without children mode leaves its children out`() {
        val scope = scope(SyncStrategy.NEVER, listSettings = mapOf("parent" to ListSettings(syncOffline = true)))
        assertTrue(scope.covers("parent", opened = false))
        assertFalse(scope.covers("child", opened = false))
    }
}
