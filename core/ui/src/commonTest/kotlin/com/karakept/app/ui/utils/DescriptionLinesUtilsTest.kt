package com.karakept.app.ui.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The auto line count turns the dead space beside a thumbnail into description lines. It has to
 * stay inside the thumbnail's height — overshooting grows the row and defeats the point — while
 * never returning zero.
 */
class DescriptionLinesUtilsTest {

    private val lineHeight = 16f

    @Test
    fun `fills the space the title leaves over`() {
        // 80dp thumbnail, 40 consumed by title and url, 40 left = 2 lines of 16.
        assertEquals(
            2,
            computeAutoDescriptionLines(
                availableHeightPx = 80f,
                consumedHeightPx = 40f,
                descriptionLineHeightPx = lineHeight
            )
        )
    }

    @Test
    fun `a taller thumbnail yields more lines`() {
        val short = computeAutoDescriptionLines(80f, 20f, lineHeight)
        val tall = computeAutoDescriptionLines(160f, 20f, lineHeight)
        assertTrue(tall > short, "a 160px thumbnail should fit more than an 80px one")
    }

    @Test
    fun `a wrapped title costs lines`() {
        val oneLineTitle = computeAutoDescriptionLines(100f, 20f, lineHeight)
        val twoLineTitle = computeAutoDescriptionLines(100f, 40f, lineHeight)
        assertTrue(twoLineTitle < oneLineTitle)
    }

    @Test
    fun `rounds down so the row never outgrows its thumbnail`() {
        // 47 available over a 16px line is 2.9 lines — three would overflow.
        assertEquals(2, computeAutoDescriptionLines(80f, 33f, lineHeight))
    }

    @Test
    fun `never returns zero even when nothing fits`() {
        assertEquals(1, computeAutoDescriptionLines(80f, 80f, lineHeight))
        assertEquals(1, computeAutoDescriptionLines(80f, 200f, lineHeight))
    }

    @Test
    fun `is capped so a huge thumbnail cannot produce a wall of text`() {
        assertEquals(
            AUTO_DESCRIPTION_LINE_CEILING,
            computeAutoDescriptionLines(10_000f, 0f, lineHeight)
        )
    }

    @Test
    fun `respects a caller-supplied ceiling`() {
        assertEquals(3, computeAutoDescriptionLines(10_000f, 0f, lineHeight, maxLines = 3))
    }

    @Test
    fun `an unmeasured line height falls back to one line rather than dividing by zero`() {
        assertEquals(1, computeAutoDescriptionLines(80f, 0f, 0f))
        assertEquals(1, computeAutoDescriptionLines(80f, 0f, -4f))
    }
}
