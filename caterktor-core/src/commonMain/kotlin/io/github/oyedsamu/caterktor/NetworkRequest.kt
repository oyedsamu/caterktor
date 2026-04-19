package io.github.oyedsamu.caterktor

/**
 * An outgoing HTTP request, fully resolved and ready to be dispatched through the
 * interceptor pipeline.
 *
 * @property method The HTTP method (verb) for this request.
 * @property url The fully-resolved URL string, including scheme, host, path, and query.
 * @property headers The request headers.
 * @property body The optional request body, or `null` for bodyless methods such as [HttpMethod.GET].
 * @property tags An arbitrary key–value bag for interceptor-to-interceptor communication.
 *   Keys are strings; values may be any type, but **must be immutable**. It is the caller's
 *   responsibility to ensure that values stored here are safe to share across coroutines.
 *   Typical uses include signalling to downstream interceptors (e.g. `"skipAuth" to true`)
 *   or carrying per-request metadata (e.g. a retry counter).
 */
public data class NetworkRequest(
    public val method: HttpMethod,
    public val url: String,
    public val headers: Headers,
    public val body: RequestBody?,
    public val tags: Map<String, Any>,
) {
    public companion object {
        /**
         * Creates a [NetworkRequest] with sensible defaults for optional parameters.
         *
         * @param method The HTTP method.
         * @param url The fully-resolved URL string.
         * @param headers Request headers; defaults to [Headers.Empty].
         * @param body Optional request body; defaults to `null`.
         * @param tags Interceptor communication bag; defaults to an empty map.
         *   Values **must be immutable** — it is the caller's responsibility.
         */
        public operator fun invoke(
            method: HttpMethod,
            url: String,
            headers: Headers = Headers.Empty,
            body: RequestBody? = null,
            tags: Map<String, Any> = emptyMap(),
        ): NetworkRequest = NetworkRequest(
            method = method,
            url = url,
            headers = headers,
            body = body,
            tags = tags,
        )
    }
}
