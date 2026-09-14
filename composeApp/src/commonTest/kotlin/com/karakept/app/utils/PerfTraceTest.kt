package com.karakept.app.utils

import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Instrumentation must not change what it measures — these pin the one thing [PerfTrace] is
 * allowed to do to the code it wraps, which is nothing.
 */
class PerfTraceTest {

    @AfterTest
    fun tearDown() {
        PerfTrace.enabled = false
    }

    @Test
    fun `measure returns the block's value while disabled`() {
        PerfTrace.enabled = false
        assertEquals(42, PerfTrace.measure("test") { 42 })
    }

    @Test
    fun `measure returns the block's value while enabled`() {
        PerfTrace.enabled = true
        assertEquals(42, PerfTrace.measure("test") { 42 })
    }

    @Test
    fun `measure lets the block's exception through`() {
        PerfTrace.enabled = true
        var thrown = false
        try {
            PerfTrace.measure<Unit>("test") { throw IllegalStateException("boom") }
        } catch (e: IllegalStateException) {
            thrown = true
        }
        assertTrue(thrown, "a failing block must fail its caller, measured or not")
    }

    @Test
    fun `measureSuspending returns the block's value`() = runTest {
        PerfTrace.enabled = true
        assertEquals("ok", PerfTrace.measureSuspending("test") { "ok" })
    }

    @Test
    fun `counting while disabled records nothing`() {
        PerfTrace.enabled = false
        PerfTrace.count("test")
    }
}
