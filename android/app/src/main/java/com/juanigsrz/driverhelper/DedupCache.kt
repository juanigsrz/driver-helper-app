package com.juanigsrz.driverhelper

class DedupCache(private val ttlMs: Long = 30_000L) {
    private val seen = HashMap<String, Long>()

    @Synchronized
    fun isNew(key: String): Boolean {
        val now = System.currentTimeMillis()
        val it = seen.entries.iterator()
        while (it.hasNext()) {
            if (now - it.next().value > ttlMs) it.remove()
        }
        if (key in seen) return false
        seen[key] = now
        return true
    }
}
