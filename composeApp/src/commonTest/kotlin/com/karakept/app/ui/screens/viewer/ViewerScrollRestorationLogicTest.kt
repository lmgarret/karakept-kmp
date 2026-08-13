package com.karakept.app.ui.screens.viewer

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [mayRestoreReadingPosition].
 *
 * Guards the cross-device regression where progress pulled from the server arrived after the
 * reader had already concluded there was nothing to restore: the article opened at the top,
 * and only closing and reopening the bookmark — by then the value was in the DB — landed in
 * the right place.
 */
class ViewerScrollRestorationLogicTest {

    @Test
    fun mayRestore_beforeAnythingHasBeenDecided() {
        assertTrue(
            mayRestoreReadingPosition(
                hasRestoredScroll = false,
                hasScrolledToSavedPosition = false,
                stillAtTopOfArticle = true
            )
        )
    }

    @Test
    fun mayRestore_afterGivingUp_whileArticleHasNotMoved() {
        // "Nothing to restore" latched on a stale 0%, then the pull landed.
        assertTrue(
            mayRestoreReadingPosition(
                hasRestoredScroll = true,
                hasScrolledToSavedPosition = false,
                stillAtTopOfArticle = true
            )
        )
    }

    @Test
    fun mayNotRestore_onceTheReaderHasScrolled() {
        // Jumping someone who has started reading is worse than losing the position.
        assertFalse(
            mayRestoreReadingPosition(
                hasRestoredScroll = true,
                hasScrolledToSavedPosition = false,
                stillAtTopOfArticle = false
            )
        )
    }

    @Test
    fun mayNotRestore_twiceForTheSameOpen() {
        // A restore that put the reader at the top of a barely-read article must not be
        // re-applied on every later emission.
        assertFalse(
            mayRestoreReadingPosition(
                hasRestoredScroll = true,
                hasScrolledToSavedPosition = true,
                stillAtTopOfArticle = true
            )
        )
    }
}
