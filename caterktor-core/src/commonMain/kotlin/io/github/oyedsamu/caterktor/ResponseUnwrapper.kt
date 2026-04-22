package io.github.oyedsamu.caterktor

/**
 * Transforms raw response bytes before they are handed to a [BodyConverter] for decoding.
 *
 * Use this when the server wraps the real payload in an envelope — e.g. `{ "data": {...} }` —
 * and you want the [BodyConverter] to see only the inner payload. Exceptions thrown by [unwrap]
 * are caught by the framework and mapped to [NetworkError.Serialization].
 *
 * ## Built-ins
 * - [ResponseUnwrapper.Raw] — identity, passes bytes through unchanged (the default)
 * - `DataFieldUnwrapper` (in `caterktor-serialization-json`) — extracts a named JSON field
 * - `PagedUnwrapper` (in `caterktor-serialization-json`) — extracts the items array from a paged JSON response
 *
 * ## Per-request override
 * Attach an instance via `tags = mapOf(CaterKtorKeys.UNWRAPPER to myUnwrapper)` on any call.
 * The per-request value takes precedence over the per-client default configured in [CaterKtorBuilder].
 *
 * ## Thread safety
 * Implementations must be stateless or thread-safe — the same instance is called concurrently.
 */
@ExperimentalCaterktor
public interface ResponseUnwrapper {

    /**
     * Transform [body] into the body that will be passed to [BodyConverter.decode].
     *
     * The default implementation keeps existing byte-based unwrappers working by
     * consuming [body] through [bytes][ResponseBody.bytes] and returning a
     * replayable [ResponseBody.Bytes].
     *
     * @param body        The raw body from the network response.
     * @param contentType The bare content-type (e.g. `"application/json"`), or `null` if absent.
     * @param response    The full [NetworkResponse] for header / status inspection.
     * @return The body to hand to [BodyConverter.decode].
     */
    public fun unwrap(body: ResponseBody, contentType: String?, response: NetworkResponse): ResponseBody =
        ResponseBody.Bytes(
            bytes = unwrap(body.bytes(), contentType, response),
            contentType = body.contentType,
        )

    /**
     * Transform [raw] bytes into the bytes that will be passed to [BodyConverter.decode].
     *
     * @param raw         The raw bytes from the network response.
     * @param contentType The bare content-type (e.g. `"application/json"`), or `null` if absent.
     * @param response    The full [NetworkResponse] for header / status inspection.
     * @return The bytes to hand to [BodyConverter.decode]. May be [raw] itself.
     */
    public fun unwrap(raw: ByteArray, contentType: String?, response: NetworkResponse): ByteArray = raw

    public companion object {
        /**
         * Identity unwrapper — passes the response body to [BodyConverter] unchanged.
         * This is the default used when no unwrapper is configured.
         */
        public val Raw: ResponseUnwrapper = object : ResponseUnwrapper {
            override fun unwrap(body: ResponseBody, contentType: String?, response: NetworkResponse): ResponseBody = body
            override fun unwrap(raw: ByteArray, contentType: String?, response: NetworkResponse): ByteArray = raw
            override fun toString(): String = "ResponseUnwrapper.Raw"
        }
    }
}
