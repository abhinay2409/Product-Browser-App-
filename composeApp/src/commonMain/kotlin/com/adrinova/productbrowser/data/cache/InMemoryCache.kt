package com.adrinova.productbrowser.data.cache

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Created by Abhinay on 21/07/26.
 */
/**
 * Small coroutine-safe TTL cache used for local caching of API responses.
 * Kept intentionally simple (bonus requirement): entries expire after
 * [ttl] and the whole cache lives only for the process lifetime.
 */
class InMemoryCache<K : Any, V : Any>(
    private val ttl: Duration = 5.minutes,
    private val timeSource: TimeSource = TimeSource.Monotonic
) {
    private class Entry<V>(val value: V, val createdAt: TimeMark)

    private val mutex = Mutex()
    private val entries = mutableMapOf<K, Entry<V>>()

    suspend fun get(key: K): V? = mutex.withLock {
        val entry = entries[key] ?: return@withLock null
        if (entry.createdAt.elapsedNow() > ttl) {
            entries.remove(key)
            null
        } else {
            entry.value
        }
    }

    suspend fun put(key: K, value: V) = mutex.withLock {
        entries[key] = Entry(value, timeSource.markNow())
    }

    suspend fun clear() = mutex.withLock { entries.clear() }
}