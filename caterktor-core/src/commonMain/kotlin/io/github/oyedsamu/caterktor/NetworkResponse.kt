package io.github.oyedsamu.caterktor

/**
 * The raw HTTP response from the transport layer, before any parsing or envelope unwrapping.
 *
 * This is the output of the final (transport) interceptor in the pipeline.
 * Upper layers — such as `ResponseUnwrapper` — operate on this value.
 *
 * @property status The HTTP status code of the response.
 * @property headers The response headers.
 * @property body The raw response body.
 */
public data class NetworkResponse(
    public val status: HttpStatus,
    public val headers: Headers,
    public val body: ResponseBody,
) {
    /**
     * Source-compatible constructor for the pre-streaming API. Prefer passing a
     * [ResponseBody] directly for new code.
     */
    public constructor(
        status: HttpStatus,
        headers: Headers,
        body: ByteArray,
    ) : this(
        status = status,
        headers = headers,
        body = ResponseBody.Bytes(body, headers["Content-Type"]),
    )

    /** Convenience byte helper for small responses and legacy call sites. */
    public val bodyBytes: ByteArray
        get() = body.bytes()

    /** Convert the response body to the raw byte wrapper used by converters. */
    public fun rawBody(): RawBody =
        body.rawBody(headers["Content-Type"])
}
