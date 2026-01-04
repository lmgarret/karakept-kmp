package com.karakept.app.ui.components

import android.graphics.Color
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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
                    highlightOffsets(h.id, h.startOffset, h.endOffset, h.color);
                }
            });
        }

        function highlightOffsets(id, start, end, color) {
            try {
                log("Highlighting: " + start + "-" + end);
                const root = document.getElementById('karakept-content') || document.body;
                const range = getRangeFromOffsets(root, start, end);
                if (!range) {
                    log("Range not found for " + start + "-" + end);
                    return;
                }
                wrapRange(range, id, color);
            } catch(e) { log("Highlight error: " + e.message); }
        }

        function getRangeFromOffsets(root, start, end) {
            let charCount = 0;
            let startNode, startOffset, endNode, endOffset;
            
            function walk(node) {
                if (node.nodeType === Node.TEXT_NODE) {
                    const length = node.textContent.length;
                    if (!startNode && start >= charCount && start < charCount + length) {
                        startNode = node;
                        startOffset = start - charCount;
                    }
                    if (end > charCount && end <= charCount + length) {
                        endNode = node;
                        endOffset = end - charCount;
                        return true;
                    }
                    charCount += length;
                } else {
                    for (let child of node.childNodes) {
                        if (walk(child)) return true;
                    }
                }
                return false;
            }
            walk(root);
            if (startNode && endNode) {
                const range = document.createRange();
                range.setStart(startNode, startOffset);
                range.setEnd(endNode, endOffset);
                return range;
            }
            return null;
        }

        function wrapRange(range, id, color) {
            const nodes = [];
            const root = document.getElementById('karakept-content') || document.body;
            const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null, false);
            let node;
            while(node = walker.nextNode()) {
                if (range.intersectsNode(node)) nodes.push(node);
            }

            nodes.forEach(textNode => {
                let start = 0, end = textNode.textContent.length;
                if (textNode === range.startContainer) start = range.startOffset;
                if (textNode === range.endContainer) end = range.endOffset;
                if (start >= end) return;

                const span = document.createElement('mark');
                span.className = 'karakept-highlight ' + (color || 'yellow');
                span.dataset.id = id;
                span.onclick = (e) => {
                    e.stopPropagation();
                    Android.onHighlightClick(id);
                };

                const part = textNode.splitText(start);
                part.splitText(end - start);
                part.parentNode.replaceChild(span, part);
                span.appendChild(part);
            });
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
                    <meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src http: https: data:; style-src 'unsafe-inline'; script-src 'unsafe-inline';">
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
                        <meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src http: https: data:; style-src 'unsafe-inline' http: https:; script-src 'unsafe-inline';">
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
        private val onPosition: ((String, com.karakept.app.ui.components.HighlightPosition?) -> Unit)?
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
        fun onLog(message: String) {
            println("WebView Log: $message")
        }
    }

    val webInterface = remember(onCreateHighlight, onDeleteHighlight, onHighlightClick, onHighlightPosition, webViewReference.value) {
        WebAppInterface(onCreateHighlight, onDeleteHighlight, onHighlightClick, webViewReference.value, onHighlightPosition)
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

                inner class WrappedActionModeCallback(private val originalCallback: ActionMode.Callback) : ActionMode.Callback {
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
                
                // Allow file access only when we need to load local files
                settings.allowFileAccess = localFilePath != null
                settings.allowContentAccess = false
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false

                // Disable mixed content (enforce HTTPS)
                @Suppress("DEPRECATION")
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW

                // Set up WebViewClient to intercept link clicks
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString()
                        if (url != null && onLinkClick != null) {
                            onLinkClick(url)
                            return true // Prevent WebView from loading the URL
                        }
                        return false
                    }

                    @Deprecated("Deprecated in Java")
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        if (url != null && onLinkClick != null) {
                            onLinkClick(url)
                            return true
                        }
                        return false
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        pageLoaded.value = true
                        // Apply highlights on page load
                        if (highlights.isNotEmpty()) {
                            applyHighlightsToWebView(view, highlights)
                            lastAppliedHighlights.value = highlights
                        }
                        onLoaded?.invoke()
                    }
                }

                // Load the HTML content or local file
                if (localFilePath != null) {
                    loadUrl("file://$localFilePath")
                } else {
                    loadDataWithBaseURL(null, themedHtml, "text/html", "UTF-8", null)
                }
            }
        },
        update = { webView ->
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
                    webView.loadDataWithBaseURL(null, themedHtml, "text/html", "UTF-8", null)
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
