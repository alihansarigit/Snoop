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

    fun registerSection(title: String, content: @Composable () -> Unit) = Unit
}
