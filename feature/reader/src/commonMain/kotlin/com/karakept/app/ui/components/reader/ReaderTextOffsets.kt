package com.karakept.app.ui.components.reader

import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode

/** Where a node's text sits in the document's text stream. [end] is exclusive. */
data class NodeTextRange(val start: Int, val end: Int)

/**
 * The document's text stream, and where every node sits in it.
 *
 * The stream is the concatenation of every text node in document order and
 * nothing else — the same count karakeep's `TreeWalker` makes when it records a
 * highlight (`BookmarkHtmlHighlighter.getTextNodeOffset`) and the same one the
 * WebView viewer already uses. Offsets are therefore portable between the two
 * viewers and the server.
 *
 * Nothing virtual is inserted between blocks. Separators are what the reader's
 * offsets kept drifting on: they have to be counted identically by the walk that
 * *resolves* a selection and by every renderer that *draws* one, and a renderer
 * that iterated `children()` instead of `childNodes()`, or that emitted a `<br>`
 * without counting it, quietly shifted every later highlight. Block boundaries
 * are still needed to match a selection that spans blocks — Compose joins those
 * with a newline — so they are kept as [boundaries], positions in the stream that
 * match as whitespace but occupy no offset.
 */
class ReaderTextOffsets internal constructor(
    /** The canonical text stream. */
    val text: String,
    private val ranges: Map<Node, NodeTextRange>,
    /** Stream positions where a block element starts. */
    private val boundaries: Set<Int>
) {
    /** Offset where [node]'s text begins, or 0 for a node outside this document. */
    fun startOf(node: Node): Int = ranges[node]?.start ?: 0

    /** Offset just past [node]'s text, or 0 for a node outside this document. */
    fun endOf(node: Node): Int = ranges[node]?.end ?: 0

    internal val normalized: NormalizedStream by lazy { normalizeStream(text, boundaries) }

    /**
     * The stream between [start] and [end] with block boundaries spelled out as
     * newlines — what the selection looked like, rather than the run-on the
     * offsets themselves describe.
     */
    fun readableText(start: Int, end: Int): String {
        val from = start.coerceIn(0, text.length)
        val to = end.coerceIn(from, text.length)
        val sb = StringBuilder()
        for (i in from until to) {
            if (i > from && i in boundaries) sb.append('\n')
            sb.append(text[i])
        }
        return sb.toString()
    }
}

/**
 * Walks [root] once, recording the text stream and every node's place in it.
 *
 * Memoize this per document: it is the single definition of the reader's offsets,
 * and every renderer reads its answer instead of counting along as it draws.
 */
fun buildReaderTextOffsets(root: Element): ReaderTextOffsets {
    val sb = StringBuilder()
    // Keyed by node identity: ksoup's Node.equals is a `this === other` test, so two
    // structurally identical paragraphs stay two entries.
    val ranges = HashMap<Node, NodeTextRange>()
    val boundaries = mutableSetOf<Int>()

    fun walk(node: Node) {
        val start = sb.length
        when (node) {
            is TextNode -> sb.append(node.getWholeText())
            is Element -> {
                val isBlock = isBlockElement(node)
                // Both edges: a block is drawn on its own, so the reader puts a break
                // before it and after it — including where inline text follows it.
                if (isBlock && start > 0) boundaries += start
                for (child in node.childNodes()) walk(child)
                if (isBlock && sb.isNotEmpty()) boundaries += sb.length
            }
        }
        ranges[node] = NodeTextRange(start, sb.length)
    }

    for (child in root.childNodes()) walk(child)
    ranges[root] = NodeTextRange(0, sb.length)

    return ReaderTextOffsets(sb.toString(), ranges, boundaries)
}

/**
 * A direct child of the rendered root, paired with where its text begins in the
 * document's text stream.
 *
 * @param isRenderable whether the reader draws this node. Nodes that are not
 *   drawn — whitespace between tags, stray inline content — still occupy the
 *   stream, so they carry an offset even though nothing is emitted for them.
 */
data class ReaderTextSpan(
    val node: Node,
    val startOffset: Int,
    val isRenderable: Boolean
)

/**
 * The top-level children the reader renders one at a time, each with the offset
 * its text starts at, read straight off [offsets].
 */
