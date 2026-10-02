package com.karakept.app.ui.screens.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class OfflineRetentionLabelTest {

    @Test
    fun namesThePeriodInItsLargestWholeUnit() {
        assertEquals("After 1 day", offlineRetentionLabel(1))
        assertEquals("After 5 days", offlineRetentionLabel(5))
        assertEquals("After 1 week", offlineRetentionLabel(7))
        assertEquals("After 3 weeks", offlineRetentionLabel(21))
        assertEquals("After 1 month", offlineRetentionLabel(30))
        assertEquals("After 3 months", offlineRetentionLabel(90))
        assertEquals("After 45 days", offlineRetentionLabel(45))
    }

    @Test
    fun namesThePeriodAloneForSentences() {
        assertEquals("1 month", offlineRetentionPeriod(30))
        assertEquals("2 weeks", offlineRetentionPeriod(14))
        assertEquals("5 days", offlineRetentionPeriod(5))
    }
}
