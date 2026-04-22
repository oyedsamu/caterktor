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
    private val _converters: MutableList<BodyConverter> = mutableListOf()
    private val _defaultHeaderEntries: MutableList<Pair<String, suspend () -> String>> = mutableListOf()
    private var _timeoutConfig: TimeoutConfig? = null
    private var _contentNegotiation: ContentNegotiationRegistry = ContentNegotiationRegistry.Empty
    private var _defaultUnwrapper: ResponseUnwrapper? = null
    private var _defaultEnveloper: RequestEnveloper? = null
    private var _transportFinalizer: ((Transport) -> Transport)? = null
    private var _maxBodyDecodeBytes: Int = 10 * 1024 * 1024

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

    /**
     * Add a static default header sent with every request.
     *
     * The header is only added if the outgoing request does not already contain
     * a header with the same name (case-insensitive). Per-request headers always win.
     *
     * Multiple calls accumulate — each named header is added in registration order.
     * A [DefaultHeadersInterceptor] is prepended to the pipeline automatically at [build] time.
     */
    public fun defaultHeader(name: String, value: String): CaterKtorBuilder = apply {
        _defaultHeaderEntries += name to { value }
    }

    /**
     * Add a dynamic default header evaluated per-request via [provider].
     *
     * [provider] is called inside the interceptor pipeline (a coroutine context) so it may
     * suspend — e.g. to read a token from a store. It is called once per request, not once
     * at build time. Do not call long-blocking I/O here.
     *
     * The header is only added if the outgoing request does not already contain a header
     * with the same name (case-insensitive).
     */
    public fun defaultHeader(name: String, provider: suspend () -> String): CaterKtorBuilder = apply {
        _defaultHeaderEntries += name to provider
    }

    /**
     * Add multiple default headers via a [DefaultHeadersBuilder] DSL block.
     *
     * Each entry in the block is evaluated with the same semantics as [defaultHeader].
     *
     * ```kotlin
     * val client = CaterKtor {
     *     transport = OkHttpTransport()
     *     defaultHeaders {
     *         set("X-App-Version", "2.0.0")
     *         set("X-Platform", "Android")
     *         set("X-Correlation-Id") { UUID.randomUUID().toString() }
     *     }
     * }
     * ```
     */
    public fun defaultHeaders(block: DefaultHeadersBuilder.() -> Unit): CaterKtorBuilder = apply {
        _defaultHeaderEntries += DefaultHeadersBuilder().apply(block).entries
    }

    /** Snapshot of currently registered interceptors, in pipeline order. */
    public val interceptors: List<Interceptor>
        get() = _interceptors.toList()

    /** Snapshot of currently registered body converters, in registration order. */
    public val converters: List<BodyConverter>
        get() = _converters.toList()

    /** Registered content negotiation converters and media types. */
    public val contentNegotiation: ContentNegotiationRegistry
        get() = _contentNegotiation

    /**
     * Register media-type-aware converters.
     *
     * Registered entries are used for request converter selection, response
     * `Content-Type` dispatch, and default `Accept` construction for typed calls.
     */
    public fun contentNegotiation(block: ContentNegotiationRegistry.Builder.() -> Unit): CaterKtorBuilder = apply {
        _contentNegotiation = _contentNegotiation.toBuilder().apply(block).build()
    }

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
     * is called. A per-request override via [CaterKtorKeys.UNWRAPPER] in [NetworkRequest.attributes]
     * takes precedence over this default.
     */
    public fun unwrapper(unwrapper: ResponseUnwrapper): CaterKtorBuilder = apply {
        _defaultUnwrapper = unwrapper
    }

    /**
     * Set the default [RequestEnveloper] applied to every request body encoded by this client.
     *
     * The enveloper runs after [BodyConverter.encode] produces bytes and before [RequestBody]
     * is built. A per-request override via [CaterKtorKeys.ENVELOPER] in [NetworkRequest.attributes]
     * takes precedence over this default.
     */
    public fun enveloper(enveloper: RequestEnveloper): CaterKtorBuilder = apply {
        _defaultEnveloper = enveloper
    }

    /**
     * Register a [finalizer] that transforms the [Transport] at [build] time.
     *
     * This hook is the extension point used by `caterktor-ktor`'s `ktor { }`
     * DSL function — it lets sibling modules post-process the transport without
     * introducing a Ktor dependency in core. Multiple calls accumulate; each
     * finalizer is applied to the result of the previous one, in registration order.
     *
     * Engine modules and application code should prefer higher-level APIs such as
     * the `ktor { }` extension defined in `caterktor-ktor`.
     */
    public fun addTransportFinalizer(finalizer: (Transport) -> Transport): CaterKtorBuilder = apply {
        val existing = _transportFinalizer
        _transportFinalizer = if (existing == null) finalizer else { t -> finalizer(existing(t)) }
    }

    /**
     * Set the maximum number of bytes that [NetworkClient] will materialise into a [ByteArray]
     * when decoding a response body via [BodyConverter.decode].
     *
     * When the response `Content-Length` header is present and exceeds this limit, decoding
     * is short-circuited with a [NetworkError.Serialization] failure. Bodies without a
     * known `Content-Length` (e.g. chunked transfer) are not guarded by this check.
     *
     * Defaults to 10 MiB (10 × 1024 × 1024 bytes).
     *
     * @param bytes The maximum body size in bytes. Must be positive.
     */
    public fun maxBodyDecodeBytes(bytes: Int): CaterKtorBuilder = apply {
        require(bytes > 0) { "maxBodyDecodeBytes must be positive, was $bytes" }
        _maxBodyDecodeBytes = bytes
    }

    internal fun build(): NetworkClient {
        val t = checkNotNull(transport) {
            "CaterKtor: `transport` must be configured before build. " +
                "Provide one directly or install a caterktor-engine-* module."
        }
        // H1: auto-install DefaultHeadersInterceptor at front if any default headers are configured
        if (_defaultHeaderEntries.isNotEmpty()) {
            _interceptors.add(0, DefaultHeadersInterceptor(_defaultHeaderEntries.toList()))
        }
        // K4: apply any transport finalizers registered by extension modules (e.g. caterktor-ktor)
        val finalTransport: Transport = _transportFinalizer?.invoke(t) ?: t

        return NetworkClient(
            transport = finalTransport,
            interceptors = _interceptors.toList(),
            converters = _converters.toList(),
            contentNegotiation = _contentNegotiation,
            baseUrl = baseUrl,
            timeoutConfig = _timeoutConfig,
            defaultUnwrapper = _defaultUnwrapper,
            defaultEnveloper = _defaultEnveloper,
            maxBodyDecodeBytes = _maxBodyDecodeBytes,
        )
    }
}
