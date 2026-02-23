package com.karakept.app.ui.components

import android.graphics.Color
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.util.Log
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.karakept.app.data.model.ReaderFontFamily
import com.karakept.app.data.model.ViewerMode
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color as ComposeColor
import org.json.JSONArray
import org.json.JSONObject

/**
 * Android implementation of HtmlRenderer using WebView.
 *
 * Security measures:
 * - JavaScript disabled (always, in both modes)
 * - File access disabled
 * - Content access disabled
 * - Mixed content blocked
 * - Link clicks intercepted
 */
@Composable
actual fun HtmlRenderer(
    html: String,
    viewerMode: ViewerMode,
    modifier: Modifier,
    onLinkClick: ((String) -> Unit)?,
    onLoaded: (() -> Unit)?,
    customTextColor: ComposeColor?,
    customFontSize: Int,
    customFontFamily: ReaderFontFamily,
    localFilePath: String?,
    highlights: List<com.karakept.app.data.model.Highlight>,
    onCreateHighlight: (String, Int, Int, String?, String?) -> Unit,
    onDeleteHighlight: (String) -> Unit,
    onHighlightClick: (String) -> Unit,
    onHighlightPosition: ((String, com.karakept.app.ui.components.HighlightPosition?) -> Unit)?
) {
    val lastLoadedHtml = remember { mutableStateOf<String?>(null) }
    val lastAppliedHighlights = remember { mutableStateOf<List<com.karakept.app.data.model.Highlight>>(emptyList()) }
    val pageLoaded = remember { mutableStateOf(false) }

    // Wrap onLinkClick in a MutableState so the WebViewClient always invokes the latest
    // lambda even when linkOpenMode changes after the AndroidView factory has run.
    val onLinkClickState = remember { mutableStateOf(onLinkClick) }

    // Track selection bounds for ActionMode positioning
    val selectionRect = remember { mutableStateOf<android.graphics.Rect?>(null) }

    val webViewReference = remember { mutableStateOf<WebView?>(null) }
    // Use custom text color if provided, otherwise default to a fixed color (e.g., Black/White based on theme) 
    // or keep using onSurface but ensure it's what the user wants.
    // The user requested that text color should NOT change with dynamic color.
    // If we use onSurface, it WILL change with dynamic color.
    // So we should probably default to a standard color if customTextColor is null, 
    // OR we can rely on the fact that onSurface might be tinted in dynamic themes.
    // Let's use a more neutral default if customTextColor is null, or just stick to onSurface 
    // but maybe the user implies they want a specific color that doesn't shift.
    // However, the best way to "stay the same" is to use the custom color logic.
    // If the user hasn't set a custom color, it defaults to onSurface.
    // If onSurface changes with dynamic theme (which it does), that's the issue.
    // We should probably default to a non-dynamic color if no custom color is set, 
    // OR explicitly set a default that isn't influenced by the dynamic palette if that's the preference.
    // But standard Material Design says onSurface SHOULD match the theme.
    // If the user wants it to "stay the same", they might mean "stay black/white" regardless of the pink/blue tint.
    
    // Let's check if we can get a non-dynamic onSurface. 
    // Actually, if the user selects "Dynamic", the whole theme is dynamic.
    // If they want the text to NOT be dynamic, they should probably set a custom color.
    // BUT, if they haven't set a custom color, maybe we should default to standard Black/White 
    // based on dark mode, ignoring the dynamic tint.
    
    val isDark = androidx.compose.foundation.isSystemInDarkTheme() // This might not match app theme if forced
    // Better to check the luminance of the background or surface to decide default text color
    // But we don't have easy access to "isDark" boolean here directly without passing it.
    // However, MaterialTheme.colorScheme.surface is available.
    
    // Memoize color values to prevent unnecessary recompositions
    val surfaceColor = MaterialTheme.colorScheme.surface
    val primaryColor = MaterialTheme.colorScheme.primary

    val defaultTextColor = if (surfaceColor.luminance() > 0.5f) ComposeColor.Black else ComposeColor.White
    val textColorHex = remember(customTextColor, defaultTextColor) {
        val color = (customTextColor ?: defaultTextColor).toArgb()
        String.format("#%06X", 0xFFFFFF and color)
    }
    val backgroundColorHex = remember(surfaceColor) {
        val color = surfaceColor.toArgb()
        String.format("#%06X", 0xFFFFFF and color)
    }
    val linkColorHex = remember(primaryColor) {
        val color = primaryColor.toArgb()
        String.format("#%06X", 0xFFFFFF and color)
    }

    val highlightStyles = remember {
        """
        mark.karakept-highlight {
            background-color: #ffeb3b !important; /* Brighter Yellow */
            color: black !important;
            cursor: pointer;
        }
        mark.karakept-highlight.blue { background-color: #2196f3 !important; }
        mark.karakept-highlight.green { background-color: #4caf50 !important; }
        mark.karakept-highlight.red { background-color: #f44336 !important; }
        """.trimIndent()
    }

    val highlightScripts = remember {
        """
        function log(msg) {
            if (window.Android && window.Android.onLog) {
                window.Android.onLog(msg);
            }
        }

        function applyHighlights(highlights) {
            log("Applying " + highlights.length + " highlights");

            // Build a map of new highlights by ID for fast lookup
            const newHighlightsMap = {};
            highlights.forEach(h => {
                newHighlightsMap[h.id] = h;
            });

            // Find existing highlight elements
            const existing = document.querySelectorAll('mark.karakept-highlight');
            const existingIds = new Set();

            // Update or remove existing highlights
            existing.forEach(el => {
                const id = el.dataset.id;
                existingIds.add(id);

                if (!newHighlightsMap[id]) {
                    // Highlight no longer exists - remove it
                    const parent = el.parentNode;
                    while(el.firstChild) parent.insertBefore(el.firstChild, el);
                    parent.removeChild(el);
                } else {
                    // Highlight still exists - check if color changed
                    const newColor = newHighlightsMap[id].color || 'yellow';
                    const currentClass = 'karakept-highlight ' + newColor;
                    if (el.className !== currentClass) {
                        el.className = currentClass;
                    }
                }
            });

            document.body.normalize(); // Join adjacent text nodes after removals

            // Add new highlights that don't exist yet
            highlights.forEach(h => {
                if (!existingIds.has(h.id)) {
                    highlightOffsets(h.id, h.startOffset, h.endOffset, h.color, h.text);
                }
            });
        }

        function highlightOffsets(id, startOffset, endOffset, color, text) {
            try {
                log("Highlighting id=" + id + " range: " + startOffset + "-" + endOffset + ", text length: " + (text ? text.length : 'N/A'));
                const root = document.getElementById('karakept-content') || document.body;
                log("Root element: " + (root.id || root.tagName) + ", total text length: " + root.textContent.length);

                // First try offset-based highlighting
                const ranges = getRangesFromOffsets(root, startOffset, endOffset);

                // Check if offset-based approach found reasonable results
                // If text is provided, verify that we're highlighting approximately the right amount
                const expectedLength = endOffset - startOffset;
                let actualLength = 0;
                if (ranges) {
                    ranges.forEach(r => actualLength += (r.end - r.start));
                }

                const offsetBasedWorks = ranges && ranges.length > 0 &&
                    (actualLength >= expectedLength * 0.8); // At least 80% of expected text

                if (offsetBasedWorks) {
                    log("Using offset-based highlighting: found " + ranges.length + " ranges, " + actualLength + "/" + expectedLength + " chars");
                    // Apply highlights in reverse order to avoid offset shifts
                    for (let i = ranges.length - 1; i >= 0; i--) {
                        const { node, start, end } = ranges[i];
                        wrapTextNode(node, start, end, id, color);
                    }
                } else if (text && text.length > 0) {
                    // Fall back to text-based search
                    log("Offset-based failed (found " + actualLength + "/" + expectedLength + " chars), trying text-based search");
                    highlightByText(id, text, color);
                } else {
                    log("No ranges found and no text provided for fallback");
                }
            } catch(e) { log("Highlight error: " + e.message + " stack: " + e.stack); }
        }

        function highlightByText(id, searchText, color) {
            try {
                const root = document.getElementById('karakept-content') || document.body;

                // Normalize the search text (collapse whitespace to single spaces)
                const normalizedSearch = searchText.replace(/\s+/g, ' ').trim();
                log("Searching for text: '" + normalizedSearch.substring(0, 50) + "...'");

                // Find text in the document using TreeWalker
                const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null, false);
                const textNodes = [];
                let node;
                while ((node = walker.nextNode())) {
                    textNodes.push(node);
                }

                // Build full text and create a mapping from original positions to normalized positions
                let fullText = '';
                textNodes.forEach(textNode => {
                    fullText += textNode.textContent;
                });

                // Create mapping: for each original position, what's the normalized position?
                // Also track: for each normalized position, what's the original position?
                const originalToNormalized = [];
                const normalizedToOriginal = [];
                let normalizedText = '';
                let lastWasSpace = false;

                for (let i = 0; i < fullText.length; i++) {
                    const char = fullText[i];
                    const isSpace = /\s/.test(char);

                    if (isSpace) {
                        if (!lastWasSpace) {
                            // First space in a sequence - add single space to normalized
                            originalToNormalized.push(normalizedText.length);
                            normalizedToOriginal.push(i);
                            normalizedText += ' ';
                            lastWasSpace = true;
                        } else {
                            // Additional space - map to same normalized position
                            originalToNormalized.push(normalizedText.length - 1);
                        }
                    } else {
                        originalToNormalized.push(normalizedText.length);
                        normalizedToOriginal.push(i);
                        normalizedText += char;
                        lastWasSpace = false;
                    }
                }
                // Add end marker
                normalizedToOriginal.push(fullText.length);

                log("Normalized text length: " + normalizedText.length + ", original: " + fullText.length);

                // Find the search text in normalized content
                const searchIndex = normalizedText.toLowerCase().indexOf(normalizedSearch.toLowerCase());
                if (searchIndex === -1) {
                    log("Text not found in normalized document. First 100 chars: '" + normalizedText.substring(0, 100) + "'");
                    return;
                }

                log("Found text at normalized index " + searchIndex);

                // Map back to original positions
                const originalStart = normalizedToOriginal[searchIndex];
                const searchEndNormalized = searchIndex + normalizedSearch.length;
                const originalEnd = searchEndNormalized < normalizedToOriginal.length
                    ? normalizedToOriginal[searchEndNormalized]
                    : fullText.length;

                log("Mapped to original positions: " + originalStart + "-" + originalEnd);

                // Now find all text nodes in this range and highlight them
                const rangesToWrap = [];
                let currentPos = 0;
                textNodes.forEach(textNode => {
                    const nodeStart = currentPos;
                    const nodeEnd = currentPos + textNode.textContent.length;

                    if (nodeStart < originalEnd && nodeEnd > originalStart) {
                        rangesToWrap.push({
                            node: textNode,
                            start: Math.max(0, originalStart - nodeStart),
                            end: Math.min(textNode.textContent.length, originalEnd - nodeStart)
                        });
                    }
                    currentPos = nodeEnd;
                });

                log("Found " + rangesToWrap.length + " ranges to wrap via text search");

                // Apply in reverse order
                for (let i = rangesToWrap.length - 1; i >= 0; i--) {
                    const { node, start, end } = rangesToWrap[i];
                    wrapTextNode(node, start, end, id, color);
                }
            } catch(e) { log("highlightByText error: " + e.message); }
        }

        function getRangesFromOffsets(root, highlightStart, highlightEnd) {
            const ranges = [];
            let currentOffset = 0;
            const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null, false);

            let node;
            while ((node = walker.nextNode())) {
                const nodeLength = node.textContent.length;
                const nodeStart = currentOffset;
                const nodeEnd = nodeStart + nodeLength;

                // Check if this node overlaps with highlight range
                if (nodeStart < highlightEnd && nodeEnd > highlightStart) {
                    ranges.push({
                        node: node,
                        start: Math.max(0, highlightStart - nodeStart),
                        end: Math.min(nodeLength, highlightEnd - nodeStart)
                    });
                }

                currentOffset += nodeLength;
            }
            return ranges;
        }

        function wrapTextNode(textNode, start, end, id, color) {
            // Skip if nothing to wrap
            if (start >= end || start >= textNode.textContent.length) {
                log("wrapTextNode skipping: start=" + start + ", end=" + end + ", nodeLen=" + textNode.textContent.length);
                return;
            }

            log("wrapTextNode: wrapping '" + textNode.textContent.substring(start, end) + "' (start=" + start + ", end=" + end + ")");

            let nodeToWrap = textNode;

            // Split at start if needed
            if (start > 0) {
                nodeToWrap = textNode.splitText(start);
                end -= start;  // Adjust end offset for the new node
            }

            // Split at end if needed
            if (end < nodeToWrap.textContent.length) {
                nodeToWrap.splitText(end);
            }

            // Create and insert the highlight span
            const span = document.createElement('mark');
            span.className = 'karakept-highlight ' + (color || 'yellow');
            span.dataset.id = id;
            span.onclick = (e) => {
                e.stopPropagation();
                Android.onHighlightClick(id);
            };

            nodeToWrap.parentNode.insertBefore(span, nodeToWrap);
            span.appendChild(nodeToWrap);
        }

        function getSelectionInfo() {
            try {
                const selection = window.getSelection();
                if (!selection || selection.rangeCount === 0 || selection.isCollapsed) return null;
                const range = selection.getRangeAt(0);
                const text = selection.toString();

                // Measure offset using a walker to be consistent with getRangeFromOffsets
                let start = 0;
                const root = document.getElementById('karakept-content') || document.body;
                const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null, false);
                let node;
                while (node = walker.nextNode()) {
                    if (node === range.startContainer) {
                        start += range.startOffset;
                        break;
                    }
                    start += node.textContent.length;
                }

                return JSON.stringify({
                    text: text,
                    start: start,
                    end: start + text.length
                });
            } catch(e) { log("Selection info error: " + e.message); return null; }
        }

        function getHighlightPosition(highlightId) {
            try {
                const marks = document.querySelectorAll('mark.karakept-highlight[data-id="' + highlightId + '"]');
                if (marks.length === 0) return null;

                // Get the bounding rect of the first mark element
                const rect = marks[0].getBoundingClientRect();

                return JSON.stringify({
                    x: rect.left,
                    y: rect.top,
                    width: rect.width,
                    height: rect.height,
                    scrollY: window.scrollY,
                    scrollX: window.scrollX
                });
            } catch(e) { log("Get highlight position error: " + e.message); return null; }
        }

        function scrollToHighlight(highlightId) {
            try {
                const marks = document.querySelectorAll('mark.karakept-highlight[data-id="' + highlightId + '"]');
                if (marks.length === 0) {
                    log("scrollToHighlight: no marks found for id=" + highlightId);
                    return false;
                }

                // Scroll the first mark element into view with some offset from the top
                marks[0].scrollIntoView({ behavior: 'smooth', block: 'center' });
                log("scrollToHighlight: scrolled to highlight id=" + highlightId);
                return true;
            } catch(e) {
                log("scrollToHighlight error: " + e.message);
                return false;
            }
        }

        // Track selection changes for ActionMode positioning
        document.addEventListener('selectionchange', function() {
            const selection = window.getSelection();
            if (selection.rangeCount > 0) {
                const range = selection.getRangeAt(0);
                const rect = range.getBoundingClientRect();
                if (window.Android && window.Android.onSelectionChanged) {
                    window.Android.onSelectionChanged(rect.left, rect.top, rect.right, rect.bottom);
                }
            }
        });
        """.trimIndent()
    }

    val themedHtml = remember(html, viewerMode, textColorHex, backgroundColorHex, linkColorHex, customFontSize, customFontFamily, highlightStyles, highlightScripts) {
        when (viewerMode) {
            ViewerMode.READER -> {
                // Reader mode: wrap with base styles
                """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                    <meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src http: https: data: file:; style-src 'unsafe-inline'; script-src 'unsafe-inline';">
                    <style>
                        * {
                            margin: 0;
                            padding: 0;
                            box-sizing: border-box;
                        }
                        html, body {
                            overflow-x: hidden;
                            max-width: 100%;
                        }
                        body {
                            color: $textColorHex;
                            background-color: transparent;
                            font-family: ${customFontFamily.cssValue};
                            font-size: ${customFontSize}px;
                            line-height: 1.6;
                            padding: 0 28px 28px 28px;
                            margin: 0;
                            word-wrap: break-word;
                            overflow-wrap: break-word;
                        }
                        a {
                            color: $linkColorHex;
                            text-decoration: underline;
                        }
                        img {
                            max-width: 100%;
                            height: auto;
                            display: block;
                            margin: 8px 0;
                        }
                        pre {
                            overflow-x: auto;
                            padding: 8px;
                            background-color: rgba(127, 127, 127, 0.1);
                            border-radius: 4px;
                            margin: 8px 0;
                        }
                        code {
                            font-family: "Courier New", Courier, monospace;
                            font-size: 14px;
                        }
                        blockquote {
                            border-left: 4px solid $linkColorHex;
                            padding-left: 12px;
                            margin: 8px 0;
                            font-style: italic;
                        }
                        ul, ol {
                            padding-left: 24px;
                            margin: 8px 0;
                        }
                        p {
                            margin: 8px 0;
                        }
                        h1, h2, h3, h4, h5, h6 {
                            margin: 12px 0 8px 0;
                            font-weight: bold;
                        }
                        figure {
                            margin: 16px 0;
                        }
                        figcaption {
                            text-align: center;
                            font-size: 14px;
                            font-style: italic;
                            color: ${textColorHex}CC;
                            margin-bottom: 8px;
                        }
                        
                        /* Highlight styles */
                        $highlightStyles
                    </style>
                </head>
                <body>
                    <div id="karakept-content">
                        $html
                    </div>
                    <script>
                        $highlightScripts
                    </script>
                </body>
                </html>
                """.trimIndent()
            }
            ViewerMode.WEB -> {
                // Archive mode: inject our scripts and styles into the existing HTML
                html
                    .replace("</head>", """
                        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                        <meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src http: https: data: file:; style-src 'unsafe-inline' http: https:; script-src 'unsafe-inline';">
                        <style>
                            $highlightStyles
                        </style>
                    </head>""".trimIndent())
                    .replace("</body>", """
                        <script>
                            $highlightScripts
                        </script>
                    </body>""".trimIndent())
            }
        }
    }

    class WebAppInterface(
        private val onCreate: (String, Int, Int, String?, String?) -> Unit,
        private val onDelete: (String) -> Unit,
        private val onClick: (String) -> Unit,
        private val webView: WebView?,
        private val onPosition: ((String, com.karakept.app.ui.components.HighlightPosition?) -> Unit)?,
        private val selectionRect: androidx.compose.runtime.MutableState<android.graphics.Rect?>
    ) {
        @JavascriptInterface
        fun onHighlightClick(id: String) {
            onClick(id)

            // Get highlight position asynchronously
            webView?.evaluateJavascript("getHighlightPosition('$id')") { positionJson ->
                if (positionJson != null && positionJson != "null" && positionJson.isNotBlank()) {
                    try {
                        val cleaned = if (positionJson.startsWith("\"") && positionJson.endsWith("\"")) {
                            positionJson.substring(1, positionJson.length - 1)
                                .replace("\\\"", "\"")
                                .replace("\\\\", "\\")
                        } else positionJson

                        val json = org.json.JSONObject(cleaned)
                        val position = com.karakept.app.ui.components.HighlightPosition(
                            x = json.getDouble("x").toFloat(),
                            y = json.getDouble("y").toFloat(),
                            width = json.getDouble("width").toFloat(),
                            height = json.getDouble("height").toFloat(),
                            scrollX = json.getDouble("scrollX").toFloat(),
                            scrollY = json.getDouble("scrollY").toFloat()
                        )
                        onPosition?.invoke(id, position)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        onPosition?.invoke(id, null)
                    }
                } else {
                    onPosition?.invoke(id, null)
                }
            }
        }
        
        @JavascriptInterface
        fun onTextSelected(selectionJson: String) {
            // This could be called from a native menu
        }

        @JavascriptInterface
        fun onSelectionChanged(left: Float, top: Float, right: Float, bottom: Float) {
            webView?.post {
                // getBoundingClientRect() returns CSS pixels, need to convert to device pixels
                val scale = webView?.scale ?: 1f
                android.util.Log.d("HtmlRenderer", "onSelectionChanged: CSS coords ($left, $top, $right, $bottom), scale=$scale")
                selectionRect.value = android.graphics.Rect(
                    (left * scale).toInt(),
                    (top * scale).toInt(),
                    (right * scale).toInt(),
                    (bottom * scale).toInt()
                )
                android.util.Log.d("HtmlRenderer", "onSelectionChanged: Device coords ${selectionRect.value}")
            }
        }

        @JavascriptInterface
        fun onLog(message: String) {
            println("WebView Log: $message")
        }
    }

    val webInterface = remember(onCreateHighlight, onDeleteHighlight, onHighlightClick, onHighlightPosition, webViewReference.value, selectionRect) {
        WebAppInterface(onCreateHighlight, onDeleteHighlight, onHighlightClick, webViewReference.value, onHighlightPosition, selectionRect)
    }

    fun applyHighlightsToWebView(view: WebView?, highlights: List<com.karakept.app.data.model.Highlight>) {
        if (view == null) return
        val highlightsJson = JSONArray().apply {
            highlights.forEach { h ->
                put(JSONObject().apply {
                    put("id", h.id)
                    put("startOffset", h.startOffset)
                    put("endOffset", h.endOffset)
                    put("color", h.color ?: "yellow")
                    put("text", h.text)  // Include text for fallback search
                })
            }
        }.toString()
        view.evaluateJavascript("if(window.applyHighlights) applyHighlights($highlightsJson)", null)
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            object : WebView(context) {
                private val HIGHLIGHT_MENU_ID = 0x7f0f0001 // Unique ID for highlight menu item

                override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? {
                    return super.startActionMode(wrapCallback(callback), type)
                }

                override fun startActionMode(callback: ActionMode.Callback?): ActionMode? {
                    return super.startActionMode(wrapCallback(callback))
                }

                private fun wrapCallback(callback: ActionMode.Callback?): ActionMode.Callback? {
                    if (callback == null) return null
                    if (callback is WrappedActionModeCallback) return callback

                    return WrappedActionModeCallback(callback)
                }

                inner class WrappedActionModeCallback(private val originalCallback: ActionMode.Callback) : ActionMode.Callback2() {
                    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                        val result = originalCallback.onCreateActionMode(mode, menu)

                        // Add "Highlight" with order 2 to appear just after Copy (which typically has order 1)
                        if (menu?.findItem(HIGHLIGHT_MENU_ID) == null) {
                            menu?.add(Menu.NONE, HIGHLIGHT_MENU_ID, 2, "Highlight")
                                ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                        }
                        return result
                    }

                    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                        return originalCallback.onPrepareActionMode(mode, menu)
                    }

                    override fun onGetContentRect(mode: ActionMode?, view: android.view.View?, outRect: android.graphics.Rect?) {
                        // Let the WebView handle selection rectangle positioning natively
                        // The default implementation properly tracks the selection position
                        if (originalCallback is ActionMode.Callback2) {
                            (originalCallback as ActionMode.Callback2).onGetContentRect(mode, view, outRect)
                        } else {
                            super.onGetContentRect(mode, view, outRect)
                        }
                    }

                    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                        if (item?.itemId == HIGHLIGHT_MENU_ID) {
                            evaluateJavascript("getSelectionInfo()") { info ->
                                // Log the raw response for debugging
                                android.util.Log.d("HtmlRenderer", "Raw selection info: $info")
                                
                                if (info != null && info != "null" && info.isNotBlank()) {
                                    try {
                                        // When evaluateJavascript returns a string, it's often double-quoted and escaped
                                        val cleaned = if (info.startsWith("\"") && info.endsWith("\"")) {
                                            // Handle cases like "\"{\\\"text\\\":...}\""
                                            var inner = info.substring(1, info.length - 1)
                                            inner = inner.replace("\\\"", "\"")
                                            inner = inner.replace("\\\\", "\\")
                                            inner
                                        } else info
                                        
                                        val json = org.json.JSONObject(cleaned)
                                        onCreateHighlight(
                                            json.getString("text"),
                                            json.getInt("start"),
                                            json.getInt("end"),
                                            null,
                                            "yellow"
                                        )
                                    } catch (e: Exception) { 
                                        e.printStackTrace()
                                        android.widget.Toast.makeText(context, "Selection error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    android.widget.Toast.makeText(context, "No text selected", android.widget.Toast.LENGTH_SHORT).show()
                                }
                                // FINISH mode ONLY AFTER we retrieved the info
                                mode?.finish()
                            }
                            return true
                        }
                        return originalCallback.onActionItemClicked(mode, item)
                    }

                    override fun onDestroyActionMode(mode: ActionMode?) {
                        originalCallback.onDestroyActionMode(mode)
                    }
                }
            }.apply {
                webViewReference.value = this
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                // Start invisible to prevent white flash
                setBackgroundColor(Color.TRANSPARENT)
                
                // Security settings
                settings.javaScriptEnabled = true
                addJavascriptInterface(webInterface, "Android")
                
                // Allow file access for local images (cached content)
                settings.allowFileAccess = true
                settings.allowContentAccess = false
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false

                // Enable mixed content to allow loading HTTP images from file:/// context
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                // Set up WebViewClient to intercept link clicks
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString()
                        if (url != null) {
                            // Let the WebView handle local anchor links internally (US4)
                            // Anchor links within the same document have a file:// scheme and a non-null fragment
                            if (request.url.scheme == "file" && request.url.fragment != null) {
                                return false
                            }
                            val handler = onLinkClickState.value
                            if (handler != null) {
                                handler(url)
                                return true // Prevent WebView from loading the URL
                            }
                        }
                        return false
                    }

                    @Deprecated("Deprecated in Java")
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        if (url != null) {
                            // Let the WebView handle local anchor links internally (US4)
                            if (url.startsWith("file://") && url.contains("#")) {
                                return false
                            }
                            val handler = onLinkClickState.value
                            if (handler != null) {
                                handler(url)
                                return true
                            }
                        }
                        return false
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        pageLoaded.value = true
                        // Apply highlights on page load
                        if (highlights.isNotEmpty()) {
                            applyHighlightsToWebView(view, highlights)
                            lastAppliedHighlights.value = highlights
                        }
                        onLoaded?.invoke()
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        Log.d("KarakeptWebView", "WebView Error: ${error?.description} for ${request?.url}")
                    }

                    override fun onReceivedHttpError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        errorResponse: WebResourceResponse?
                    ) {
                        super.onReceivedHttpError(view, request, errorResponse)
                        Log.d("KarakeptWebView", "WebView HTTP Error: ${errorResponse?.statusCode} for ${request?.url}")
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        Log.d("KarakeptWebView", "WebView Console: ${consoleMessage?.message()} -- From line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}")
                        return true
                    }
                }

                // Load the HTML content or local file
                if (localFilePath != null) {
                    loadUrl("file://$localFilePath")
                } else {
                    loadDataWithBaseURL("file:///", themedHtml, "text/html", "UTF-8", null)
                }
            }
        },
        update = { webView ->
            // Keep the link-click handler current so that a linkOpenMode change that
            // triggers recomposition takes effect on the very next tap.
            onLinkClickState.value = onLinkClick

            // Handle HTML content reload - only reload if content actually changed
            if (lastLoadedHtml.value != themedHtml) {
                pageLoaded.value = false
                lastAppliedHighlights.value = emptyList()
                if (localFilePath != null) {
                    if (webView.url != "file://$localFilePath") {
                        webView.loadUrl("file://$localFilePath")
                        lastLoadedHtml.value = themedHtml
                    }
                } else {
                    webView.loadDataWithBaseURL("file:///", themedHtml, "text/html", "UTF-8", null)
                    lastLoadedHtml.value = themedHtml
                }
                return@AndroidView
            }

            // Only apply highlights if page is loaded
            if (!pageLoaded.value) return@AndroidView

            // Robust comparison: check if highlights have actually changed
            val highlightsChanged = if (highlights.size != lastAppliedHighlights.value.size) {
                true
            } else {
                // Create maps for O(1) lookup
                val oldMap = lastAppliedHighlights.value.associateBy { it.id }
                highlights.any { newHighlight ->
                    val oldHighlight = oldMap[newHighlight.id]
                    oldHighlight == null ||
                    oldHighlight.color != newHighlight.color ||
                    oldHighlight.note != newHighlight.note
                }
            }

            if (highlightsChanged) {
                applyHighlightsToWebView(webView, highlights)
                lastAppliedHighlights.value = highlights
            }
        }
    )

    // Highlights are now handled in the 'update' block AND onPageFinished

    DisposableEffect(Unit) {
        onDispose {
            // Cleanup if needed
        }
    }
}
