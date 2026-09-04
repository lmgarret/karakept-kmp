package com.karakept.app.ui.components.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.fleeksoft.ksoup.Ksoup
import com.karakept.app.utils.HtmlSanitizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests that `<pre>` blocks containing `<div>` children (e.g. ASCII diagrams)
 * render with proper line breaks instead of collapsing into a single line.
 *
 * Regression test for GitHub #171.
 */
class PreDivRenderingTest {

    private val theme = ReaderThemeData(
        textColor = Color.Black,
        backgroundColor = Color.White,
        fontSize = 16.sp,
        fontFamily = FontFamily.Monospace,
        linkColor = Color.Blue,
        codeBackgroundColor = Color.LightGray
    )

    private fun buildText(html: String): String {
        val doc = Ksoup.parse(html)
        val pre = doc.selectFirst("pre")!!
        val offset = TextOffsetTracker()
        return buildInlineAnnotatedString(pre, theme, emptyList(), offset, {}, {}).text
    }

    /** Non-blank lines from rendered text */
    private fun nonBlankLines(text: String): List<String> =
        text.split("\n").filter { it.isNotBlank() }

    @Test
    fun preDivChildren_produceNewlines() {
        val html = "<pre><div>Line 1</div><div>Line 2</div><div>Line 3</div></pre>"
        val lines = nonBlankLines(buildText(html))
        assertEquals(3, lines.size, "Expected 3 lines from 3 <div> children, got: ${lines.size}")
        assertEquals("Line 1", lines[0].trim())
        assertEquals("Line 2", lines[1].trim())
        assertEquals("Line 3", lines[2].trim())
    }

    @Test
    fun preDivWithSpans_preservesContent() {
        val html = """<pre><div><span class="border">┌────</span> <span class="label">LINEAR</span> <span class="border">────┐</span></div><div><span class="border">│</span>    Content    <span class="border">│</span></div><div><span class="border">└──────────────────┘</span></div></pre>"""

        val lines = nonBlankLines(buildText(html))
        assertEquals(3, lines.size, "Expected 3 lines, got: ${lines.size}")
        assertTrue(lines[0].contains("LINEAR"), "First line should contain LINEAR")
        assertTrue(lines[1].contains("Content"), "Second line should contain Content")
        assertTrue(lines[2].contains("┘"), "Third line should contain closing border")
    }

    @Test
    fun preWithCodeChild_stillWorks() {
        val html = "<pre><code>function hello() {\n    return \"world\";\n}</code></pre>"

        val doc = Ksoup.parse(html)
        val pre = doc.selectFirst("pre")!!
        val codeElement = pre.selectFirst("code") ?: pre
        val offset = TextOffsetTracker()
        val text = buildInlineAnnotatedString(codeElement, theme, emptyList(), offset, {}, {}).text

        assertTrue(text.contains("function hello()"), "Should contain function declaration")
        assertTrue(text.contains("return"), "Should contain return statement")
    }

    @Test
    fun complexDiagramWithNestedSpans_producesMultipleLines() {
        // Simplified version of the linear.app/next diagram from issue #171
        val html = """<pre><div><span class="outer">     </span><span class="border">┌──────────</span> <span class="label">LINEAR</span> <span class="border">──────────┐</span></div><div><span class="border">     │                                   │</span></div><div><span class="outer">     </span><span class="border">│</span>  <span class="q">┌───────</span> <span class="p">Context</span> <span class="q">──────┐</span>  <span class="border">│</span></div><div><span class="outer">     </span><span class="border">│</span>  <span class="q">│</span>        Plans         <span class="q">│</span>  <span class="border">│</span></div><div><span class="outer"></span>Customer requests    <span class="border">│</span>  <span class="q">│</span>        Technical      <span class="q">│</span>  <span class="border">│</span></div><div><span class="outer">     </span>Feedback    <span class="border">│</span>  <span class="q">│</span>        Decisions      <span class="q">│</span>  <span class="border">│</span></div><div><span class="outer">     </span><span class="border">│</span>  <span class="q">└──────────────────────┘</span>  <span class="border">│</span></div><div><span class="border">     └───────────────────────────────────┘</span></div></pre>"""

        val lines = nonBlankLines(buildText(html))
        assertEquals(8, lines.size, "Expected 8 lines from 8 <div> children, got: ${lines.size}")
        assertTrue(lines[0].contains("LINEAR"), "First line should contain LINEAR")
        assertTrue(lines[3].contains("Plans"), "Fourth line should contain Plans")
        assertTrue(lines[4].contains("Customer requests"), "Fifth line should contain Customer requests")
        assertTrue(lines[5].contains("Feedback"), "Sixth line should contain Feedback")
    }

