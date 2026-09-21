@file:OptIn(ExperimentalCaterktor::class)

package io.github.oyedsamu.caterktor

/**
 * Per-client timeout configuration for CaterKtor.
 *
 * Timeouts are applied at two levels:
 *
 * - **Request timeout** ([requestTimeoutMs]) — enforced by [NetworkClient]
 *   via a coroutine timeout around the full pipeline execution for a single
 *   attempt. This is independent of the transport engine's own timeouts.
 * - **Connect and socket timeouts** ([connectTimeoutMs], [socketTimeoutMs])
 *   — applied to the engine by the [TransportFactory] passed to
 *   [CaterKtorBuilder.engine]. A transport assigned directly to
 *   [CaterKtorBuilder.transport] is constructed before the builder runs, and a
 *   Ktor engine cannot be reconfigured afterwards, so these two values cannot
 *   reach it; configure that transport's own engine block instead.
 *
 * | Engine | `connectTimeoutMs` | `socketTimeoutMs` |
 * |---|---|---|
 * | `Cio` | `endpoint.connectTimeout` | `endpoint.socketTimeout` |
 * | `OkHttp` | `connectTimeout` | `readTimeout` and `writeTimeout` |
 * | `Darwin` | not expressible | `timeoutIntervalForRequest` |
 *
 * `NSURLSession` has no separate connect timeout, so on Darwin the connect
 * phase is bounded by [socketTimeoutMs] and by [requestTimeoutMs] rather than
 * independently.
 *
 * All values are in milliseconds. `null` means "no limit" for that dimension.
 *
 * ## Deadlines vs timeouts
 *
 * A *deadline* ([NetworkClient.execute]'s `deadline` parameter) is a
 * wall-clock [kotlin.time.Instant] that spans the entire logical operation,
 * including retries. A *timeout* is a per-attempt limit. Both can be active
 * simultaneously; whichever expires first wins.
 *
 * ## Usage
 *
 * ```kotlin
 * val client = CaterKtor {
 *     transport = OkHttpTransport()
 *     timeout {
 *         requestTimeoutMs = 30_000
 *     }
 * }
 * ```
 *
 * @property connectTimeoutMs Maximum time (ms) to establish a TCP connection.
 *   Applied by the engine when the transport comes from [CaterKtorBuilder.engine].
 *   Not expressible on Darwin.
 * @property socketTimeoutMs Maximum idle time (ms) between data packets on an
 *   open connection. Applied by the engine when the transport comes from
 *   [CaterKtorBuilder.engine].
 * @property requestTimeoutMs Maximum time (ms) for a single request attempt,
 *   from the moment the pipeline starts executing to the first byte of the
 *   response body. Enforced by [NetworkClient] via a coroutine timeout.
 */
public data class TimeoutConfig(
    public val connectTimeoutMs: Long? = null,
    public val socketTimeoutMs: Long? = null,
    public val requestTimeoutMs: Long? = null,
) {

    /**
     * Mutable builder for [TimeoutConfig], used by the `timeout { }` DSL on
     * [CaterKtorBuilder].
     */
    @CaterKtorDsl
    public class Builder {
        /** @see TimeoutConfig.connectTimeoutMs */
        public var connectTimeoutMs: Long? = null

        /** @see TimeoutConfig.socketTimeoutMs */
        public var socketTimeoutMs: Long? = null

        /** @see TimeoutConfig.requestTimeoutMs */
        public var requestTimeoutMs: Long? = null

        /** Build an immutable [TimeoutConfig] from the current state. */
        public fun build(): TimeoutConfig = TimeoutConfig(
            connectTimeoutMs = connectTimeoutMs,
            socketTimeoutMs = socketTimeoutMs,
            requestTimeoutMs = requestTimeoutMs,
        )
    }
}
