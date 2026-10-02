package com.karakept.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerVersionUtilsTest {

    @Test
    fun parse_acceptsReleaseNumbersWithOrWithoutPrefix() {
        assertEquals(ServerVersion(0, 31, 0), ServerVersion.parse("0.31.0"))
        assertEquals(ServerVersion(0, 31, 2), ServerVersion.parse("v0.31.2"))
        assertEquals(ServerVersion(1, 2, 0), ServerVersion.parse("1.2"))
        assertEquals(ServerVersion(0, 32, 0), ServerVersion.parse(" 0.32.0-rc.1 "))
    }

    @Test
    fun parse_rejectsNonReleaseStrings() {
        assertNull(ServerVersion.parse("nightly"))
        assertNull(ServerVersion.parse("unknown"))
        assertNull(ServerVersion.parse(""))
    }

    @Test
    fun compare_isNumericNotLexicographic() {
        assertTrue(ServerVersion(0, 10, 0) > ServerVersion(0, 9, 9))
        assertTrue(ServerVersion(1, 0, 0) > ServerVersion(0, 99, 99))
        assertTrue(ServerVersion(0, 31, 1) > ServerVersion(0, 31, 0))
    }

    @Test
    fun evaluate_classifiesAgainstTheMinimum() {
        val min = ServerVersionUtils.MIN_RECOMMENDED_VERSION
        val below = ServerVersion(min.major, min.minor - 1, 0).toString()
        val above = ServerVersion(min.major, min.minor + 1, 0).toString()

        assertEquals(ServerCompatibility.SUPPORTED, ServerVersionUtils.evaluate(min.toString()).compatibility)
        assertEquals(ServerCompatibility.SUPPORTED, ServerVersionUtils.evaluate(above).compatibility)
        assertEquals(ServerCompatibility.OUTDATED, ServerVersionUtils.evaluate(below).compatibility)
        assertEquals(below, ServerVersionUtils.evaluate(below).version)
    }

    @Test
    fun evaluate_missingVersionRouteIsOutdated() {
        assertEquals(
            ServerVersionCheck(null, ServerCompatibility.OUTDATED),
            ServerVersionUtils.evaluate(null)
        )
    }

    @Test
    fun evaluate_nightlyIsNotWarnedAbout() {
        assertEquals(
            ServerVersionCheck("nightly", ServerCompatibility.UNRECOGNIZED),
            ServerVersionUtils.evaluate("nightly")
        )
    }

    @Test
    fun outdatedWarning_namesBothVersions() {
        val message = ServerVersionUtils.outdatedWarning(ServerVersionCheck("0.20.0", ServerCompatibility.OUTDATED))
        assertTrue("0.20.0" in message)
        assertTrue(ServerVersionUtils.MIN_RECOMMENDED_VERSION.toString() in message)
    }
}
