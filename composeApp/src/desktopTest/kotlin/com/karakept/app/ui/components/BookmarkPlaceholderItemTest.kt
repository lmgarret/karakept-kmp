package com.karakept.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.karakept.app.data.model.ItemContainerStyle
import com.karakept.app.data.model.LayoutType
import com.karakept.app.data.model.ThumbnailSide
import com.karakept.app.data.model.TitlePosition
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Regression tests for [BookmarkPlaceholderItem]: the temporary row shown while a bookmark is
 * being added must follow the active list layout instead of always rendering as a card (#342).
 *
 * The shimmer boxes carry no text, so structural differences are asserted through the one text
 * node every variant renders (the bookmark URL) and its position, which shifts with the container
 * style and thumbnail visibility.
 */
@OptIn(ExperimentalTestApi::class)
class BookmarkPlaceholderItemTest {

    private val url = "https://example.com/article"

    @Test
    fun `renders url for every layout and container style combination`() = runComposeUiTest {
        for (layoutType in listOf(LayoutType.LIST, LayoutType.CARD)) {
            for (containerStyle in ItemContainerStyle.entries) {
                setContent {
                    MaterialTheme {
                        BookmarkPlaceholderItem(
                            url = url,
                            layoutType = layoutType,
                            itemContainerStyle = containerStyle
                        )
                    }
                }
                onNodeWithText(url).assertExists()
            }
        }
    }

    @Test
    fun `flat container is not inset like the card container`() = runComposeUiTest {
        var flatLeft = 0f
        setContent {
            MaterialTheme {
                BookmarkPlaceholderItem(
                    url = url,
                    layoutType = LayoutType.LIST,
                    itemContainerStyle = ItemContainerStyle.FLAT
                )
            }
        }
        flatLeft = onNodeWithText(url).getBoundsInRoot().left.value

        var cardLeft = 0f
        setContent {
            MaterialTheme {
                BookmarkPlaceholderItem(
                    url = url,
                    layoutType = LayoutType.LIST,
                    itemContainerStyle = ItemContainerStyle.CARD
                )
            }
        }
        cardLeft = onNodeWithText(url).getBoundsInRoot().left.value

        // A card row carries an extra 16dp outer margin a flat row does not — the placeholder
        // must reflect that instead of always wrapping itself in a Card.
        assertTrue(
            cardLeft > flatLeft,
            "card container should be inset further than the flat container (card=$cardLeft, flat=$flatLeft)"
        )
    }

    @Test
    fun `hiding the thumbnail moves the url text left`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                BookmarkPlaceholderItem(
                    url = url,
                    layoutType = LayoutType.LIST,
                    showThumbnail = true
                )
            }
        }
        val withThumbnail = onNodeWithText(url).getBoundsInRoot().left.value

        setContent {
            MaterialTheme {
                BookmarkPlaceholderItem(
                    url = url,
                    layoutType = LayoutType.LIST,
                    showThumbnail = false
                )
            }
        }
        val withoutThumbnail = onNodeWithText(url).getBoundsInRoot().left.value

        assertTrue(
            withoutThumbnail < withThumbnail,
            "url text should start further left with no thumbnail (with=$withThumbnail, without=$withoutThumbnail)"
        )
    }

    @Test
    fun `thumbnail on the right moves the url text to the left edge`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                BookmarkPlaceholderItem(
                    url = url,
                    layoutType = LayoutType.LIST,
                    thumbnailSide = ThumbnailSide.LEFT
                )
            }
        }
        val leftThumbnailUrlLeft = onNodeWithText(url).getBoundsInRoot().left.value

        setContent {
            MaterialTheme {
                BookmarkPlaceholderItem(
                    url = url,
                    layoutType = LayoutType.LIST,
                    thumbnailSide = ThumbnailSide.RIGHT
                )
            }
        }
        val rightThumbnailUrlLeft = onNodeWithText(url).getBoundsInRoot().left.value

        assertTrue(
            rightThumbnailUrlLeft < leftThumbnailUrlLeft,
            "url text should start at the row's text inset (not after the thumbnail) once the " +
                "thumbnail moves to the right (left-thumb=$leftThumbnailUrlLeft, right-thumb=$rightThumbnailUrlLeft)"
        )
    }

    @Test
    fun `renders url for every title position and thumbnail visibility combination`() = runComposeUiTest {
        for (titlePosition in TitlePosition.entries) {
            for (showThumbnail in listOf(true, false)) {
                setContent {
                    MaterialTheme {
                        BookmarkPlaceholderItem(
                            url = url,
                            layoutType = LayoutType.LIST,
                            showThumbnail = showThumbnail,
                            titlePosition = titlePosition
                        )
                    }
                }
                onNodeWithText(url).assertExists()
            }
        }
    }
}
