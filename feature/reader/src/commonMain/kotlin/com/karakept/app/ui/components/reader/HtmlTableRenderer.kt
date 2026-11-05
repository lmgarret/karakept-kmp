package com.karakept.app.ui.components.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fleeksoft.ksoup.nodes.Element
import com.karakept.app.data.model.Highlight
import com.karakept.app.ui.components.highlight.HighlightPosition

// Reader-mode rendering of <table>: rows collected across thead/tbody/tfoot, header detection,
// and horizontally scrollable cells.

/**
 * Collects all `<tr>` elements from a `<table>`, traversing `<thead>`, `<tbody>`, and `<tfoot>`.
 * Preserves DOM order for correct text-offset tracking.
 */
private fun collectTableRows(table: Element): List<Element> {
    val rows = mutableListOf<Element>()
    for (child in table.children()) {
        when (child.tagName().lowercase()) {
            "thead", "tbody", "tfoot" -> {
                for (grandChild in child.children()) {
                    if (grandChild.tagName().lowercase() == "tr") rows.add(grandChild)
                }
            }
            "tr" -> rows.add(child)
        }
    }
    return rows
}

private fun isHeaderRow(row: Element): Boolean {
    val parentTag = row.parent()?.tagName()?.lowercase()
    if (parentTag == "thead") return true
    val cells = row.children().filter { it.tagName().lowercase() in setOf("td", "th") }
    return cells.isNotEmpty() && cells.all { it.tagName().lowercase() == "th" }
}

