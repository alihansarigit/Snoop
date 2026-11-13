package io.github.alihansarigit.snoop.diagnostics

import android.os.Process
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** Bounded diagnostic buffer. SDK logs are reliable; Logcat ingestion is best-effort per device. */
internal object DiagnosticLogBuffer {
    private const val RING_CAPACITY = 100
    private const val MANUAL_CAPACITY = 1_000
    private const val RING_WINDOW_MS = 30_000L

    private data class Entry(val timestamp: Long, val line: String)

    private val lock = Any()
    private val ring = ArrayDeque<Entry>(RING_CAPACITY)
    private val manual = ArrayDeque<Entry>(MANUAL_CAPACITY)
    private val logcatStarted = AtomicBoolean(false)
    private var manualEnabled = false
    private var logcatProcess: java.lang.Process? = null

    fun add(level: String, tag: String, message: String) {
        addRaw("${level.take(1).uppercase(Locale.US)}/${tag.take(40)}: ${message.take(2_000)}")
    }

    fun startManualCapture() = synchronized(lock) {
        manual.clear()
        manualEnabled = true
        addRaw("I/Snoop: manual log capture started")
    }

    fun stopManualCapture(): List<String> = synchronized(lock) {
        addRaw("I/Snoop: manual log capture stopped")
        manualEnabled = false
        manual.map(::format)
    }

    fun isManualCaptureActive(): Boolean = synchronized(lock) { manualEnabled }

    fun snapshot(): List<String> = synchronized(lock) {
        trim(System.currentTimeMillis())
        ring.map(::format)
    }

    fun startLogcatBestEffort() {
        if (!logcatStarted.compareAndSet(false, true)) return
        thread(name = "Snoop-Logcat", isDaemon = true) {
            try {
                val process = ProcessBuilder(
                    "logcat",
                    "--pid=${Process.myPid()}",
                    "-v",
                    "threadtime",
                ).redirectErrorStream(true).start()
                logcatProcess = process
                BufferedReader(InputStreamReader(process.inputStream)).useLines { lines ->
                    lines.forEach(::addRaw)
                }
            } catch (error: Throwable) {
                add("W", "Snoop", "Logcat unavailable: ${error.javaClass.simpleName}")
            } finally {
                logcatProcess = null
            }
        }
    }

    fun stopLogcat() {
        logcatProcess?.destroy()
        logcatProcess = null
        logcatStarted.set(false)
    }

    private fun addRaw(line: String) = synchronized(lock) {
        val now = System.currentTimeMillis()
        trim(now)
        while (ring.size >= RING_CAPACITY) ring.removeFirst()
        val entry = Entry(now, line.take(4_000))
        ring.addLast(entry)
        if (manualEnabled) {
            while (manual.size >= MANUAL_CAPACITY) manual.removeFirst()
            manual.addLast(entry)
        }
    }

    private fun trim(now: Long) {
        val cutoff = now - RING_WINDOW_MS
        while (ring.isNotEmpty() && ring.first().timestamp < cutoff) ring.removeFirst()
    }

    private fun format(entry: Entry): String =
        "${SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(entry.timestamp))} ${entry.line}"
}
