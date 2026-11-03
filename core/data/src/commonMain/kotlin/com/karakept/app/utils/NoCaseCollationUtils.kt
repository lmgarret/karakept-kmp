package com.karakept.app.utils

/**
 * SQLite's `NOCASE` collation, in Kotlin.
 *
 * The paged query orders titles with `title COLLATE NOCASE` and the loaded window is re-sorted in
 * memory by the same key, so the two have to mean the same thing by "before". They did not.
 * `NOCASE` folds the 26 ASCII letters and nothing else, then compares what remains as UTF-8 bytes
 * — which is code-point order. `String.lowercase()` folds the whole of Unicode, and
 * `String.compareTo` compares UTF-16 code units. Both differences are reachable from a bookmark
 * title: `Ωmega` and `ωmega` are one key to Kotlin and two to SQLite, and a title opening with an
 * emoji sorts before every CJK title in UTF-16 order and after them in code-point order.
 *
 * A page appended to the window is re-sorted against the rows already in it, so a comparator that
 * disagrees with the query interleaves the new page in an order no page boundary matches. Sorting
 * a library by title was enough to see it.
 */
object NoCaseCollationUtils {

    /** Orders [a] against [b] the way SQLite's `COLLATE NOCASE` does. */
    fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = codePointAt(a, i)
            val cb = codePointAt(b, j)
            val fa = foldAscii(ca)
            val fb = foldAscii(cb)
            if (fa != fb) return if (fa < fb) -1 else 1
            i += charCount(ca)
            j += charCount(cb)
        }
        // Whichever ran out first is a prefix of the other, and a prefix sorts first.
        return (a.length - i).compareTo(b.length - j)
    }

    /** [compare] as a `Comparator`, for `compareBy` / `compareByDescending`. */
    val ascending: Comparator<String> = Comparator { a, b -> compare(a, b) }

    private const val CASE_SHIFT = 'a'.code - 'A'.code
    private const val SUPPLEMENTARY_BASE = 0x10000
    private const val MIN_HIGH_SURROGATE = 0xD800
    private const val MIN_LOW_SURROGATE = 0xDC00

    private fun foldAscii(codePoint: Int): Int =
        if (codePoint in 'A'.code..'Z'.code) codePoint + CASE_SHIFT else codePoint

    private fun codePointAt(value: String, index: Int): Int {
        val high = value[index]
        if (high.isHighSurrogate() && index + 1 < value.length) {
            val low = value[index + 1]
            if (low.isLowSurrogate()) {
                return SUPPLEMENTARY_BASE +
                    ((high.code - MIN_HIGH_SURROGATE) shl 10) +
                    (low.code - MIN_LOW_SURROGATE)
            }
        }
        return high.code
    }

    private fun charCount(codePoint: Int): Int =
        if (codePoint >= SUPPLEMENTARY_BASE) 2 else 1
}
