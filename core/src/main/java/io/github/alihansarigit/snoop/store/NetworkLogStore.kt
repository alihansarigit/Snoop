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

    /** Reserve a monotonically increasing id for a new transaction. */
    fun nextId(): Long = idGenerator.incrementAndGet()

    /** Insert (newest first) or replace a transaction with the same id. */
    fun put(transaction: NetworkTransaction) {
        synchronized(lock) {
            val current = _transactions.value.toMutableList()
            val index = current.indexOfFirst { it.id == transaction.id }
            if (index >= 0) {
                current[index] = transaction
            } else {
                current.add(0, transaction)
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
            updated[index] = transform(updated[index])
            _transactions.value = updated
        }
    }

    fun clear() {
        synchronized(lock) { _transactions.value = emptyList() }
    }

    private companion object {
        const val DEFAULT_CAPACITY = 200
    }
}
