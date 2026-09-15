package com.karakept.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.BookmarkLayout
import com.karakept.app.data.model.DescriptionPosition
import com.karakept.app.data.model.ItemContainerStyle
import com.karakept.app.data.model.LayoutType
import com.karakept.app.data.model.MetadataPosition
import com.karakept.app.data.model.ThumbnailSide
import com.karakept.app.data.model.TitlePosition
import com.karakept.app.data.model.UrlPosition
import com.karakept.app.ui.utils.BookmarkRowMetrics
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The tile is only as good as the natural height it is derived from, and that height is *declared*
 * rather than measured — see [BookmarkRowMetrics]. Nothing in a pure unit test can say whether the
 * declaration still matches the row `BookmarkListLayout` actually lays out, so this renders the
 * real row for every built-in layout and compares.
 *
 * A few percent of error is harmless: it only ever moves the choice of how many rows fit a page,
 * and the drift check then decides whether that choice is worth applying. What matters is that the
 * estimate does not drift far enough to pick a different number of rows.
 */
@OptIn(ExperimentalTestApi::class)
class BookmarkRowTilingTest {

    private val rowWidth = 400.dp

    /** A worst-case row: the title wraps to its two-line cap and the description fills its own. */
    private val bookmark = BookmarkEntity(
        localId = 1,
        remoteId = "1",
        serverId = "server",
        url = "https://example.com/a-fairly-long-article-path",
        title = "A bookmark title long enough to wrap onto the second line the row allows for it",
        content = null,
        imageUrl = null,
        bannerImageAssetId = null,
        screenshotAssetId = null,
        description = "An excerpt long enough to run past whatever line cap the layout puts on " +
            "it, so the row is as tall as that layout ever lets a row be, which is the height " +
            "the estimate claims to describe and the one a page has to be divided into.",
        createdAt = 0,
        isArchived = false,
        isStarred = false,
        tags = "kotlin, e-ink",
        readingTimeMinutes = 7
    )

    @Test
    fun `the estimated row height tracks the row every built-in list layout renders`() {
        BookmarkLayout.ALL_BUILTIN
            .filter { LayoutType.fromString(it.layoutType) != LayoutType.CARD }
            .forEach { layout ->
                val (estimated, measured) = measureRow(layout)
                val error = abs(estimated - measured) / measured
                assertTrue(
                    error <= MAX_ESTIMATE_ERROR,
                    "${layout.name}: estimated ${estimated}px against a measured ${measured}px"
                )
            }
    }

    @Test
    fun `every built-in list layout tiles into whole rows that fill the page`() {
        BookmarkLayout.ALL_BUILTIN.forEach { layout ->
            val layoutType = LayoutType.fromString(layout.layoutType)
            val tiled = resolveFor(layout, EINK_PAGE_PX)
            if (layoutType == LayoutType.CARD) {
                // Magazine's hero image sizes each row for itself; no single height is close to
                // two of them, so the list is deliberately left ragged.
                assertNull(tiled, "${layout.name} should not be tiled")
                return@forEach
            }
            val tiling = assertNotNull(tiled, "${layout.name} should tile").tiling
            assertTrue(
                tiling.pageHeightPx <= EINK_PAGE_PX,
                "${layout.name} overflows the page: ${tiling.pageHeightPx}"
            )
            // What integer division leaves over stays under a pixel per row, so the page is full
            // rather than merely close to it.
            assertTrue(
                EINK_PAGE_PX - tiling.pageHeightPx < tiling.rowsPerPage,
                "${layout.name} leaves ${EINK_PAGE_PX - tiling.pageHeightPx}px of the page unused"
            )
            assertTrue(tiling.rowsPerPage >= 2, "${layout.name} fits ${tiling.rowsPerPage} row(s)")
        }
    }

    @Test
    fun `tiling is off unless the list is paged`() {
        assertNull(resolveFor(BookmarkLayout.BUILTIN_ROWS, EINK_PAGE_PX, enabled = false))
    }

    /**
     * A skeleton stands in for a row that has not arrived, so it has to be the size of the row it
     * stands in for. A skeleton shorter or taller than the real row moves everything below it the
     * moment the row lands — which on a list being scrolled is the reader's place jumping.
     *
     * It is built from the same [BookmarkRowMetrics] the tiling uses, so this is really asking
     * whether that declaration reaches the skeleton intact. Describing the row a second time by
     * hand is what the old placeholder did, and it had drifted: no description lines, no url, no
     * metadata band.
     */
    @Test
    fun `a skeleton is the size of the row it stands in for`() {
        BookmarkLayout.ALL_BUILTIN
            .filter { LayoutType.fromString(it.layoutType) != LayoutType.CARD }
            .forEach { layout ->
                val (_, rowHeight) = measureRow(layout)
                val skeletonHeight = measureSkeleton(layout)
                val error = abs(skeletonHeight - rowHeight) / rowHeight
                assertTrue(
                    error <= MAX_ESTIMATE_ERROR,
                    "${layout.name}: skeleton ${skeletonHeight}px against a row ${rowHeight}px"
                )
            }
    }

