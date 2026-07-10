package io.github.alihansarigit.snoop.store

import io.github.alihansarigit.snoop.model.NetworkTransaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory, thread-safe ring buffer of captured transactions. Newest first.
 * A single instance is owned by [io.github.alihansarigit.snoop.Snoop].
 */
class NetworkLogStore internal constructor(
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    private val idGenerator = AtomicLong(0L)
    private val lock = Any()

    private val _transactions = MutableStateFlow<List<NetworkTransaction>>(emptyList())
    val transactions: StateFlow<List<NetworkTransaction>> = _transactions.asStateFlow()

    /**
     * Optional transform applied to every transaction just before it is stored — wired by
     * [io.github.alihansarigit.snoop.Snoop] to [io.github.alihansarigit.snoop.SnoopConfig.redact].
     * Must be idempotent: an updated transaction is passed through again on each [update].
     */
    internal var redactor: ((NetworkTransaction) -> NetworkTransaction)? = null

    /** Reserve a monotonically increasing id for a new transaction. */
    fun nextId(): Long = idGenerator.incrementAndGet()

    /** Insert (newest first) or replace a transaction with the same id. */
    fun put(transaction: NetworkTransaction) {
        val entry = redactor?.invoke(transaction) ?: transaction
        synchronized(lock) {
            val current = _transactions.value.toMutableList()
            val index = current.indexOfFirst { it.id == entry.id }
            if (index >= 0) {
                current[index] = entry
            } else {
                current.add(0, entry)
                while (current.size > capacity) current.removeAt(current.lastIndex)
            }
            _transactions.value = current
        }
    }

    /** Apply [transform] to the transaction with [id], if present. */
    fun update(id: Long, transform: (NetworkTransaction) -> NetworkTransaction) {
        synchronized(lock) {
            val current = _transactions.value
            val index = current.indexOfFirst { it.id == id }
            if (index < 0) return
            val updated = current.toMutableList()
            val transformed = transform(updated[index])
            updated[index] = redactor?.invoke(transformed) ?: transformed
            _transactions.value = updated
        }
    }

    /**
     * Seed the buffer from persisted [loaded] transactions (newest first) and advance the
     * id sequence past them so newly captured ids can't collide. Trimmed to capacity. A
     * no-op for an empty list, so it never clobbers live captures with nothing.
     */
    fun restore(loaded: List<NetworkTransaction>) {
        if (loaded.isEmpty()) return
        synchronized(lock) {
            _transactions.value = if (loaded.size > capacity) loaded.take(capacity) else loaded
            val maxId = loaded.maxOf { it.id }
            if (maxId > idGenerator.get()) idGenerator.set(maxId)
        }
    }

    fun clear() {
        synchronized(lock) { _transactions.value = emptyList() }
    }

    private companion object {
        const val DEFAULT_CAPACITY = 200
    }
}
