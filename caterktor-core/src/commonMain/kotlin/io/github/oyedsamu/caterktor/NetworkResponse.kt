package io.github.oyedsamu.caterktor

/**
 * The raw HTTP response from the transport layer, before any parsing or envelope unwrapping.
 *
 * This is the output of the final (transport) interceptor in the pipeline.
 * Upper layers — such as `ResponseUnwrapper` — operate on this value.
 *
 * @property status The HTTP status code of the response.
 * @property headers The response headers.
 * @property body The raw response body bytes. Will be replaced by a streaming Source in B3.
 */
public data class NetworkResponse(
    public val status: HttpStatus,
    public val headers: Headers,
    public val body: ResponseBytes,
)
