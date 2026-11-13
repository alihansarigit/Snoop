package io.github.alihansarigit.snoop.feedback

import java.util.ArrayDeque

/** Process-wide, bounded action history used by normal feedback and crash reports. */
internal object BreadcrumbRecorder {
    private const val CAPACITY = 20
    private val lock = Any()
    private val entries = ArrayDeque<Breadcrumb>(CAPACITY)

    fun record(
        type: String,
        label: String,
        x: Float? = null,
        y: Float? = null,
        metadata: Map<String, String> = emptyMap(),
    ) {
        val safeMetadata = metadata
            .filterKeys { it.length <= 80 }
            .mapValues { (_, value) -> value.take(250) }
        synchronized(lock) {
            while (entries.size >= CAPACITY) entries.removeFirst()
            entries.addLast(
                Breadcrumb(
                    timestamp = System.currentTimeMillis(),
                    type = type.take(40),
                    label = label.take(250),
                    x = x,
                    y = y,
                    metadata = safeMetadata,
                ),
            )
        }
    }

    fun snapshot(limit: Int = 10): List<Breadcrumb> = synchronized(lock) {
        entries.toList().takeLast(limit.coerceIn(1, CAPACITY))
    }

    fun clear() = synchronized(lock) { entries.clear() }
}
