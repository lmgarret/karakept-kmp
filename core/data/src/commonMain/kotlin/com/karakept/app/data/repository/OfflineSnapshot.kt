package com.karakept.app.data.repository

import com.karakept.app.data.local.projection.OfflineHolderRow
import com.karakept.app.utils.LocalFileInfo

/**
 * Every offline copy at one moment: which bookmarks hold one, what each body takes, and which
 * cache files each copy references. The storage cap and the "can be freed" estimate both decide
 * from it, so the estimate is the cleanup it describes rather than a second guess at it.
 */
internal class OfflineSnapshot(
    val holders: List<OfflineHolderRow>,
    private val bodyBytes: Map<Long, Long>,
    private val refsOf: Map<Long, Set<String>>,
    private val managedFiles: List<LocalFileInfo>
) {
    private val sizes = managedFiles.associate { it.name to it.sizeBytes }

    fun simulation() = EvictionSimulation()

    /** Managed files no copy references, past the sweep's grace period. */
    fun unusedFiles(now: Long, graceMillis: Long): List<LocalFileInfo> {
        val referenced = refsOf.values.flatten().toSet()
        return managedFiles.filter { it.name !in referenced && now - it.lastModifiedMillis >= graceMillis }
    }

    /**
     * Evicts holders in [STORAGE_CAP_ORDER] until [simulation] is at or under [capBytes], and
     * returns them. Holders the simulation already evicted are skipped.
     */
    fun selectForCap(simulation: EvictionSimulation, capBytes: Long): List<OfflineHolderRow> {
        val selected = mutableListOf<OfflineHolderRow>()
        for (holder in holders.sortedWith(STORAGE_CAP_ORDER)) {
            if (simulation.usage <= capBytes) break
            if (simulation.evict(holder) != null) selected += holder
        }
        return selected
    }

    /**
     * Usage as evicting copies one by one changes it. A content image shared by two bookmarks is
     * freed by the second eviction, not the first, so each file carries a count of its referrers.
     */
    inner class EvictionSimulation {
        private val referrers = mutableMapOf<String, Int>().apply {
            refsOf.values.forEach { names -> names.forEach { this[it] = (this[it] ?: 0) + 1 } }
        }
        private val evicted = mutableSetOf<Long>()

        /** Every stored body plus every referenced file, each once. */
        var usage: Long = bodyBytes.values.sum() + referrers.keys.sumOf { sizes[it] ?: 0L }
            private set

        /** Bytes the eviction frees, or null when [holder] was already evicted. */
        fun evict(holder: OfflineHolderRow): Long? {
            if (!evicted.add(holder.localId)) return null
            var freed = bodyBytes[holder.localId] ?: 0L
            for (name in refsOf[holder.localId].orEmpty()) {
                val left = referrers.getValue(name) - 1
                referrers[name] = left
                if (left == 0) freed += sizes[name] ?: 0L
            }
            usage -= freed
            return freed
        }
    }

    companion object {
        // Read or archived before unread; within each, the copy opened longest ago first. One
        // never opened counts from when it was saved.
        val STORAGE_CAP_ORDER = compareBy<OfflineHolderRow>(
            { if (it.isRead || it.isArchived) 0 else 1 },
            { it.lastOpenedAt ?: it.createdAt }
        )
    }
}

/** UTF-8 byte length without encoding the string: what SQLite's `LENGTH(CAST(x AS BLOB))` counts. */
internal fun utf8Length(text: String): Long {
    var bytes = 0L
    var i = 0
    while (i < text.length) {
        val c = text[i]
        bytes += when {
            c.code < 0x80 -> 1
            c.code < 0x800 -> 2
            c.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate() -> { i++; 4 }
            else -> 3
        }
        i++
    }
    return bytes
}
