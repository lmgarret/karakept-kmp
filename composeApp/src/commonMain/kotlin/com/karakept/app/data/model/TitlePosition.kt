package com.karakept.app.data.model

/**
 * Where the bookmark title sits relative to the thumbnail.
 *
 * [BESIDE_THUMBNAIL] keeps the title in the text column next to the image. [ABOVE_THUMBNAIL] gives
 * it the full row width and drops the thumbnail below it, alongside whatever text remains — worth
 * having on a narrow screen, where an 80dp thumbnail takes a third of the line the title has to
 * fit into.
 *
 * Has no effect when the thumbnail is hidden; the title spans the row either way.
 */
enum class TitlePosition {
    BESIDE_THUMBNAIL, ABOVE_THUMBNAIL;

    companion object {
        fun fromString(value: String): TitlePosition =
            entries.find { it.name == value } ?: BESIDE_THUMBNAIL
    }
}
