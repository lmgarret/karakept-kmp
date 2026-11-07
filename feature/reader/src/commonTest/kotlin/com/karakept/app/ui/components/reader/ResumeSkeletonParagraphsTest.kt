package com.karakept.app.ui.components.reader

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class ResumeSkeletonParagraphsTest {

    @Test
    fun paragraphs_vary_inLengthAndLastLine() {
        val paragraphs = resumeSkeletonParagraphs(Random(42))

        assertTrue(paragraphs.map { it.size }.distinct().size > 1)
        assertTrue(paragraphs.map { it.last() }.distinct().size > 1)
    }

    @Test
    fun lineWidths_stayWithinTheColumn() {
        val paragraphs = resumeSkeletonParagraphs(Random(7))

        assertTrue(paragraphs.all { it.size in 1..6 })
        assertTrue(paragraphs.flatten().all { it > 0f && it <= 1f })
    }
}
