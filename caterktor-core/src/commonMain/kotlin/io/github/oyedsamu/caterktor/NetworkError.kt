package io.github.oyedsamu.caterktor

/**
 * Represents a failed network operation. Every variant carries an optional [cause]
 * for debugging; callers SHOULD NOT rely on [cause] for control flow — use the
 * typed variant itself and its payload.
 *
 * ## Cancellation
 * There is intentionally **no `Cancelled` variant**. `kotlinx.coroutines.CancellationException`
 * is a Kotlin coroutines control-flow signal and must never be caught and wrapped
 * by a library — doing so breaks structured concurrency. If a coroutine is
 * cancelled, the `CancellationException` propagates to the caller's scope
 * directly and never materializes as a [NetworkResult.Failure]. See PRD-v2 §5.3
 * and non-negotiable principle 1.
 *
 * If you believe you need to handle cancellation as a result, handle it outside
 * CaterKtor by catching `CancellationException` at the scope you own.
 */
public sealed interface NetworkError {

    /** Underlying cause for debugging. Do not branch on this for control flow. */
    public val cause: Throwable?

    /**
     * The server returned an HTTP error status (typically 4xx or 5xx).
     *
     * @property status The response status code.
     * @property headers The response headers.
     * @property body The response body — see [ErrorBody]. May be [ErrorBody.Empty]
     *   when the server sent no body.
     */
    public data class Http(
        public val status: HttpStatus,
        public val headers: Headers,
        public val body: ErrorBody,
        override val cause: Throwable? = null,
    ) : NetworkError

    /**
     * The connection could not be established. See [ConnectionFailureKind] for
     * the specific reason.
     *
     * This replaces the misleadingly-named "NoInternet" pattern from v1. A DNS
     * failure, a refused connection, an unreachable host, and a TLS handshake
     * failure are distinct failure modes that a caller may want to handle
     * differently.
     */
    public data class ConnectionFailed(
        public val kind: ConnectionFailureKind,
        override val cause: Throwable? = null,
    ) : NetworkError

    /**
     * The operation exceeded a time limit. See [TimeoutKind] for which limit was hit.
     */
    public data class Timeout(
        public val kind: TimeoutKind,
        override val cause: Throwable? = null,
    ) : NetworkError

    /**
     * The request body could not be serialized, or the response body could not
     * be deserialized.
     *
     * @property phase Whether the failure occurred during [SerializationPhase.Encoding]
     *   (request) or [SerializationPhase.Decoding] (response).
     * @property rawBody The raw bytes in hand at the time of failure. Populated
     *   on decoding failures when bytes were received; `null` on encoding failures
     *   (there is nothing "raw" yet) and on decoding failures where the transport
     *   did not surface the bytes.
     */
    public data class Serialization(
        public val phase: SerializationPhase,
        public val rawBody: RawBody? = null,
        override val cause: Throwable? = null,
    ) : NetworkError

    /**
     * An HTTP protocol violation occurred (e.g. malformed response, too many
     * redirects, unexpected framing).
     *
     * @property message Human-readable description of the violation.
     */
    public data class Protocol(
        public val message: String,
        override val cause: Throwable? = null,
    ) : NetworkError

    /**
     * A circuit breaker rejected the request before it reached the transport.
     *
     * @property name Human-readable breaker name.
     * @property state The breaker state that rejected the request.
     */
    public data class CircuitOpen(
        public val name: String,
        public val state: CircuitBreakerState,
        override val cause: Throwable? = null,
    ) : NetworkError

    /**
     * The device has no network connectivity. Emitted by [ConnectivityInterceptor]
     * (from `caterktor-connectivity`) when a request fails with [ConnectionFailed]
     * and the [ConnectivityProbe] reports the device is offline.
     *
     * This variant is only produced when the opt-in `caterktor-connectivity` module
     * is on the classpath and a [ConnectivityInterceptor] is installed.
     */
    public data class Offline(
        override val cause: Throwable? = null,
    ) : NetworkError

    /**
     * An error that does not fit any other category. [cause] is guaranteed
     * non-null for this variant.
     *
     * If you find yourself handling this variant in application code, open an
     * issue — it may deserve its own typed variant.
     */
    public data class Unknown(
        override val cause: Throwable,
    ) : NetworkError
}

/** Why a connection could not be established. See [NetworkError.ConnectionFailed]. */
public enum class ConnectionFailureKind {
    /** The hostname could not be resolved. */
    Dns,

    /** The server actively refused the connection. */
    Refused,

    /** The network is unreachable (no route to host). */
    Unreachable,

    /** The TLS handshake failed (certificate, cipher, or protocol mismatch). */
    TlsHandshake,
}

/** Which timeout was exceeded. See [NetworkError.Timeout]. */
public enum class TimeoutKind {
    /** Time to establish the TCP connection. */
    Connect,

    /** Time between data packets on an established connection. */
    Socket,

    /** Total time for a single request attempt. */
    Request,

    /**
     * The cross-attempt deadline was exceeded. A deadline spans retries and
     * auth-refresh waits; it is the hard wall-clock limit for the entire
     * logical operation.
     */
    Deadline,
}

/** Whether the failure occurred during encoding (request) or decoding (response). */
public enum class SerializationPhase {
    /** The request body could not be serialized. */
    Encoding,

    /** The response body could not be deserialized. */
    Decoding,
}
