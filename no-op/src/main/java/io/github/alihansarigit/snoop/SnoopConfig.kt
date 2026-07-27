package io.github.alihansarigit.snoop

/**
 * No-op mirror of the real [SnoopConfig]. Every setter is accepted and ignored so a
 * `releaseImplementation` swap compiles unchanged and does nothing at runtime.
 */
class SnoopConfig internal constructor() {

    var redactionEnabled: Boolean = true

    var redactionMask: String = "[REDACTED]"

    var decodeBodies: Boolean = true

    var persistenceEnabled: Boolean = false

    var maxAgeMillis: Long = 0L

    fun redactHeaders(vararg names: String): SnoopConfig = this

    fun redactBodyKeys(vararg keys: String): SnoopConfig = this

    fun unredactHeader(name: String): SnoopConfig = this
}
