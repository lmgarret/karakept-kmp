package com.karakept.app.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [NoCaseCollationUtils] stands in for SQLite's `COLLATE NOCASE`, so what it must get right is
 * not "a sensible order" but *that* order — including the places it is unintuitive.
 *
 * The cases below are the ones where the obvious Kotlin spelling (`lowercase()` compared with
 * `compareTo`) gives a different answer from the database, which is what put rows at the wrong
 * index in a title-sorted list.
 */
class NoCaseCollationUtilsTest {

    private fun assertOrders(first: String, second: String) {
        assertTrue(
            NoCaseCollationUtils.compare(first, second) < 0,
            "'$first' must sort before '$second'"
        )
        assertTrue(
            NoCaseCollationUtils.compare(second, first) > 0,
            "and '$second' after '$first' — the comparator must be antisymmetric"
        )
    }

    private fun assertTies(first: String, second: String) {
        assertEquals(
            0,
            NoCaseCollationUtils.compare(first, second),
            "'$first' and '$second' must compare equal"
        )
    }

    @Test
    fun `ASCII letters fold, so case does not order them`() {
        assertTies("Alpha", "alpha")
        assertTies("ALPHA", "alpha")
        assertOrders("alpha", "Beta")
        assertOrders("Alpha", "beta")
    }

    @Test
    fun `non-ASCII letters do not fold`() {
        // The case that broke title sorting: Kotlin's lowercase() folds these into one key and
        // SQLite keeps them apart, so the window and the query disagreed about the order.
        assertOrders("Ωmega", "ωmega")
        assertOrders("Über", "über")
        assertOrders("Ångström", "ångström")
    }

    @Test
    fun `characters order by code point, not by UTF-16 code unit`() {
        // A supplementary character is a surrogate pair, whose leading unit (U+D83D here) is
        // *below* every BMP character from U+E000 up — so UTF-16 order puts an emoji first and
        // code-point order puts it last. SQLite compares UTF-8, which is code-point order.
        assertOrders("�replacement", "🚀 rocket")
        assertOrders("日本語", "🚀 rocket")
    }

    @Test
    fun `a prefix sorts before the string extending it`() {
        assertOrders("alpha", "alphabet")
        assertOrders("", "a")
        assertTies("", "")
    }

    @Test
    fun `folding moves the punctuation between the two alphabets below both`() {
        assertOrders(" leading space", "alpha")
        // '[' (0x5B) sits between 'Z' (0x5A) and 'a' (0x61), so unfolded it separates the two
        // alphabets. Folding A-Z up to 0x61 leaves it below every letter of either case.
        assertOrders("[bracket", "Alpha")
        assertOrders("[bracket", "alpha")
    }

    @Test
    fun `an unpaired surrogate compares without running off the end`() {
        // Not reachable through SQLite, which stores UTF-8, but the comparator must not throw.
        assertTies("\uD83D", "\uD83D")
        assertOrders("a", "\uD83D")
    }
}
