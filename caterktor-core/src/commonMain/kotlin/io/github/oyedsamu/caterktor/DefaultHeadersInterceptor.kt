package io.github.oyedsamu.caterktor

/**
 * DSL builder for configuring default headers on a [CaterKtor] client.
 *
 * Use via [CaterKtorBuilder.defaultHeaders].
 */
@ExperimentalCaterktor
@CaterKtorDsl
public class DefaultHeadersBuilder {
    internal val entries: MutableList<Pair<String, suspend () -> String>> = mutableListOf()

    /** Add a static header. Does not override a per-request header with the same name. */
    public fun set(name: String, value: String): Unit {
        entries += name to { value }
    }

    /** Add a dynamic header evaluated per-request. Does not override a per-request header. */
    public fun set(name: String, provider: suspend () -> String): Unit {
        entries += name to provider
    }
}

/**
 * Interceptor that adds default request headers to every outgoing request.
 *
 * Static values are stored directly; dynamic providers are called per-request via the suspend
 * lambda.
 *
 * A header is **only added if the outgoing request does not already contain that header**
 * (case-insensitive). Per-request headers always win.
 *
 * Registered automatically by [CaterKtorBuilder] when [CaterKtorBuilder.defaultHeader] or
 * [CaterKtorBuilder.defaultHeaders] is called; auto-installed at position 0 in the pipeline
 * (runs before auth and retry).
 *
 * Can also be constructed and registered manually for full pipeline position control.
 */
@ExperimentalCaterktor
public class DefaultHeadersInterceptor(
    /** Ordered list of (header-name, value-provider) pairs. */
    public val entries: List<Pair<String, suspend () -> String>>,
) : Interceptor {

    override suspend fun intercept(chain: Chain): NetworkResponse {
        val request = chain.request
        var merged = request.headers
        for ((name, provider) in entries) {
            if (name !in merged) {                   // only add if not already present
                val value = provider()
                merged = merged + Headers { set(name, value) }
            }
        }
        return chain.proceed(
            if (merged === request.headers) request   // fast path: nothing added
            else request.copy(headers = merged),
        )
    }
}
