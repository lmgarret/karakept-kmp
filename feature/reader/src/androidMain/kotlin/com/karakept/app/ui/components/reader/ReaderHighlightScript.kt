package com.karakept.app.ui.components.reader

// Injected into both WebView documents (Reader-in-WebView and Web archive). Talks to the page
// through the `Android` bridge registered in HtmlRenderer.android.kt.
internal val READER_HIGHLIGHT_SCRIPT: String =
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

            // Measure both start and end offsets using a walker to be consistent with getRangesFromOffsets.
            // Walking to each container independently is more accurate than using start + text.length,
            // which can drift when the selection spans nodes with different whitespace handling.
            const root = document.getElementById('karakept-content') || document.body;
            const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null, false);
            let start = -1;
            let end = -1;
            let currentOffset = 0;
            let node;
            while ((node = walker.nextNode())) {
                const nodeLength = node.textContent.length;
                if (start === -1 && node === range.startContainer) {
                    start = currentOffset + range.startOffset;
                }
                if (end === -1 && node === range.endContainer) {
                    end = currentOffset + range.endOffset;
                    break;
                }
                currentOffset += nodeLength;
            }

            // Fallback: if containers were not found (e.g. selection spans outside root), use text length
            if (start === -1) start = 0;
            if (end === -1) end = start + text.length;

            return JSON.stringify({
                text: text,
                start: start,
                end: end
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

    function blinkHighlight(highlightId) {
        try {
            const marks = document.querySelectorAll('mark.karakept-highlight[data-id="' + highlightId + '"]');
            if (marks.length === 0) {
                log("blinkHighlight: no marks found for id=" + highlightId);
                return;
            }
            marks.forEach(mark => {
                mark.classList.add('karakept-highlight-blink');
                setTimeout(() => mark.classList.remove('karakept-highlight-blink'), 600);
            });
            log("blinkHighlight: blink triggered for id=" + highlightId);
        } catch(e) { log("blinkHighlight error: " + e.message); }
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
