package io.github.alihansarigit.snoop.store

import io.github.alihansarigit.snoop.SnoopConfig
import io.github.alihansarigit.snoop.model.NetworkTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File

/**
 * Persists captured transactions to a JSON [file] and restores them on startup, applying
 * the age-based retention window from [SnoopConfig.maxAgeMillis].
 *
 * Writes are throttled to at most one per [WRITE_INTERVAL_MS] on a background IO scope:
 * the store's [StateFlow] conflates bursts while a write is cooling down, so heavy traffic
 * doesn't hammer the disk. All I/O failures are swallowed — persistence is best-effort and
 * never affects capture.
 */
internal class DiskPersistence(
    private val file: File,
    private val config: SnoopConfig,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Read persisted transactions, dropping any outside the retention window. Newest first. */
    fun load(): List<NetworkTransaction> {
        if (!file.exists()) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            val cutoff = cutoff()
            buildList {
                for (i in 0 until array.length()) {
                    val tx = array.optJSONObject(i)?.toTransaction() ?: continue
                    if (withinRetention(tx, cutoff)) add(tx)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Begin mirroring [transactions] to disk. The current (just-restored) value is skipped,
     * then each change is written — immediately for the first, then no more than once per
     * [WRITE_INTERVAL_MS].
     */
    fun observe(transactions: StateFlow<List<NetworkTransaction>>) {
        scope.launch {
            transactions.drop(1).collect { list ->
                write(list)
                delay(WRITE_INTERVAL_MS)
            }
        }
    }

    fun close() {
        scope.cancel()
    }

    private fun write(list: List<NetworkTransaction>) {
        try {
            val cutoff = cutoff()
            val array = JSONArray()
            list.forEach { if (withinRetention(it, cutoff)) array.put(it.toJson()) }
            file.parentFile?.mkdirs()
            file.writeText(array.toString())
        } catch (_: Exception) {
            // best-effort; a failed write must never surface to the app
        }
    }

    /** Epoch cutoff below which transactions are stale, or 0 when no age limit is configured. */
    private fun cutoff(): Long {
        val maxAge = config.maxAgeMillis
        return if (maxAge > 0L) now() - maxAge else 0L
    }

    private fun withinRetention(tx: NetworkTransaction, cutoff: Long): Boolean =
        cutoff <= 0L || tx.requestDate >= cutoff

    private companion object {
        const val WRITE_INTERVAL_MS = 1_000L
    }
}
