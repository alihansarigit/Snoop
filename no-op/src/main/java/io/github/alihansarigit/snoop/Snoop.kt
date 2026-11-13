package io.github.alihansarigit.snoop

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Composable

/**
 * No-op replacement of the real [Snoop]. Mirrors the public API exactly so a
 * `releaseImplementation` swap compiles unchanged and does nothing at runtime.
 */
object Snoop {

    @JvmField
    val config: SnoopConfig = SnoopConfig()

    var enabled: Boolean = false

    var shakeToShow: Boolean = true

    @JvmStatic
    fun install(application: Application) = Unit

    @JvmStatic
    fun show() = Unit

    @JvmStatic
    fun hide() = Unit

    @JvmStatic
    fun clear() = Unit

    @JvmStatic
    fun launchInspector(context: Context) = Unit

    @JvmStatic
    fun launchCapture(context: Context) = Unit

    @JvmStatic
    @JvmOverloads
    fun setFeedbackScreenContext(
        routeName: String,
        subScreen: String = "",
        screenFileHint: String = "",
    ) = Unit

    @JvmStatic
    fun clearFeedbackScreenContext() = Unit

    @JvmStatic
    fun startSession(name: String): String = ""

    @JvmStatic
    fun resumeSession(sessionId: String): Boolean = false

    @JvmStatic
    fun closeSession(): String? = null

    @JvmStatic
    fun exportSession(sessionId: String): String? = null

    @JvmStatic
    fun deleteSession(sessionId: String): Boolean = false

    @JvmStatic
    @JvmOverloads
    fun recordAction(
        tag: String,
        action: String = "action",
        metadata: Map<String, String> = emptyMap(),
    ) = Unit

    @JvmStatic
    @JvmOverloads
    fun log(tag: String, message: String, level: String = "I") = Unit

    @JvmStatic
    fun startLogCapture() = Unit

    @JvmStatic
    fun stopLogCapture(): List<String> = emptyList()

    @JvmStatic
    @JvmOverloads
    fun addNetworkMock(
        method: String,
        urlPattern: String,
        statusCode: Int,
        body: String,
        headers: Map<String, String> = emptyMap(),
    ): String = ""

    @JvmStatic
    fun removeNetworkMock(id: String): Boolean = false

    @JvmStatic
    fun clearNetworkMocks() = Unit

    @JvmStatic
    fun addFeedbackCategory(label: String, colorArgb: Int): String = ""

    @JvmStatic
    fun replay(transactionId: Long): Boolean = false

    @JvmStatic
    fun activeSessionId(): String? = null

    fun registerSection(title: String, content: @Composable () -> Unit) = Unit

    @JvmStatic
    fun registerDeveloperAction(title: String, action: Runnable) = Unit

    @JvmStatic
    fun restartApp(context: Context) = Unit
}
