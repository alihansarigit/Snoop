package io.github.alihansarigit.snoop.daemon

import java.io.File
import kotlin.concurrent.thread

fun main(args: Array<String>) {
    if (args.firstOrNull() != "bridge") {
        SnoopMcpServer().run()
        return
    }

    val options = parseBridgeOptions(args.drop(1))
    val projectDirectory = options["project-dir"]
        ?: System.getenv("SNOOP_PROJECT_DIR")
        ?: error("bridge requires --project-dir <path> or SNOOP_PROJECT_DIR")
    val packageName = options["package"] ?: System.getenv("SNOOP_PACKAGE")
    val port = (
        options["port"]
            ?: System.getenv("SNOOP_BRIDGE_PORT")
            ?: DEFAULT_BRIDGE_PORT.toString()
        ).toInt().also {
        require(it in 1..65535) { "Bridge port must be between 1 and 65535" }
    }

    val idleTimeoutMinutes = (
        options["idle-timeout"]
            ?: System.getenv("SNOOP_BRIDGE_IDLE_TIMEOUT")
            ?: DEFAULT_IDLE_TIMEOUT_MINUTES.toString()
        ).toLong().also {
        require(it >= 0) { "Idle timeout cannot be negative" }
    }

    val adb = AdbSessionBridge()
    DesktopBridgeServer(File(projectDirectory), packageName, port).use { server ->
        val monitor = thread(name = "snoop-adb-reverse", isDaemon = true) {
            var previousDevices = emptyList<String>()
            while (!Thread.currentThread().isInterrupted) {
                val devices = runCatching { adb.connectedDevices() }
                    .onFailure { System.err.println("Snoop bridge: ${it.message}") }
                    .getOrDefault(emptyList())
                if (devices != previousDevices) {
                    runCatching { adb.connectReverse(server.boundPort, devices) }
                        .onFailure { System.err.println("Snoop bridge: ${it.message}") }
                    System.err.println(
                        if (devices.isEmpty()) {
                            "Snoop bridge: waiting for an authorized ADB device"
                        } else {
                            "Snoop bridge: connected ${devices.joinToString()}"
                        },
                    )
                    previousDevices = devices
                }
                try {
                    Thread.sleep(ADB_REFRESH_MILLIS)
                } catch (_: InterruptedException) {
                    return@thread
                }
            }
        }
        val watchdog = idleTimeoutMinutes.takeIf { it > 0 }?.let { minutes ->
            val timeoutMillis = minutes * 60_000L
            thread(name = "snoop-idle-watchdog", isDaemon = true) {
                while (!Thread.currentThread().isInterrupted) {
                    try {
                        Thread.sleep(IDLE_CHECK_MILLIS)
                    } catch (_: InterruptedException) {
                        return@thread
                    }
                    if (System.currentTimeMillis() - server.lastActivityAt >= timeoutMillis) {
                        System.err.println(
                            "Snoop bridge: idle for $minutes minutes, shutting down",
                        )
                        server.close()
                        return@thread
                    }
                }
            }
        }
        Runtime.getRuntime().addShutdownHook(thread(start = false) {
            monitor.interrupt()
            watchdog?.interrupt()
            server.close()
        })
        System.err.println(
            "Snoop bridge: ${File(projectDirectory).canonicalPath} " +
                "(127.0.0.1:${server.boundPort})",
        )
        server.serve()
    }
}

private fun parseBridgeOptions(arguments: List<String>): Map<String, String> {
    val result = linkedMapOf<String, String>()
    var index = 0
    while (index < arguments.size) {
        val key = arguments[index]
        require(key in setOf("--project-dir", "--package", "--port", "--idle-timeout")) {
            "Unknown bridge option: $key"
        }
        require(index + 1 < arguments.size) { "Missing value for $key" }
        result[key.removePrefix("--")] = arguments[index + 1]
        index += 2
    }
    return result
}

private const val DEFAULT_BRIDGE_PORT = 8737
private const val ADB_REFRESH_MILLIS = 3_000L

/** A bridge started automatically by a build should not survive the working day. */
private const val DEFAULT_IDLE_TIMEOUT_MINUTES = 480L
private const val IDLE_CHECK_MILLIS = 60_000L
