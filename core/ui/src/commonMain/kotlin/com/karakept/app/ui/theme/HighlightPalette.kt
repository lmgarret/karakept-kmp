package com.karakept.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * How a highlight's colour is expressed when hue is unavailable.
 *
 * On a B&W e-ink panel the four highlight fills all land within a couple of greys of one another,
 * so the colour has to be carried by something the panel can actually draw. One pattern per
 * colour, distinguishable at a glance and cheap in ink.
 */
enum class HighlightPattern { SOLID, DOUBLE, DASHED, DOTTED }

/**
 * Everything the UI needs to render one highlight colour.
 *
 * [name] is the wire value — the Karakeep API only accepts `yellow`, `red`, `green`, `blue` — and
 * is what gets stored on [com.karakept.app.data.model.Highlight.color]. Never render it directly;
 * use [label].
 */
@Immutable
data class HighlightStyle(
    val name: String,
    val label: String,
    val color: Color,
    val pattern: HighlightPattern,
    val cssHex: String
)

/**
 * The single source of truth for highlight colours.
 *
 * Every surface that turns a highlight's colour string into something visible goes through here:
 * the reader's span styling, the highlight list, the colour picker, and the WebView stylesheet.
 * They used to keep four independent copies of this table, which had already drifted.
 */
object HighlightPalette {

    /** Picker order, densest-used first. */
    val all: List<HighlightStyle> = listOf(
        HighlightStyle("yellow", "Yellow", HighlightYellow, HighlightPattern.SOLID, "#ffeb3b"),
        HighlightStyle("blue", "Blue", HighlightBlue, HighlightPattern.DOUBLE, "#2196f3"),
        HighlightStyle("green", "Green", HighlightGreen, HighlightPattern.DASHED, "#4caf50"),
        HighlightStyle("red", "Red", HighlightRed, HighlightPattern.DOTTED, "#f44336")
    )

    /** The colour a highlight gets when none was chosen — matches the API's own default. */
    val default: HighlightStyle = all.first()

    private val byName = all.associateBy { it.name }

    /** Resolves a stored colour string. Unknown and null values fall back to [default]. */
    fun styleFor(name: String?): HighlightStyle =
        name?.lowercase()?.let { byName[it] } ?: default
}
