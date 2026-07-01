package io.github.alihansarigit.snoop.ktor

import io.ktor.client.plugins.api.createClientPlugin

/** No-op mirror of the real [SnoopKtorConfig], so `install(SnoopKtor) { … }` compiles. */
class SnoopKtorConfig {
    @Suppress("unused")
    var maxBodyBytes: Long = 250_000L
}

/**
 * No-op Ktor plugin for release builds: it installs but captures nothing, so a
 * `releaseImplementation` swap of `snoop-ktor` → `snoop-no-op` compiles unchanged
 * and adds zero overhead. Requests pass straight through.
 */
val SnoopKtor = createClientPlugin("SnoopKtor", ::SnoopKtorConfig) {
    // No handlers registered — the request pipeline is untouched.
}
