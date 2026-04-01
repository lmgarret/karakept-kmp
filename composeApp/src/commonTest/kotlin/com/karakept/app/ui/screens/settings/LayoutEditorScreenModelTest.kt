package com.karakept.app.ui.screens.settings

import com.karakept.app.data.model.DescriptionPosition
import com.karakept.app.data.model.UrlDisplayMode
import com.karakept.app.data.model.UrlIconMode
import com.karakept.app.data.model.UrlPosition
import com.karakept.app.data.repository.SettingsRepository
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LayoutEditorScreenModelTest {
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val screenModel = LayoutEditorScreenModel(settingsRepository)

    @Test
    fun updateShowDescription_false_setsShowDescriptionToFalse() {
        // Default is true
        assertTrue(screenModel.layout.value.showDescription)
        screenModel.updateShowDescription(false)
        assertFalse(screenModel.layout.value.showDescription)
    }

    @Test
    fun updateShowDescription_true_setsShowDescriptionToTrue() {
        screenModel.updateShowDescription(false)
        assertFalse(screenModel.layout.value.showDescription)
        screenModel.updateShowDescription(true)
        assertTrue(screenModel.layout.value.showDescription)
    }

    @Test
    fun updateDescriptionPosition_ABOVE_METADATA_setsDescriptionPositionToABOVE_METADATA() {
        screenModel.updateDescriptionPosition(DescriptionPosition.ABOVE_METADATA)
        assertEquals("ABOVE_METADATA", screenModel.layout.value.descriptionPosition)
    }

    @Test
    fun updateDescriptionPosition_BELOW_TITLE_setsDescriptionPositionToBELOW_TITLE() {
        screenModel.updateDescriptionPosition(DescriptionPosition.ABOVE_METADATA)
        screenModel.updateDescriptionPosition(DescriptionPosition.BELOW_TITLE)
        assertEquals("BELOW_TITLE", screenModel.layout.value.descriptionPosition)
    }

    @Test
    fun updateShowUrl_true_setsShowUrlToTrue() {
        // Default is false
        assertFalse(screenModel.layout.value.showUrl)
        screenModel.updateShowUrl(true)
        assertTrue(screenModel.layout.value.showUrl)
    }

    @Test
    fun updateShowUrl_false_setsShowUrlToFalse() {
        screenModel.updateShowUrl(true)
        assertTrue(screenModel.layout.value.showUrl)
        screenModel.updateShowUrl(false)
        assertFalse(screenModel.layout.value.showUrl)
    }

    @Test
    fun updateUrlDisplayMode_FULL_URL_setsUrlDisplayModeToFULL_URL() {
        screenModel.updateUrlDisplayMode(UrlDisplayMode.FULL_URL)
        assertEquals("FULL_URL", screenModel.layout.value.urlDisplayMode)
    }

    @Test
    fun updateUrlDisplayMode_DOMAIN_ONLY_setsUrlDisplayModeToDOMAIN_ONLY() {
        screenModel.updateUrlDisplayMode(UrlDisplayMode.FULL_URL)
        screenModel.updateUrlDisplayMode(UrlDisplayMode.DOMAIN_ONLY)
        assertEquals("DOMAIN_ONLY", screenModel.layout.value.urlDisplayMode)
    }

    @Test
    fun updateUrlPosition_METADATA_ROW_setsUrlPositionToMETADATA_ROW() {
        screenModel.updateUrlPosition(UrlPosition.METADATA_ROW)
        assertEquals("METADATA_ROW", screenModel.layout.value.urlPosition)
    }

    @Test
    fun updateUrlPosition_BELOW_TITLE_setsUrlPositionToBELOW_TITLE() {
        screenModel.updateUrlPosition(UrlPosition.METADATA_ROW)
        screenModel.updateUrlPosition(UrlPosition.BELOW_TITLE)
        assertEquals("BELOW_TITLE", screenModel.layout.value.urlPosition)
    }

    @Test
    fun updateUrlIconMode_FAVICON_setsUrlIconModeToFAVICON() {
        screenModel.updateUrlIconMode(UrlIconMode.FAVICON)
        assertEquals("FAVICON", screenModel.layout.value.urlIconMode)
    }

    @Test
    fun updateUrlIconMode_GLOBE_ONLY_setsUrlIconModeToGLOBE_ONLY() {
        screenModel.updateUrlIconMode(UrlIconMode.FAVICON)
        screenModel.updateUrlIconMode(UrlIconMode.GLOBE_ONLY)
        assertEquals("GLOBE_ONLY", screenModel.layout.value.urlIconMode)
    }
}
