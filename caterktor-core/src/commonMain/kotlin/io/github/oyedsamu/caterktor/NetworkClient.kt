package io.github.oyedsamu.caterktor

import kotlin.time.Instant

/**
 * The CaterKtor client — a constructed, disposable object that walks a request
 * through an ordered interceptor pipeline and a terminal [Transport].
 *
 * Instances are created via the [CaterKtor] builder. The client itself holds
 * no global state; every collaborator (interceptors, transport) is supplied at
 * construction time and may be substituted freely in tests.
 *
 * ## Pipeline semantics
 *
 * On [execute], the client constructs a fresh root [Chain] and invokes
 * `proceed`. The request traverses each interceptor in registration order;
 * the response traverses them in reverse. Ordering is authoritative, explicit,
 * and introspectable via [describePipeline] — a deliberate choice over the
 * event-based plugin pipelines that sit below us in Ktor.
 *
 * ## Scope
 *
 * At this stage (Wave 4) the client exposes only the raw-response surface:
 * `execute(request) -> NetworkResponse`. Typed, [NetworkResult]-returning call
 * helpers (`get<T>`, `post<T>`, …) layer on top once body converters and the
 * response-unwrapper arrive.
 */
@ExperimentalCaterktor
public class NetworkClient internal constructor(
    private val transport: Transport,
    internal val interceptors: List<Interceptor>,
) {
    /**
     * Execute [request] through the full interceptor pipeline.
     *
     * @param request The request to dispatch.
     * @param deadline Optional wall-clock deadline spanning every attempt and
     *   wait performed on behalf of this call. `null` disables the deadline.
     */
    public suspend fun execute(
        request: NetworkRequest,
        deadline: Instant? = null,
    ): NetworkResponse {
        val chain = RealChain(
            request = request,
            attempt = 1,
            deadline = deadline,
            interceptors = interceptors,
            index = 0,
            transport = transport,
        )
        return chain.proceed(request)
    }

    /**
     * Return the ordered pipeline as a list of human-readable names, terminating
     * in the transport. Intended for logging, diagnostics, and documentation —
     * not for programmatic identification of interceptors.
     *
     * Example output:
     * ```
     * [0] AuthInterceptor
     * [1] RetryInterceptor
     * [2] LoggerInterceptor
     * [3] Transport(KtorTransport)
     * ```
     */
    public fun describePipeline(): List<String> = buildList(interceptors.size + 1) {
        interceptors.forEachIndexed { i, interceptor ->
            add("[$i] ${interceptor.displayName()}")
        }
        add("[${interceptors.size}] Transport(${transport.displayName()})")
    }

    private fun Interceptor.displayName(): String = this::class.simpleName ?: "<anonymous>"
    private fun Transport.displayName(): String = this::class.simpleName ?: "<anonymous>"
}
