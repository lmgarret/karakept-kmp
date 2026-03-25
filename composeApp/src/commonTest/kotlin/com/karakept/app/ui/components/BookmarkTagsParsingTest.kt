package com.karakept.app.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class BookmarkTagsParsingTest {

    @Test
    fun parseTagString_commaSeparated() {
        assertEquals(listOf("kotlin", "android", "kmp"), parseTagString("kotlin,android,kmp"))
    }

    @Test
    fun parseTagString_withSpaces() {
        assertEquals(listOf("kotlin", "android", "kmp"), parseTagString("kotlin, android , kmp"))
    }

    @Test
    fun parseTagString_emptyString() {
        assertEquals(emptyList(), parseTagString(""))
    }

    @Test
    fun parseTagString_onlyCommas() {
        assertEquals(emptyList(), parseTagString(",,"))
    }

    @Test
    fun parseTagString_singleTag() {
        assertEquals(listOf("single"), parseTagString("single"))
    }

    @Test
    fun parseTagString_spacesAndCommas() {
        assertEquals(listOf("tag"), parseTagString(" , tag , "))
    }
}
