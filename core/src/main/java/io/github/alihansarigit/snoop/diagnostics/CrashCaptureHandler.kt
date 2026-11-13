package io.github.alihansarigit.snoop.diagnostics

import android.content.Context
import android.os.Build
import io.github.alihansarigit.snoop.feedback.BreadcrumbRecorder
import io.github.alihansarigit.snoop.feedback.PendingCrash
import io.github.alihansarigit.snoop.feedback.SessionStore
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Persists a minimal crash envelope and always delegates to Android/the host handler. Swallowing
 * fatal exceptions would leave application state corrupt, so recovery happens on the next launch.
 */
internal class CrashCaptureHandler(
    private val appContext: Context,
    private val store: SessionStore,
    private val delegate: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {
    private val handling = AtomicBoolean(false)

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        if (handling.compareAndSet(false, true)) {
            runCatching {
                store.writePendingCrash(
                    PendingCrash(
                        timestamp = System.currentTimeMillis(),
                        threadName = thread.name,
                        stackTrace = throwable.stackTraceString(),
                        deviceInfo = deviceInfo(appContext),
                        breadcrumbs = BreadcrumbRecorder.snapshot(),
                        logs = DiagnosticLogBuffer.snapshot(),
                    ),
                    screenshotPng = ScreenshotCapture.lastGoodPng(),
                )
            }
        }
        if (delegate != null && delegate !== this) {
            delegate.uncaughtException(thread, throwable)
        } else {
            android.os.Process.killProcess(android.os.Process.myPid())
            kotlin.system.exitProcess(10)
        }
    }

    companion object {
        fun deviceInfo(context: Context): Map<String, String> {
            val packageInfo = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0)
            }.getOrNull()
            return linkedMapOf(
                "package" to context.packageName,
                "versionName" to (packageInfo?.versionName ?: "unknown"),
                "versionCode" to (
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        packageInfo?.longVersionCode?.toString()
                    } else {
                        @Suppress("DEPRECATION")
                        packageInfo?.versionCode?.toString()
                    } ?: "unknown"
                ),
                "manufacturer" to Build.MANUFACTURER,
                "model" to Build.MODEL,
                "android" to Build.VERSION.RELEASE,
                "sdk" to Build.VERSION.SDK_INT.toString(),
                "abi" to Build.SUPPORTED_ABIS.joinToString(),
            )
        }
    }
}

private fun Throwable.stackTraceString(): String =
    StringWriter().also { printStackTrace(PrintWriter(it)) }.toString()