@Composable
internal fun RenderTable(
    element: Element,
    theme: ReaderThemeData,
    highlights: List<Highlight>,
    offsets: ReaderTextOffsets,
    onLinkClick: (String) -> Unit,
    onHighlightClick: (String) -> Unit,
    onHighlightPosition: (String, HighlightPosition) -> Unit,
    depth: Int,
    selectedHighlightId: String? = null
) {
    val allRows = remember(element) { collectTableRows(element) }
    val columnCount = remember(allRows) {
        allRows.maxOfOrNull { row ->
            row.children().count { it.tagName().lowercase() in setOf("td", "th") }
        } ?: 0
    }

    val captionSearchState = LocalSearchState.current
    // Render caption if present
    for (child in element.children()) {
        if (child.tagName().lowercase() == "caption") {
            val text = buildInlineAnnotatedString(child, theme, highlights, offsets, onLinkClick, onHighlightClick, selectedHighlightId, captionSearchState)
            AnnotatedClickableText(
                text = text,
                onLinkClick = onLinkClick,
                onHighlightClick = onHighlightClick,
                onHighlightPosition = onHighlightPosition,
                color = theme.textColor,
                fontSize = theme.fontSize,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp),
                selectedHighlightId = selectedHighlightId,
                highlights = highlights
            )
        }
    }

    if (columnCount == 0) return

    // Estimate column widths based on the longest text content in each column
    val columnWidths = remember(allRows, columnCount, theme.fontSize) {
        val widths = IntArray(columnCount) { 80 }
        for (row in allRows) {
            val cells = row.children().filter { it.tagName().lowercase() in setOf("td", "th") }
            cells.forEachIndexed { index, cell ->
                if (index < columnCount) {
                    val textLength = cell.text().length
                    val estimated = (textLength * theme.fontSize.value * 0.6f + 32).toInt().coerceIn(80, 400)
                    widths[index] = maxOf(widths[index], estimated)
                }
            }
        }
        widths.toList()
    }

    val borderColor = theme.textColor.copy(alpha = 0.2f)
    val headerBgColor = theme.textColor.copy(alpha = 0.08f)

    HorizontallyScrollableContainer(
        modifier = Modifier
            .padding(vertical = 8.dp)
            .fillMaxWidth(),
        scrollbarColor = theme.textColor
    ) {
        Column(
            modifier = Modifier
                .border(0.5.dp, borderColor, RoundedCornerShape(4.dp))
                .clip(RoundedCornerShape(4.dp))
        ) {
            allRows.forEachIndexed { rowIndex, row ->
                val isHeader = isHeaderRow(row)
                Row(
                    modifier = Modifier
                        .height(IntrinsicSize.Min)
                        .then(if (isHeader) Modifier.background(headerBgColor) else Modifier)
                ) {
                    val cells = row.children().filter { it.tagName().lowercase() in setOf("td", "th") }
                    cells.forEachIndexed { cellIndex, cell ->
                        if (cellIndex > 0) {
                            VerticalDivider(color = borderColor, thickness = 0.5.dp)
                        }
                        val colWidth = columnWidths.getOrElse(cellIndex) { 80 }
                        Box(
                            modifier = Modifier
                                .width(colWidth.dp)
                                .fillMaxHeight()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            RenderTableCell(
                                cell, theme, highlights, offsets,
                                onLinkClick, onHighlightClick, onHighlightPosition,
                                depth, isHeader = cell.tagName().lowercase() == "th" || isHeader,
                                selectedHighlightId = selectedHighlightId
                            )
                        }
                    }
                }
                if (rowIndex < allRows.lastIndex) {
                    HorizontalDivider(color = borderColor, thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
internal fun RenderTableSection(
    element: Element,
    theme: ReaderThemeData,
    highlights: List<Highlight>,
    offsets: ReaderTextOffsets,
    onLinkClick: (String) -> Unit,
    onHighlightClick: (String) -> Unit,
    onHighlightPosition: (String, HighlightPosition) -> Unit,
    depth: Int,
    selectedHighlightId: String? = null
) {
    for (child in element.children()) {
        if (child.tagName().lowercase() == "tr") {
            RenderTableRow(child, theme, highlights, offsets, onLinkClick, onHighlightClick, onHighlightPosition, depth, selectedHighlightId)
        }
    }
}

@Composable
internal fun RenderTableRow(
    element: Element,
    theme: ReaderThemeData,
    highlights: List<Highlight>,
    offsets: ReaderTextOffsets,
    onLinkClick: (String) -> Unit,
    onHighlightClick: (String) -> Unit,
    onHighlightPosition: (String, HighlightPosition) -> Unit,
    depth: Int,
    selectedHighlightId: String? = null
) {
    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        for (child in element.children()) {
            val tag = child.tagName().lowercase()
            if (tag == "td" || tag == "th") {
                Box(
                    modifier = Modifier
                        .widthIn(min = 80.dp)
                        .fillMaxHeight()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    RenderTableCell(child, theme, highlights, offsets, onLinkClick, onHighlightClick, onHighlightPosition, depth, isHeader = tag == "th", selectedHighlightId = selectedHighlightId)
                }
            }
        }
    }
    HorizontalDivider()
}

@Composable
internal fun RenderTableCell(
    element: Element,
    theme: ReaderThemeData,
    highlights: List<Highlight>,
    offsets: ReaderTextOffsets,
    onLinkClick: (String) -> Unit,
    onHighlightClick: (String) -> Unit,
    onHighlightPosition: (String, HighlightPosition) -> Unit,
    depth: Int,
    isHeader: Boolean,
    selectedHighlightId: String? = null
) {
    val searchState = LocalSearchState.current
    val searchCallback = LocalSearchMatchScrollCallback.current
    if (hasBlockChildren(element)) {
        Column {
            RenderChildren(element, highlights, offsets, onLinkClick, onHighlightClick, onHighlightPosition, depth, selectedHighlightId = selectedHighlightId)
        }
    } else {
        val blockStart = offsets.startOf(element)
        val text = buildInlineAnnotatedString(element, theme, highlights, offsets, onLinkClick, onHighlightClick, selectedHighlightId, searchState)
        val blockEnd = offsets.endOf(element)
        val scrollMod = rememberSearchScrollModifier(blockStart, blockEnd, searchState, searchCallback)
        AnnotatedClickableText(
            text = text,
            onLinkClick = onLinkClick,
            onHighlightClick = onHighlightClick,
            onHighlightPosition = onHighlightPosition,
            color = theme.textColor,
            fontSize = theme.fontSize,
            fontFamily = theme.fontFamily,
            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
            lineHeight = theme.tightLineHeight(),
            modifier = scrollMod,
            selectedHighlightId = selectedHighlightId,
            highlights = highlights
        )
    }
}
