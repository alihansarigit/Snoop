package io.github.alihansarigit.snoop.network

import androidx.annotation.RestrictTo
import java.util.LinkedHashMap

/** Bounded registry of adapter-owned replay closures. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
object NetworkReplayRegistry {
    private const val CAPACITY = 200
    private val lock = Any()
    private val handlers = LinkedHashMap<Long, () -> Unit>(CAPACITY, 0.75f, true)

    fun register(transactionId: Long, handler: () -> Unit) = synchronized(lock) {
        handlers[transactionId] = handler
        while (handlers.size > CAPACITY) {
            handlers.remove(handlers.entries.first().key)
        }
    }

    fun replay(transactionId: Long): Boolean {
        val handler = synchronized(lock) { handlers[transactionId] } ?: return false
        return runCatching {
            handler()
            true
        }.getOrDefault(false)
    }

    fun clear() = synchronized(lock) { handlers.clear() }
}
