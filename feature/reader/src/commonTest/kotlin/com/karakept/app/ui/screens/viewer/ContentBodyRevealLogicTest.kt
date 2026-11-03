package com.karakept.app.ui.screens.viewer

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for the reader body reveal gating ([computeContentRevealed] and
 * [shouldShowBodySkeleton]).
 *
 * These guard against the regression where the whole reader column was blanked until a
 * network reading-progress round-trip completed, producing a "black screen" on every open.
 * The hero must render immediately while the body shows a shimmer skeleton, and blank
 * content must never be held behind the skeleton.
 */
class ContentBodyRevealLogicTest {

    // computeContentRevealed -----------------------------------------------------

    @Test
    fun contentRevealed_true_whenNeitherGateActive() {
        assertTrue(computeContentRevealed(needsScrollRestore = false, needsHighlightScroll = false))
    }

    @Test
    fun contentRevealed_false_whileScrollRestorePending() {
        assertFalse(computeContentRevealed(needsScrollRestore = true, needsHighlightScroll = false))
    }

    @Test
    fun contentRevealed_false_whileHighlightScrollPending() {
        assertFalse(computeContentRevealed(needsScrollRestore = false, needsHighlightScroll = true))
    }

    // shouldShowBodySkeleton -----------------------------------------------------

    @Test
    fun skeleton_shown_whileHtmlParsing() {
        // Body exists but not parsed yet -> skeleton, regardless of reveal.
        assertTrue(
            shouldShowBodySkeleton(
                htmlContentReady = false,
                hasRenderableBody = true,
                contentRevealed = true
            )
        )
    }

    @Test
    fun skeleton_stillShown_afterParse_whenNotYetRevealed() {
        // Parsed but the parent is still restoring scroll -> keep the skeleton.
        assertTrue(
            shouldShowBodySkeleton(
                htmlContentReady = true,
                hasRenderableBody = true,
                contentRevealed = false
            )
        )
    }

    @Test
    fun skeleton_hidden_whenReadyAndRevealed() {
        assertFalse(
            shouldShowBodySkeleton(
                htmlContentReady = true,
                hasRenderableBody = true,
                contentRevealed = true
            )
        )
    }

    @Test
    fun skeleton_hidden_forBlankContent_evenWhenNotRevealed() {
        // No renderable body: the "content unavailable" message must surface immediately
        // instead of spinning behind the skeleton while scroll restoration waits.
        assertFalse(
            shouldShowBodySkeleton(
                htmlContentReady = true,
                hasRenderableBody = false,
                contentRevealed = false
            )
        )
    }

    // shouldShowRestoreOverlay ---------------------------------------------------

    @Test
    fun restoreOverlay_shown_whenRestoringWithSavedProgress() {
        assertTrue(shouldShowRestoreOverlay(needsScrollRestore = true, readingProgress = 0.5f))
    }

    @Test
    fun restoreOverlay_hidden_forFreshOpenWithoutProgress() {
        // The !serverProgressChecked wait keeps the hero-first behavior, not a full overlay.
        assertFalse(shouldShowRestoreOverlay(needsScrollRestore = true, readingProgress = 0f))
    }

    @Test
    fun restoreOverlay_hidden_forNegligibleProgress() {
        assertFalse(shouldShowRestoreOverlay(needsScrollRestore = true, readingProgress = 0.02f))
    }

    @Test
    fun restoreOverlay_hidden_whenRestoreAlreadyDone() {
        assertFalse(shouldShowRestoreOverlay(needsScrollRestore = false, readingProgress = 0.5f))
    }

    // shouldShowStickyTitle ------------------------------------------------------

    @Test
    fun stickyTitle_shown_whileRestoreOverlayCoversTheHero() {
        // The overlay hides the hero while the scroll is still at the top, so the top bar is the
        // only thing left that can name the article — otherwise the restore reads as a blank page.
        assertTrue(
            shouldShowStickyTitle(scrolledPastBanner = false, restoringToSavedPosition = true)
        )
    }

    @Test
    fun stickyTitle_hidden_atTopOfAFreshOpen() {
        // No overlay: the hero is visible and carries the title itself.
        assertFalse(
            shouldShowStickyTitle(scrolledPastBanner = false, restoringToSavedPosition = false)
        )
    }

    @Test
    fun stickyTitle_shown_onceScrolledPastTheBanner() {
        assertTrue(
            shouldShowStickyTitle(scrolledPastBanner = true, restoringToSavedPosition = false)
        )
    }
}
