package io.github.alihansarigit.snoop.okhttp

import okhttp3.Interceptor
import okhttp3.Response

/** No-op interceptor for release builds: passes the request straight through. */
class SnoopInterceptor @JvmOverloads constructor(
    @Suppress("UNUSED_PARAMETER") maxBodyBytes: Long = 250_000L,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())
}
