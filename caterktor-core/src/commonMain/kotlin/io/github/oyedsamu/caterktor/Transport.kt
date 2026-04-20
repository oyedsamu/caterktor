package io.github.oyedsamu.caterktor

/**
 * The terminal stage of the CaterKtor pipeline: the adapter that turns a
 * [NetworkRequest] into a [NetworkResponse] by issuing an actual HTTP call.
 *
 * A [Transport] sits below every interceptor. `caterktor-core` defines only
 * this contract; concrete implementations ship in the `caterktor-engine-*`
 * modules (Ktor-backed on JVM/Android/Apple/Linux) or are supplied in tests
 * via `caterktor-testing`.
 *
 * ## Contract
 *
 *  - Implementations must be `suspend`-correct and honor structured
 *    concurrency — cancellation propagates, exceptions surface mapped into
 *    [NetworkError] *at the boundary* (by the Ktor boundary layer), not as
 *    ad-hoc throwables.
 *  - Transports are called exactly once per logical attempt. Retries live in
 *    an interceptor above the transport, not inside it.
 *  - Transports do not wrap the call in `withContext(Dispatchers.IO)`. The
 *    engine's own dispatching is authoritative; callers' context is preserved
 *    on resume (see execution plan §1 decision 4).
 */
@ExperimentalCaterktor
public fun interface Transport {
    public suspend fun execute(request: NetworkRequest): NetworkResponse
}
