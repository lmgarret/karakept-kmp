package com.karakept.app.utils

object HtmlCache {
    private val cache = mutableMapOf<String, String>()
    private const val MAX_SIZE = 50

    fun get(key: String): String? {
        return cache[key]
    }

    fun put(key: String, value: String) {
        if (cache.size >= MAX_SIZE) {
            // Simple removal of first entry (not true LRU but sufficient for now)
            val iterator = cache.iterator()
            if (iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }
        cache[key] = value
    }
    
    fun generateKey(html: String, mode: String): String {
        // Use hash code to save memory on keys, or just a prefix of html if unique enough
        // For correctness, we should use the full content or a strong hash.
        // Let's use hashCode for now as a simple key.
        return "${mode}_${html.hashCode()}"
    }
}
