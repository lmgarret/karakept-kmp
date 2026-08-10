package com.karakept.app.data.model

/**
 * How a bookmark row is separated from the rows around it.
 *
 * [CARD] is the Material default: an elevated, tonally filled container per bookmark.
 * [FLAT] drops the container entirely and separates rows with a single hairline rule — far less
 * ink on an e-ink panel, where a card's fill is invisible and its outline has to be drawn at full
 * strength to register at all.
 */
enum class ItemContainerStyle {
    CARD, FLAT;

    companion object {
        fun fromString(value: String): ItemContainerStyle =
            entries.find { it.name == value } ?: CARD
    }
}

/**
 * How a bookmark that has already been read is distinguished from an unread one.
 *
 * [DIM] fades the whole row to 50% alpha. [MARKER] keeps every row at full contrast and puts a
 * bullet beside unread titles instead — on a monochrome panel a dimmed row is mid-grey, which is
 * both hard to read and hard to tell apart from a normal one.
 */
enum class ReadIndicatorStyle {
    DIM, MARKER;

    companion object {
        fun fromString(value: String): ReadIndicatorStyle =
            entries.find { it.name == value } ?: DIM
    }
}
