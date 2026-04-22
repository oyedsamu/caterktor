package io.github.oyedsamu.caterktor

import io.ktor.client.HttpClientConfig

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
    private val _converters: MutableList<BodyConverter> = mutableListOf()
    private var _timeoutConfig: TimeoutConfig? = null
    private var _defaultUnwrapper: ResponseUnwrapper? = null
    private var _defaultEnveloper: RequestEnveloper? = null
    private var _ktorBlock: (HttpClientConfig<*>.() -> Unit)? = null

    /**
     * Configure per-attempt and advisory connection timeouts.
     *
     * [TimeoutConfig.requestTimeoutMs] is enforced by [NetworkClient] via a
     * coroutine timeout around each pipeline execution. [TimeoutConfig.connectTimeoutMs]
     * and [TimeoutConfig.socketTimeoutMs] are advisory — pass them to the engine
     * factory for enforcement at the transport level.
     */
    public fun timeout(block: TimeoutConfig.Builder.() -> Unit): CaterKtorBuilder = apply {
        _timeoutConfig = TimeoutConfig.Builder().apply(block).build()
    }

    /** The currently configured [TimeoutConfig], or `null` if none was set. */
    public val timeoutConfig: TimeoutConfig?
        get() = _timeoutConfig

    /**
     * The terminal transport. Must be set before [build] is invoked, either
     * directly or via the `install(engine)` helpers shipped in the
     * `caterktor-engine-*` modules.
     */
    public var transport: Transport? = null

    /**
     * The base URL for all relative-path requests issued through the typed
     * call helpers (`get`, `post`, …).
     *
     * - If `null`, every call must use an absolute URL.
     * - If set, relative paths are resolved against this URL per the rules in
     *   the URL resolver (see `resolveUrl`).
     */
    public var baseUrl: String? = null

    /**
     * Append [interceptor] to the pipeline. Returns this builder for chaining.
     */
    public fun addInterceptor(interceptor: Interceptor): CaterKtorBuilder = apply {
        _interceptors += interceptor
    }

    /**
     * Register a [BodyConverter]. Converters are tried in registration order;
     * the first whose [BodyConverter.supports] returns `true` for a given
     * content-type wins. Returns this builder for chaining.
     */
    public fun addConverter(converter: BodyConverter): CaterKtorBuilder = apply {
        _converters += converter
    }

    /** Snapshot of currently registered interceptors, in pipeline order. */
    public val interceptors: List<Interceptor>
        get() = _interceptors.toList()

    /** Snapshot of currently registered body converters, in registration order. */
    public val converters: List<BodyConverter>
        get() = _converters.toList()

    /** The default [ResponseUnwrapper], or `null` if none was set. */
    public val defaultUnwrapper: ResponseUnwrapper?
        get() = _defaultUnwrapper

    /** The default [RequestEnveloper], or `null` if none was set. */
    public val defaultEnveloper: RequestEnveloper?
        get() = _defaultEnveloper

    /**
     * Set the default [ResponseUnwrapper] applied to every response decoded by this client.
     *
     * The unwrapper runs after the transport returns raw bytes and before [BodyConverter.decode]
     * is called. A per-request override via [CaterKtorKeys.UNWRAPPER] in [NetworkRequest.tags]
     * takes precedence over this default.
     */
    public fun unwrapper(unwrapper: ResponseUnwrapper): CaterKtorBuilder = apply {
        _defaultUnwrapper = unwrapper
    }

    /**
     * Set the default [RequestEnveloper] applied to every request body encoded by this client.
     *
     * The enveloper runs after [BodyConverter.encode] produces bytes and before [RequestBody]
     * is built. A per-request override via [CaterKtorKeys.ENVELOPER] in [NetworkRequest.tags]
     * takes precedence over this default.
     */
    public fun enveloper(enveloper: RequestEnveloper): CaterKtorBuilder = apply {
        _defaultEnveloper = enveloper
    }

    /**
     * Apply additional configuration to the underlying Ktor [HttpClient].
     *
     * This is the K4 escape hatch: use it to install Ktor plugins (caching, logging,
     * content-encoding, etc.) that CaterKtor does not surface directly.
     *
     * The block is applied **after** the engine-specific factory configures the client.
     * Multiple calls accumulate — each block is applied in registration order.
     *
     * Only effective when [transport] is a [KtorTransport]. Silently ignored otherwise.
     *
     * ```kotlin
     * val client = CaterKtor {
     *     transport = OkHttpTransport()
     *     ktor {
     *         install(HttpCache)
     *     }
     * }
     * ```
     */
    public fun ktor(block: HttpClientConfig<*>.() -> Unit): CaterKtorBuilder = apply {
        val existing = _ktorBlock
        _ktorBlock = if (existing == null) block else ({ existing(); block() })
    }

    internal fun build(): NetworkClient {
        val t = checkNotNull(transport) {
            "CaterKtor: `transport` must be configured before build. " +
                "Provide one directly or install a caterktor-engine-* module."
        }
        // K4: apply any ktor { } blocks on top of the transport's HttpClient
        val finalTransport: Transport = _ktorBlock?.let { block ->
            if (t is KtorTransport) KtorTransport(t.httpClient.config(block)) else t
        } ?: t

        return NetworkClient(
            transport = finalTransport,
            interceptors = _interceptors.toList(),
            converters = _converters.toList(),
            baseUrl = baseUrl,
            timeoutConfig = _timeoutConfig,
            defaultUnwrapper = _defaultUnwrapper,
            defaultEnveloper = _defaultEnveloper,
        )
    }
}