    @Test
    fun spanInsidePre_noExtraNewlines() {
        val html = "<pre><span class=\"a\">Hello</span> <span class=\"b\">World</span></pre>"
        val lines = nonBlankLines(buildText(html))
        assertEquals(1, lines.size, "Spans should not create newlines, got: ${lines.size}")
        assertEquals("Hello World", lines[0].trim())
    }

    @Test
    fun sanitizer_preservesPreDivStructure() {
        // Verify the sanitizer doesn't destroy the <div> children inside <pre>
        val html = """<pre class="sc-d5151d0-0"><div><span class="ArchitectureDiagram_border__ZTbCZ">┌── LINEAR ──┐</span></div><div><span class="ArchitectureDiagram_border__ZTbCZ">│            │</span></div><div><span class="ArchitectureDiagram_border__ZTbCZ">└────────────┘</span></div></pre>"""

        val sanitized = HtmlSanitizer.sanitize(html)
        println("Sanitized HTML: [$sanitized]")
        // After sanitizing, parse and render
        val doc = Ksoup.parse(sanitized)
        val pre = doc.selectFirst("pre")
        assertTrue(pre != null, "Sanitized HTML should still contain <pre>: [$sanitized]")

        val offset = TextOffsetTracker()
        val text = buildInlineAnnotatedString(pre, theme, emptyList(), offset, {}, {}).text
        val lines = nonBlankLines(text)
        println("Rendered after sanitize: [$text]")
        assertEquals(3, lines.size, "Expected 3 lines after sanitize+render, got: ${lines.size}\nSanitized: [$sanitized]\nRendered: [$text]")
    }

    @Test
    fun ksoupParsing_divInsidePre_preservedCorrectly() {
        // Test that Ksoup doesn't restructure <div> inside <pre>
        // (HTML5 spec says <div> inside <pre> is valid, but some parsers
        // may "break out" block elements from <pre>)
        val html = "<pre><div>Line A</div><div>Line B</div></pre>"
        val doc = Ksoup.parse(html)
        val pre = doc.selectFirst("pre")!!
        val divs = pre.select("div")
        println("Pre children: ${pre.childNodes().map { it.nodeName() }}")
        println("Divs found inside pre: ${divs.size}")
        assertEquals(2, divs.size, "Ksoup should keep <div> inside <pre>. Pre HTML: ${pre.outerHtml()}")
    }

    @Test
    fun preWithPlainTextNewlines_preservesLines() {
        // After Readability strips div/span, <pre> might just have text with newlines
        val html = "<pre>Line 1\nLine 2\nLine 3</pre>"
        val lines = nonBlankLines(buildText(html))
        assertEquals(3, lines.size, "Plain text with \\n in <pre> should produce 3 lines, got: ${lines.size}\nFull text: [${buildText(html)}]")
    }

    @Test
    fun preWithWhitespaceOnlySpans_rendersBoxDrawing() {
        // Test that spans with only whitespace + box-drawing chars are visible
        val html = """<pre><div><span>     ┌──── </span><span>LINEAR</span><span> ────┐</span></div><div><span>     │            │</span></div></pre>"""
        val text = buildText(html)
        val lines = nonBlankLines(text)
        assertTrue(lines.any { it.contains("┌") }, "Box-drawing characters should be rendered\nFull text: [$text]")
        assertTrue(lines.any { it.contains("│") }, "Vertical box-drawing should be rendered\nFull text: [$text]")
    }

    @Test
    fun sanitizerThenKsoup_preWithDivSpans_fullPipeline() {
        // Full pipeline: raw HTML → sanitize → parse → render
        // Using actual issue HTML structure
        val rawHtml = """<div class="page_block__ikCbZ"><div class="Bleed_root__EzNZN"><div class="ArchitectureDiagram_root__vTOE1"><pre class="sc-d5151d0-0 hNRjEg"><div><span class="ArchitectureDiagram_border__ZTbCZ">     ┌──── <span class="ArchitectureDiagram_outer__NIjgK">LINEAR</span> ────┐</span></div><div><span class="ArchitectureDiagram_border__ZTbCZ">     │                   │</span></div><div>     Bug reports  ◀──▶  Skills</div><div><span class="ArchitectureDiagram_outer__NIjgK">     </span>Feedback</div><div><span class="ArchitectureDiagram_border__ZTbCZ">     └───────────────────┘</span></div></pre></div></div></div>"""

        val sanitized = HtmlSanitizer.sanitize(rawHtml)
        println("Full pipeline sanitized: [$sanitized]")
        val doc = Ksoup.parse(sanitized)
        val pre = doc.selectFirst("pre")
        assertTrue(pre != null, "Pre should survive sanitization: [$sanitized]")

        val offset = TextOffsetTracker()
        val text = buildInlineAnnotatedString(pre, theme, emptyList(), offset, {}, {}).text
        val lines = nonBlankLines(text)
        println("Full pipeline rendered (${lines.size} lines): [$text]")
        assertTrue(lines.size >= 4, "Expected at least 4 visible lines, got: ${lines.size}\nSanitized: [$sanitized]\nRendered: [$text]")
        assertTrue(lines.any { it.contains("LINEAR") }, "Should contain LINEAR")
        assertTrue(lines.any { it.contains("Bug reports") }, "Should contain Bug reports")
        assertTrue(lines.any { it.contains("Feedback") }, "Should contain Feedback")
    }