    /** What the skeleton really measures, in pixels, for one layout. */
    private fun measureSkeleton(layout: BookmarkLayout): Float {
        var measured = 0f
        runComposeUiTest {
        var densityScale = 1f
        setContent {
            MaterialTheme {
                densityScale = LocalDensity.current.density
                Box(modifier = Modifier.width(rowWidth)) {
                    BookmarkRowSkeleton(
                        metrics = metricsFor(layout),
                        itemContainerStyle =
                            ItemContainerStyle.fromString(layout.itemContainerStyle),
                        thumbnailSide = ThumbnailSide.fromString(layout.thumbnailSide),
                        showRowDivider = layout.showRowDivider,
                        modifier = Modifier.testTag(SKELETON_TAG)
                    )
                }
            }
        }
        val bounds = onNodeWithTag(SKELETON_TAG).getBoundsInRoot()
        measured = (bounds.bottom - bounds.top).value * densityScale
        }
        return measured
    }

    /** The estimate and what the row really measures, both in pixels, for one layout. */
    private fun measureRow(layout: BookmarkLayout): Pair<Int, Float> {
        var estimated = 0
        var measured = 0f
        runComposeUiTest {
        var densityScale = 1f
        setContent {
            MaterialTheme {
                densityScale = LocalDensity.current.density
                val metrics = metricsFor(layout)
                // The estimate covers the whole list item; the swipe wrapper's padding is not part
                // of the row being measured here.
                estimated = (metrics.naturalHeightPx - metrics.wrapperPaddingPx).toInt()
                Box(modifier = Modifier.width(rowWidth)) {
                    BookmarkListLayout(
                        bookmark = bookmark,
                        onClick = {},
                        showReadingTime = layout.showReadingTime,
                        showTags = layout.showTags,
                        showDate = layout.showDate,
                        thumbnailSize = layout.thumbnailSize,
                        metadataPosition = MetadataPosition.fromString(layout.metadataPosition),
                        showDescription = layout.showDescription,
                        descriptionPosition =
                            DescriptionPosition.fromString(layout.descriptionPosition),
                        showUrl = layout.showUrl,
                        urlPosition = UrlPosition.fromString(layout.urlPosition),
                        showThumbnail = layout.showThumbnail,
                        itemContainerStyle =
                            ItemContainerStyle.fromString(layout.itemContainerStyle),
                        showRowDivider = layout.showRowDivider,
                        titlePosition = TitlePosition.fromString(layout.titlePosition),
                        descriptionMaxLines = layout.descriptionMaxLines,
                        rowWidth = rowWidth,
                        modifier = Modifier.testTag(ROW_TAG)
                    )
                }
            }
        }
        val bounds = onNodeWithTag(ROW_TAG).getBoundsInRoot()
        measured = (bounds.bottom - bounds.top).value * densityScale
        }
        return estimated to measured
    }

    private fun resolveFor(
        layout: BookmarkLayout,
        viewportPx: Int,
        enabled: Boolean = true
    ): TiledRows? {
        var tiled: TiledRows? = null
        runComposeUiTest {
        setContent {
            MaterialTheme {
                tiled = rememberTiledRows(
                    enabled = enabled,
                    viewportPx = viewportPx,
                    layoutType = LayoutType.fromString(layout.layoutType),
                    metrics = metricsFor(layout)
                )
            }
        }
        }
        return tiled
    }

    @Composable
    private fun metricsFor(layout: BookmarkLayout): BookmarkRowMetrics =
        rememberBookmarkRowMetrics(
            itemContainerStyle = ItemContainerStyle.fromString(layout.itemContainerStyle),
            showThumbnail = layout.showThumbnail,
            thumbnailSize = layout.thumbnailSize,
            titlePosition = TitlePosition.fromString(layout.titlePosition),
            showDescription = layout.showDescription,
            descriptionMaxLines = layout.descriptionMaxLines,
            descriptionPosition = DescriptionPosition.fromString(layout.descriptionPosition),
            showUrl = layout.showUrl,
            urlPosition = UrlPosition.fromString(layout.urlPosition),
            showTags = layout.showTags,
            showDate = layout.showDate,
            showReadingTime = layout.showReadingTime,
            metadataPosition = MetadataPosition.fromString(layout.metadataPosition),
            showRowDivider = layout.showRowDivider
        )

    private companion object {
        const val ROW_TAG = "bookmark-row"
        const val SKELETON_TAG = "bookmark-row-skeleton"

        /** A 7.8" reader's page once the top bar has taken its share. */
        const val EINK_PAGE_PX = 1300

        /**
         * The estimate matches the rendered row exactly here — both sides read the same declared
         * line heights — so this only has to absorb a title or a chip wrapping differently under
         * another font, not a structural difference.
         */
        const val MAX_ESTIMATE_ERROR = 0.08f
    }
}
