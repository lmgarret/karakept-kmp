package com.karakept.app.ui.screens.settings

import com.karakept.app.domain.OfflineRetention
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StorageCapInputTest {

    @Test
    fun parsesSizesInEitherUnit() {
        assertEquals(750, parseCapInput("750", CapUnit.MB))
        assertEquals(2500, parseCapInput("2.5", CapUnit.GB))
        assertEquals(2500, parseCapInput("2,5", CapUnit.GB), "a decimal comma counts too")
    }

    @Test
    fun rejectsWhatTheSettingWouldNotAccept() {
        assertNull(parseCapInput("", CapUnit.MB))
        assertNull(parseCapInput("5", CapUnit.MB), "below the minimum")
        assertNull(parseCapInput("500", CapUnit.GB), "above the maximum")
        assertNull(parseCapInput(".", CapUnit.GB))
    }

    @Test
    fun showsAGigabyteAndAboveInGigabytes() {
        assertEquals(CapUnit.MB, capUnitFor(750))
        assertEquals(CapUnit.GB, capUnitFor(1000))
        assertEquals("1.5", capFieldText(1500, CapUnit.GB))
        assertEquals("1500", capFieldText(1500, CapUnit.MB))
    }

    @Test
    fun labelsTrimTrailingZeros() {
        assertEquals("250 MB", offlineStorageCapLabel(250))
        assertEquals("1 GB", offlineStorageCapLabel(1000))
        assertEquals("7.5 GB", offlineStorageCapLabel(7500))
        assertEquals("2.25 GB", offlineStorageCapLabel(2250))
    }

    @Test
    fun sliderStopsRunFromAHundredMegabytesToTenGigabytes() {
        val stops = OfflineRetention.CAP_SLIDER_STOPS_MB
        assertEquals(100, stops.first())
        assertEquals(10_000, stops.last())
        assertEquals(1000, stops[OfflineRetention.nearestCapStopIndex(1100)])
    }
}