    @Test
    fun preParagraphChildren_produceNewlines() {
        // Server-side Readability converts <div> inside <pre> to <p>
        // This is the ACTUAL structure observed on device via logcat
        val html = "<pre><p>Line 1</p><p>Line 2</p><p>Line 3</p></pre>"
        val lines = nonBlankLines(buildText(html))
        assertEquals(3, lines.size, "Expected 3 lines from 3 <p> children, got: ${lines.size}\nFull text: [${buildText(html)}]")
    }

    @Test
    fun preParagraphWithSpans_matchesActualServerOutput() {
        // Actual server output: <pre> with <p><span>...</span></p> children
        val html = """<pre><p><span>     ┌──── <span>LINEAR</span> ────┐</span></p><p><span>     │                   │</span></p><p><span>     </span><span>│</span>  <span>┌───</span> <span>Context</span> <span>───┐</span>  <span>│</span></p><p>     Bug reports  ◀──▶  Skills</p><p><span>     </span>Feedback</p><p><span>     └───────────────────┘</span></p></pre>"""

        val lines = nonBlankLines(buildText(html))
        assertEquals(6, lines.size, "Expected 6 lines from 6 <p> children, got: ${lines.size}\nFull text: [${buildText(html)}]")
        assertTrue(lines[0].contains("LINEAR"), "First line should contain LINEAR")
        assertTrue(lines[3].contains("Bug reports"), "Fourth line should contain Bug reports")
        assertTrue(lines[4].contains("Feedback"), "Fifth line should contain Feedback")
    }

    @Test
    fun preP_whitespaceAlignment() {
        // Test that whitespace inside <p> within <pre> is preserved for alignment
        val html = "<pre><p>     ┌────┐</p><p>     │    │</p><p>     └────┘</p></pre>"
        val text = buildText(html)
        val lines = text.split("\n").filter { it.isNotEmpty() }
        println("Alignment test lines:")
        lines.forEachIndexed { i, line -> println("  [$i] '${line}' (len=${line.length})") }

        // All lines should have same leading spaces (5 spaces)
        assertTrue(lines.all { it.startsWith("     ") }, "All lines should have 5 leading spaces")
        // Check right-side border alignment: ┐, │, ┘ should be at same position
        val topEnd = lines[0].indexOf("┐")
        val midEnd = lines[1].lastIndexOf("│")
        val botEnd = lines[2].indexOf("┘")
        assertEquals(topEnd, midEnd, "Right border should align: ┐ at $topEnd, │ at $midEnd")
        assertEquals(topEnd, botEnd, "Right border should align: ┐ at $topEnd, ┘ at $botEnd")
    }

    @Test
    fun ksoup_pInsidePre_whitespacePreservation() {
        // Verify Ksoup preserves multiple spaces inside <p> within <pre>
        val html = "<pre><p>     Hello     World</p></pre>"
        val doc = Ksoup.parse(html)
        val p = doc.selectFirst("pre p")!!
        val textNode = p.textNodes().first()
        val wholeText = textNode.getWholeText()
        println("getWholeText: '$wholeText' (len=${wholeText.length})")
        assertTrue(wholeText.startsWith("     "), "Leading spaces should be preserved, got: '$wholeText'")
        assertTrue(wholeText.contains("     World"), "Middle spaces should be preserved, got: '$wholeText'")
    }

