package io.github.alihansarigit.snoop.internal

import android.app.Application
import android.content.Context
import androidx.startup.Initializer
import io.github.alihansarigit.snoop.Snoop

/** Auto-installs Snoop at app startup via androidx.startup. */
class SnoopInitializer : Initializer<Snoop> {
    override fun create(context: Context): Snoop {
        (context.applicationContext as? Application)?.let { Snoop.install(it) }
        return Snoop
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
