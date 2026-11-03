package com.karakept.app.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private data class TestKey(val id: Int) : NavKey

class AppNavigatorTest {

    @Test
    fun push_addsKeyToTopOfStack() {
        val stack = mutableListOf<NavKey>(TestKey(0))
        val navigator = AppNavigator(stack)

        navigator.push(TestKey(1))

        assertEquals(listOf<NavKey>(TestKey(0), TestKey(1)), stack)
    }

    @Test
    fun pop_removesTop_andReturnsTrue_whenMoreThanOne() {
        val stack = mutableListOf<NavKey>(TestKey(0), TestKey(1))
        val navigator = AppNavigator(stack)

        val popped = navigator.pop()

        assertTrue(popped)
        assertEquals(listOf<NavKey>(TestKey(0)), stack)
    }

    @Test
    fun pop_atRoot_returnsFalse_andKeepsEntry() {
        val stack = mutableListOf<NavKey>(TestKey(0))
        val navigator = AppNavigator(stack)

        val popped = navigator.pop()

        assertFalse(popped)
        assertEquals(listOf<NavKey>(TestKey(0)), stack)
    }

    @Test
    fun replaceAll_list_clearsThenSetsStack() {
        val stack = mutableListOf<NavKey>(TestKey(0), TestKey(1), TestKey(2))
        val navigator = AppNavigator(stack)

        navigator.replaceAll(listOf(TestKey(9), TestKey(10)))

        assertEquals(listOf<NavKey>(TestKey(9), TestKey(10)), stack)
    }

    @Test
    fun replaceAll_single_leavesOneEntry() {
        val stack = mutableListOf<NavKey>(TestKey(0), TestKey(1))
        val navigator = AppNavigator(stack)

        navigator.replaceAll(TestKey(5))

        assertEquals(listOf<NavKey>(TestKey(5)), stack)
        assertFalse(navigator.canPop)
    }

    @Test
    fun canPop_reflectsStackDepth() {
        val stack = mutableListOf<NavKey>(TestKey(0))
        val navigator = AppNavigator(stack)
        assertFalse(navigator.canPop)

        navigator.push(TestKey(1))
        assertTrue(navigator.canPop)
    }
}
