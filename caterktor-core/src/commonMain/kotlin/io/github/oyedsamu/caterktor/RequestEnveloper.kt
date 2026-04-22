package io.github.oyedsamu.caterktor

/**
 * Wraps encoded request bytes in an envelope before they are handed to [KtorTransport].
 *
 * Use this when the server expects the real payload nested inside a wrapper object —
 * e.g. `{ "data": <payload> }`. The enveloper runs after [BodyConverter.encode] produces
 * bytes and before [RequestBody] is built.
 *
 * ## Built-ins
 * - [RequestEnveloper.None] — no-op, wraps bytes in a plain [RequestBody.Bytes] (the default)
 * - `DataFieldEnveloper` (in `caterktor-serialization-json`) — wraps the payload in `{ "field": <payload> }`
 *
 * ## Per-request override
 * Attach an instance via `tags = mapOf(CaterKtorKeys.ENVELOPER to myEnveloper)` on any call.
 * The per-request value takes precedence over the per-client default in [CaterKtorBuilder].
 *
 * ## Thread safety
 * Implementations must be stateless or thread-safe — the same instance is called concurrently.
 */
@ExperimentalCaterktor
public interface RequestEnveloper {

    /**
     * Wrap [encoded] bytes in an envelope and return the [RequestBody] to send.
     *
     * @param encoded     The bytes produced by [BodyConverter.encode].
     * @param contentType The content-type for which the bytes were encoded (e.g. `"application/json"`).
     * @return A [RequestBody] to pass to the transport. Typically [RequestBody.Bytes].
     */
    public fun envelop(encoded: ByteArray, contentType: String): RequestBody

    public companion object {
        /**
         * No-op enveloper — returns a plain [RequestBody.Bytes] wrapping [encoded] unchanged.
         * This is the default used when no enveloper is configured.
         */
        public val None: RequestEnveloper = object : RequestEnveloper {
            override fun envelop(encoded: ByteArray, contentType: String): RequestBody =
                RequestBody.Bytes(encoded, contentType)
            override fun toString(): String = "RequestEnveloper.None"
        }
    }
}
