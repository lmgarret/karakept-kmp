package com.karakept.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SmartListProbeUtilsTest {

    // 2024-05-19T12:34:56.000Z
    private val createdAt = 1_716_122_096_000L

    @Test
    fun `cursor matches the server's own nextCursor format`() {
        // adaptPagination: `${id}_${createdAt.toISOString()}` — always three fraction digits.
        assertEquals("abc123_2024-05-19T12:34:56.000Z", SmartListProbeUtils.cursorFor("abc123", createdAt))
    }

    @Test
    fun `cursor pads every field`() {
        // 2001-02-03T04:05:06.000Z
        assertEquals("id_2001-02-03T04:05:06.000Z", SmartListProbeUtils.cursorFor("id", 981_173_106_000L))
    }

    @Test
    fun `no cursor for a createdAt the server cannot have sent`() {
        // The server stores whole seconds; a millisecond part means a local-clock fallback.
        assertNull(SmartListProbeUtils.cursorFor("abc", createdAt + 123))
        assertNull(SmartListProbeUtils.cursorFor("abc", 0L))
        assertNull(SmartListProbeUtils.cursorFor("abc", -1000L))
    }

    @Test
    fun `no cursor for an id the server would split`() {
        assertNull(SmartListProbeUtils.cursorFor("a_b", createdAt))
        assertNull(SmartListProbeUtils.cursorFor("", createdAt))
    }

    @Test
    fun `affected-query detector`() {
        val table = listOf(
            "list:Work" to true,
            "-list:Work" to true,
            "!list:Work" to true,
            "list:\"Read later\"" to true,
            "is:inlist" to true,
            "-is:inlist" to true,
            "!is:inlist" to true,
            "#tag and list:Inbox" to true,
            "(is:fav or list:Starred) -is:archived" to true,
            "LIST:Work" to true, // not a qualifier to the parser; a harmless false positive
            "title:\"my checklist: day 1\"" to true, // false positive, costs one request
            null to true,
            "" to true,
            "   " to true,
            "is:fav" to false,
            "#reading -is:archived" to false,
            "url:example.com after:2024-01-01" to false,
            "is:tagged" to false,
            "feed:\"Hacker News\"" to false,
        )
        table.forEach { (query, expected) ->
            assertEquals(expected, SmartListProbeUtils.canDependOnListMembership(query), "query=$query")
        }
    }

    @Test
    fun `first row being the bookmark means it is in the list`() {
        val rows = listOf(ProbeRow("abc", "2024-05-19T12:34:56.000Z"))
        assertEquals(true, SmartListProbeUtils.classifyPage("abc", createdAt, rows))
    }

    @Test
    fun `an empty page means it is not`() {
        assertEquals(false, SmartListProbeUtils.classifyPage("abc", createdAt, emptyList()))
    }

    @Test
    fun `an older first row means it is not`() {
        val rows = listOf(ProbeRow("zzz", "2024-05-19T12:34:55.000Z"))
        assertEquals(false, SmartListProbeUtils.classifyPage("abc", createdAt, rows))
    }

    @Test
    fun `a same-second first row with a smaller id means it is not`() {
        val rows = listOf(ProbeRow("abb", "2024-05-19T12:34:56.000Z"))
        assertEquals(false, SmartListProbeUtils.classifyPage("abc", createdAt, rows))
    }

    @Test
    fun `a first row sorting before the bookmark is not trusted`() {
        // Newer, or as new with a larger id: the cursor was not applied as expected.
        assertNull(SmartListProbeUtils.classifyPage("abc", createdAt, listOf(ProbeRow("x", "2024-05-19T12:34:57.000Z"))))
        assertNull(SmartListProbeUtils.classifyPage("abc", createdAt, listOf(ProbeRow("abd", "2024-05-19T12:34:56.000Z"))))
    }

    @Test
    fun `a first row that cannot be read is not trusted`() {
        assertNull(SmartListProbeUtils.classifyPage("abc", createdAt, listOf(ProbeRow("x", null))))
        assertNull(SmartListProbeUtils.classifyPage("abc", createdAt, listOf(ProbeRow("x", "yesterday"))))
        assertNull(SmartListProbeUtils.classifyPage("abc", createdAt, listOf(ProbeRow(null, "2024-05-19T12:34:55.000Z"))))
    }
}
