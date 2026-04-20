package io.github.oyedsamu.caterktor

/**
 * Build a [NetworkClient] via DSL.
 *
 * This is the primary construction entry point for CaterKtor. The block
 * configures the pipeline; the function returns a ready-to-use client.
 *
 * ## Example
 *
 * ```kotlin
 * val client = CaterKtor {
 *     transport = KtorTransport(/* ... */)
 *     addInterceptor(AuthInterceptor(tokenStore))
 *     addInterceptor(RetryInterceptor(policy))
 *     addInterceptor(LoggerInterceptor(level = Basic))
 * }
 * ```
 *
 * @throws IllegalStateException if no [Transport] was configured in [block].
 */
@ExperimentalCaterktor
public fun CaterKtor(block: CaterKtorBuilder.() -> Unit): NetworkClient {
    return CaterKtorBuilder().apply(block).build()
}

/**
 * Marker annotation for the [CaterKtor] builder DSL. Prevents implicit receiver
 * confusion when nested DSLs are composed in later waves (e.g. `auth { }`,
 * `retry { }`, `redaction { }` blocks).
 */
@ExperimentalCaterktor
@DslMarker
public annotation class CaterKtorDsl

/**
 * Mutable builder for a [NetworkClient].
 *
 * Prefer the [CaterKtor] function over direct use of this class. The builder is
 * public so that extension functions — including those that ship in sibling
 * CaterKtor modules — can install their own interceptors cleanly.
 *
 * Ordering: interceptors run in the order they are registered. The first
 * registered interceptor sees the request first and sees the response last.
 */
@ExperimentalCaterktor
@CaterKtorDsl
public class CaterKtorBuilder internal constructor() {

    private val _interceptors: MutableList<Interceptor> = mutableListOf()

    /**
     * The terminal transport. Must be set before [build] is invoked, either
     * directly or via the `install(engine)` helpers shipped in the
     * `caterktor-engine-*` modules.
     */
    public var transport: Transport? = null

    /**
     * Append [interceptor] to the pipeline. Returns this builder for chaining.
     */
    public fun addInterceptor(interceptor: Interceptor): CaterKtorBuilder = apply {
        _interceptors += interceptor
    }

    /** Snapshot of currently registered interceptors, in pipeline order. */
    public val interceptors: List<Interceptor>
        get() = _interceptors.toList()

    internal fun build(): NetworkClient {
        val t = checkNotNull(transport) {
            "CaterKtor: `transport` must be configured before build. " +
                "Provide one directly or install a caterktor-engine-* module."
        }
        return NetworkClient(
            transport = t,
            interceptors = _interceptors.toList(),
        )
    }
}
