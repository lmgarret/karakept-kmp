package com.karakept.app.domain

import com.karakept.app.data.local.projection.TagGroup
import com.karakept.app.utils.NoCaseCollationUtils

/**
 * How many bookmarks carry each tag, from rows the database has already grouped.
 *
 * A bookmark's tags are a comma-separated column, so counting them means splitting it. Grouping
 * on the whole column first means splitting once per distinct tag *set* rather than once per row
 * — and, more to the point, means the rows themselves never have to be in memory.
 */
object TagCountUtils {

    /** Tag to the number of bookmarks carrying it. */
    fun countsByTag(groups: List<TagGroup>): Map<String, Int> {
        val counts = HashMap<String, Int>()
        for (group in groups) {
            forEachTag(group.tags) { tag ->
                counts[tag] = (counts[tag] ?: 0) + group.rowCount
            }
        }
        return counts
    }

    /** Every tag in use, ordered the way the rest of the app orders text. */
    fun allTags(groups: List<TagGroup>): List<String> =
        countsByTag(groups).keys.sortedWith(NoCaseCollationUtils.ascending)

    /**
     * The [limit] most-used tags as `name (count)`, with any tag the user has actually selected
     * appended when it did not make the cut — a filter must always show what it is filtering on.
     */
    fun topTagsWithCounts(
        groups: List<TagGroup>,
        activeTags: List<String>,
        limit: Int = 10
    ): List<String> {
        val counts = countsByTag(groups)
        val top = counts.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenComparing({ it.key }, NoCaseCollationUtils.ascending)
            )
            .take(limit)
            .map { it.key }
        val missing = activeTags.filter { it !in top }
        return top.map { "$it (${counts[it]})" } +
            missing.map { tag -> counts[tag]?.let { "$tag ($it)" } ?: tag }
    }

    /** Visits each tag in a comma-separated column without building intermediate lists. */
    private inline fun forEachTag(tags: String, action: (String) -> Unit) {
        if (tags.isEmpty()) return
        var start = 0
        while (start <= tags.length) {
            val comma = tags.indexOf(',', start)
            val end = if (comma < 0) tags.length else comma
            val tag = tags.substring(start, end).trim()
            if (tag.isNotEmpty()) action(tag)
            if (comma < 0) return
            start = comma + 1
        }
    }
}
