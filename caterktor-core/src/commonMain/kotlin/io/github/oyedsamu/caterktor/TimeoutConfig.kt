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
 *   — advisory values. Pass them to the engine factory when constructing the
 *   transport (e.g. `OkHttpTransport { ... }`); CaterKtor records them here
 *   for introspection and potential enforcement by the transport adapter.
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
 *   Advisory — the transport engine is responsible for enforcement.
 * @property socketTimeoutMs Maximum idle time (ms) between data packets on an
 *   open connection. Advisory — the transport engine is responsible.
 * @property requestTimeoutMs Maximum time (ms) for a single request attempt,
 *   from the moment the pipeline starts executing to the first byte of the
 *   response body. Enforced by [NetworkClient] via a coroutine timeout.
 */
@ExperimentalCaterktor
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
