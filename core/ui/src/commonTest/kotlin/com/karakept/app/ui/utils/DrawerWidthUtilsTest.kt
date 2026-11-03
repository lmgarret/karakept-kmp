package com.karakept.app.ui.utils

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class DrawerWidthUtilsTest {

    @Test
    fun `caps at 360dp on wide screens`() {
        assertEquals(360.dp, modalDrawerWidth(600.dp))
        assertEquals(360.dp, modalDrawerWidth(800.dp))
    }

    @Test
    fun `leaves a 56dp scrim strip on narrow screens`() {
        assertEquals(304.dp, modalDrawerWidth(360.dp))
        assertEquals(264.dp, modalDrawerWidth(320.dp))
    }

    @Test
    fun `switches to constrained width exactly at the 416dp threshold`() {
        assertEquals(360.dp, modalDrawerWidth(416.dp))
        assertEquals(359.dp, modalDrawerWidth(415.dp))
    }

    @Test
    fun `coerceExpandedDrawerWidth keeps in-range values unchanged`() {
        assertEquals(280f, coerceExpandedDrawerWidth(280f))
        assertEquals(ExpandedDrawerMinWidth.value, coerceExpandedDrawerWidth(ExpandedDrawerMinWidth.value))
        assertEquals(ExpandedDrawerMaxWidth.value, coerceExpandedDrawerWidth(ExpandedDrawerMaxWidth.value))
    }

    @Test
    fun `coerceExpandedDrawerWidth clamps to the allowed bounds`() {
        assertEquals(ExpandedDrawerMinWidth.value, coerceExpandedDrawerWidth(50f))
        assertEquals(ExpandedDrawerMaxWidth.value, coerceExpandedDrawerWidth(1000f))
    }
}