    @Test
    fun nestedDivP_noDoubleNewlines() {
        // Server wraps some lines in <div><p>...</p><p>...</p></div>
        // The <div> and each <p> are block elements — without dedup,
        // the closing </p></div> would emit two newlines (blank line)
        val html = "<pre><p>Line A</p><div><p>Part 1</p><p>Part 2</p></div><p>Line B</p></pre>"
        val text = buildText(html)
        println("Nested div/p text: [$text]")
        // Should NOT have double newlines (blank lines) between sections
        assertTrue(!text.contains("\n\n"), "Should not have double newlines (blank lines)\nFull text: [$text]")
        val lines = nonBlankLines(text)
        assertEquals(4, lines.size, "Expected 4 lines: A, Part1, Part2, B — got: ${lines.size}\nFull text: [$text]")
    }

    @Test
    fun actualServerHtml_splitLineNoBlanks() {
        // Actual structure from logcat: a <div> wrapping 3 <p> elements inside <pre>
        val html = """<pre><p><span>     │  │  Specs     │  │</span></p><div><p>Bug reports  ◀──▶  Technical</p><p>░░░░░▓▒▒░░</p><p>   ◀──▶  Product</p></div><p><span>     Feedback    │  │</span></p></pre>"""
        val text = buildText(html)
        println("Server split line: [$text]")
        assertTrue(!text.contains("\n\n"), "No blank lines from nested div/p\nFull text: [$text]")
        val lines = nonBlankLines(text)
        assertEquals(5, lines.size, "Expected 5 lines (no extra blank from div wrapper), got: ${lines.size}\nFull text: [$text]")
    }

    @Test
    fun actualIssueHtml_rendersAllDivLines() {
        // Actual HTML structure from GitHub #171 (simplified to core structure)
        val html = """<pre class="sc-d5151d0-0"><div><span class="ArchitectureDiagram_border__ZTbCZ">                     ┌────────────── <span class="ArchitectureDiagram_outer__NIjgK">LINEAR</span> ──────────────┐</span></div><div><span class="ArchitectureDiagram_border__ZTbCZ">                     │                                            │</span></div><div><span class="ArchitectureDiagram_outer__NIjgK">                     </span><span class="ArchitectureDiagram_border__ZTbCZ">│</span>  <span class="ArchitectureDiagram_quaternary__Ul66t">┌───────</span> <span class="ArchitectureDiagram_primary__LKDs2">Context</span> <span class="ArchitectureDiagram_quaternary__Ul66t">──────┐</span>  <span class="ArchitectureDiagram_border__ZTbCZ">│</span></div><div><span class="ArchitectureDiagram_outer__NIjgK">                     </span><span class="ArchitectureDiagram_border__ZTbCZ">│</span>  <span class="ArchitectureDiagram_quaternary__Ul66t">│</span>        Plans          <span class="ArchitectureDiagram_quaternary__Ul66t">│</span>  <span class="ArchitectureDiagram_border__ZTbCZ">│</span></div><div><span class="ArchitectureDiagram_outer__NIjgK"></span>Customer requests    <span class="ArchitectureDiagram_border__ZTbCZ">│</span>  <span class="ArchitectureDiagram_quaternary__Ul66t">│</span>        Technical      <span class="ArchitectureDiagram_quaternary__Ul66t">│</span>  <span class="ArchitectureDiagram_border__ZTbCZ">│</span></div><div>      Bug reports  ◀──────▶      Technical designs        ◀───▶      Skills</div><div><span class="ArchitectureDiagram_outer__NIjgK">         </span>Feedback    <span class="ArchitectureDiagram_border__ZTbCZ">│</span>  <span class="ArchitectureDiagram_quaternary__Ul66t">│</span>        Decisions      <span class="ArchitectureDiagram_quaternary__Ul66t">│</span>  <span class="ArchitectureDiagram_border__ZTbCZ">│</span></div><div><span class="ArchitectureDiagram_outer__NIjgK">                     </span><span class="ArchitectureDiagram_border__ZTbCZ">│</span>  <span class="ArchitectureDiagram_quaternary__Ul66t">└──────────────────────┘</span>  <span class="ArchitectureDiagram_border__ZTbCZ">│</span></div><div><span class="ArchitectureDiagram_border__ZTbCZ">                     └────────────────────────────────────────────┘</span></div></pre>"""

        val lines = nonBlankLines(buildText(html))
        assertEquals(9, lines.size, "Expected 9 lines from 9 <div> children, got: ${lines.size}\nFull text: [${buildText(html)}]")
        assertTrue(lines[0].contains("LINEAR"), "First line should contain LINEAR")
        assertTrue(lines[2].contains("Context"), "Third line should contain Context")
        assertTrue(lines[5].contains("Bug reports"), "Sixth line should contain Bug reports")
        assertTrue(lines[6].contains("Feedback"), "Seventh line should contain Feedback")
    }
}
