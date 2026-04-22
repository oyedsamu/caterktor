package io.github.oyedsamu.caterktor

/**
 * An outgoing HTTP request, fully resolved and ready to be dispatched through the
 * interceptor pipeline.
 *
 * @property method The HTTP method (verb) for this request.
 * @property url The fully-resolved URL string, including scheme, host, path, and query.
 * @property headers The request headers.
 * @property body The optional request body, or `null` for bodyless methods such as [HttpMethod.GET].
 * @property attributes A typed, immutable collection of key-value pairs for interceptor-to-interceptor
 *   communication. Use [AttributeKey] to define typed keys and [Attributes] to build values.
 *   Typical uses include signalling to downstream interceptors (e.g. [CaterKtorKeys.SKIP_AUTH])
 *   or carrying per-request metadata.
 */
@ExperimentalCaterktor
public data class NetworkRequest(
    public val method: HttpMethod,
    public val url: String,
    public val headers: Headers,
    public val body: RequestBody?,
    public val attributes: Attributes,
) {
    public companion object {
        /**
         * Creates a [NetworkRequest] with sensible defaults for optional parameters.
         *
         * @param method The HTTP method.
         * @param url The fully-resolved URL string.
         * @param headers Request headers; defaults to [Headers.Empty].
         * @param body Optional request body; defaults to `null`.
         * @param attributes Typed interceptor communication bag; defaults to [Attributes.Empty].
         */
        public operator fun invoke(
            method: HttpMethod,
            url: String,
            headers: Headers = Headers.Empty,
            body: RequestBody? = null,
            attributes: Attributes = Attributes.Empty,
        ): NetworkRequest = NetworkRequest(
            method = method,
            url = url,
            headers = headers,
            body = body,
            attributes = attributes,
        )
    }
}