fun computeReaderTextSpans(root: Element, offsets: ReaderTextOffsets): List<ReaderTextSpan> =
    root.childNodes().mapNotNull { child ->
        when (child) {
            is TextNode -> ReaderTextSpan(
                node = child,
                startOffset = offsets.startOf(child),
                isRenderable = child.getWholeText().isNotBlank()
            )

            is Element -> ReaderTextSpan(
                node = child,
                startOffset = offsets.startOf(child),
                isRenderable = isBlockElement(child)
            )

            else -> null
        }
    }

/**
 * The stream with whitespace collapsed, for matching selected text against it.
 *
 * [startAt] and [endAt] map each normalized character back to the stream: a block
 * boundary contributes a space that maps to a zero-width position, so a selection
 * spanning two blocks matches without the boundary counting toward any offset.
 */
internal class NormalizedStream(
    val text: String,
    private val startAt: IntArray,
    private val endAt: IntArray
) {
    val lowercase: String by lazy { text.lowercase() }

    fun streamStart(normalizedIndex: Int): Int = startAt[normalizedIndex]

    fun streamEnd(normalizedIndex: Int): Int = endAt[normalizedIndex - 1]
}

private fun normalizeStream(text: String, boundaries: Set<Int>): NormalizedStream {
    val capacity = text.length + boundaries.size
    val normalized = StringBuilder(capacity)
    val startAt = IntArray(capacity)
    val endAt = IntArray(capacity)
    var size = 0
    var lastWasSpace = false

    fun append(char: Char, start: Int, end: Int) {
        normalized.append(char)
        startAt[size] = start
        endAt[size] = end
        size++
    }

    for (i in text.indices) {
        // A boundary collapses into whatever whitespace is already there
        if (!lastWasSpace && i in boundaries) {
            append(' ', i, i)
            lastWasSpace = true
        }
        val char = text[i]
        if (char.isWhitespace()) {
            if (!lastWasSpace) {
                append(' ', i, i)
                lastWasSpace = true
            }
        } else {
            append(char, i, i + 1)
            lastWasSpace = false
        }
    }

    return NormalizedStream(normalized.toString(), startAt.copyOf(size), endAt.copyOf(size))
}

/** Whitespace, NBSP included, collapsed the way [normalizeStream] collapses the document. */
internal fun normalizeForMatching(text: String): String =
    text.replace('\u00A0', ' ').replace(Regex("\\s+"), " ").trim()

/**
 * Where each piece of the document's text landed in a rendered string.
 *
 * A renderer's string is not the stream: it carries a newline for a `<br>`, a
 * space for an inline image, a bullet's worth of nothing for a list marker. So
 * rather than counting stream offsets alongside the text as it is built — the
 * accounting that kept drifting — each text node records the one fact that ties
 * the two together, and a stream range is translated through those records.
 */
class TextRuns {
    private data class Run(val builderStart: Int, val streamStart: Int, val length: Int)

    private val runs = mutableListOf<Run>()

    /** Stream offset where the recorded text begins, or 0 if nothing was recorded. */
    var streamStart: Int = 0
        private set

    /** Stream offset just past the recorded text, or 0 if nothing was recorded. */
    var streamEnd: Int = 0
        private set

    fun record(builderStart: Int, streamStart: Int, length: Int) {
        if (length <= 0) return
        if (runs.isEmpty()) this.streamStart = streamStart
        this.streamEnd = streamStart + length
        runs += Run(builderStart, streamStart, length)
    }

    /** Does [start] until [end] of the stream touch any of the recorded text? */
    fun overlaps(start: Int, end: Int): Boolean =
        runs.isNotEmpty() && start < streamEnd && end > streamStart

    /**
     * The positions in the rendered string covering [start] until [end] of the
     * stream, or null if none of it was rendered here. Text the renderer added on
     * its own falls inside the returned range when it sits between two runs, which
     * is what keeps a highlight whole across a `<br>`.
     */
    fun localRange(start: Int, end: Int): IntRange? {
        var localStart = -1
        var localEnd = -1
        for (run in runs) {
            val runEnd = run.streamStart + run.length
            if (runEnd <= start || run.streamStart >= end) continue
            if (localStart == -1) {
                localStart = run.builderStart + (start - run.streamStart).coerceIn(0, run.length)
            }
            localEnd = run.builderStart + (end - run.streamStart).coerceIn(0, run.length)
        }
        return if (localStart == -1 || localEnd <= localStart) null else localStart until localEnd
    }
}
