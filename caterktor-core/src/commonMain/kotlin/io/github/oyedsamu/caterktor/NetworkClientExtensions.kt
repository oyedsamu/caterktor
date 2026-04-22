package io.github.oyedsamu.caterktor

import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.time.Instant

/**
 * Execute a GET request and decode the response body to [T].
 *
 * @param T The expected response body type. Must be supported by a registered [BodyConverter].
 *   Use [Unit] to ignore the response body.
 * @param url Absolute URL or relative path (resolved against [NetworkClient]'s base URL).
 *   Path templates are expanded via `expandPathTemplate` when [pathParams] is non-empty.
 * @param pathParams Template parameters to expand in [url], e.g. `mapOf("id" to "42")` for
 *   a [url] of `"users/{id}"`.
 * @param headers Additional request headers. Merged with any headers added by interceptors.
 * @param tags Interceptor communication bag; values must be immutable.
 * @param deadline Optional wall-clock deadline for the entire logical request.
 */
@ExperimentalCaterktor
public suspend inline fun <reified T : Any> NetworkClient.get(
    url: String,
    pathParams: Map<String, Any> = emptyMap(),
    headers: Headers = Headers.Empty,
    tags: Map<String, Any> = emptyMap(),
    deadline: Instant? = null,
): NetworkResult<T> {
    val resolvedUrl = resolveUrl(baseUrl, expandPathTemplate(url, pathParams))
    val request = NetworkRequest(HttpMethod.GET, resolvedUrl, headers, null, tags)
    return call(request, typeOf<T>(), deadline)
}

/**
 * Execute a HEAD request.
 *
 * HEAD responses have no body; use [Unit] as [T] or [NetworkResult.Success.headers] for the
 * response headers.
 */
@ExperimentalCaterktor
public suspend inline fun <reified T : Any> NetworkClient.head(
    url: String,
    pathParams: Map<String, Any> = emptyMap(),
    headers: Headers = Headers.Empty,
    tags: Map<String, Any> = emptyMap(),
    deadline: Instant? = null,
): NetworkResult<T> {
    val resolvedUrl = resolveUrl(baseUrl, expandPathTemplate(url, pathParams))
    val request = NetworkRequest(HttpMethod.HEAD, resolvedUrl, headers, null, tags)
    return call(request, typeOf<T>(), deadline)
}

/**
 * Execute a DELETE request and decode the optional response body to [T].
 *
 * Use `delete<Unit>(...)` for endpoints that return 204 No Content.
 */
@ExperimentalCaterktor
public suspend inline fun <reified T : Any> NetworkClient.delete(
    url: String,
    pathParams: Map<String, Any> = emptyMap(),
    headers: Headers = Headers.Empty,
    tags: Map<String, Any> = emptyMap(),
    deadline: Instant? = null,
): NetworkResult<T> {
    val resolvedUrl = resolveUrl(baseUrl, expandPathTemplate(url, pathParams))
    val request = NetworkRequest(HttpMethod.DELETE, resolvedUrl, headers, null, tags)
    return call(request, typeOf<T>(), deadline)
}

/**
 * Execute a POST request, encoding [body] with the first [BodyConverter] that
 * supports [contentType], and decode the response to [T].
 *
 * @param T The expected response body type.
 * @param B The request body type. Must be serializable by a registered [BodyConverter].
 * @param body The request body value.
 * @param contentType The `Content-Type` for the request body. Default: `"application/json"`.
 * @param url Absolute URL or relative path (resolved against [NetworkClient]'s base URL).
 * @param pathParams Template parameters to expand in [url].
 * @param headers Additional request headers.
 * @param tags Interceptor communication bag; values must be immutable.
 * @param deadline Optional wall-clock deadline for the entire logical request.
 */
@ExperimentalCaterktor
public suspend inline fun <reified T : Any, reified B : Any> NetworkClient.post(
    url: String,
    body: B,
    contentType: String = "application/json",
    pathParams: Map<String, Any> = emptyMap(),
    headers: Headers = Headers.Empty,
    tags: Map<String, Any> = emptyMap(),
    deadline: Instant? = null,
): NetworkResult<T> {
    val resolvedUrl = resolveUrl(baseUrl, expandPathTemplate(url, pathParams))
    return callWithBody(
        resolvedUrl = resolvedUrl,
        method = HttpMethod.POST,
        body = body,
        bodyType = typeOf<B>(),
        responseType = typeOf<T>(),
        contentType = contentType,
        headers = headers,
        tags = tags,
        deadline = deadline,
    )
}

/** Execute a PUT request. See [post] for parameter documentation. */
@ExperimentalCaterktor
public suspend inline fun <reified T : Any, reified B : Any> NetworkClient.put(
    url: String,
    body: B,
    contentType: String = "application/json",
    pathParams: Map<String, Any> = emptyMap(),
    headers: Headers = Headers.Empty,
    tags: Map<String, Any> = emptyMap(),
    deadline: Instant? = null,
): NetworkResult<T> {
    val resolvedUrl = resolveUrl(baseUrl, expandPathTemplate(url, pathParams))
    return callWithBody(
        resolvedUrl = resolvedUrl,
        method = HttpMethod.PUT,
        body = body,
        bodyType = typeOf<B>(),
        responseType = typeOf<T>(),
        contentType = contentType,
        headers = headers,
        tags = tags,
        deadline = deadline,
    )
}

/** Execute a PATCH request. See [post] for parameter documentation. */
@ExperimentalCaterktor
public suspend inline fun <reified T : Any, reified B : Any> NetworkClient.patch(
    url: String,
    body: B,
    contentType: String = "application/json",
    pathParams: Map<String, Any> = emptyMap(),
    headers: Headers = Headers.Empty,
    tags: Map<String, Any> = emptyMap(),
    deadline: Instant? = null,
): NetworkResult<T> {
    val resolvedUrl = resolveUrl(baseUrl, expandPathTemplate(url, pathParams))
    return callWithBody(
        resolvedUrl = resolvedUrl,
        method = HttpMethod.PATCH,
        body = body,
        bodyType = typeOf<B>(),
        responseType = typeOf<T>(),
        contentType = contentType,
        headers = headers,
        tags = tags,
        deadline = deadline,
    )
}

/**
 * Encodes [body] using the first matching [BodyConverter] and executes the request.
 * Called by the inline body-bearing variants; not intended for direct use.
 *
 * Public only because the inline [post] / [put] / [patch] helpers must delegate
 * to it across module boundaries. Prefer those entry points in application code.
 */
@ExperimentalCaterktor
public suspend fun <T : Any, B : Any> NetworkClient.callWithBody(
    resolvedUrl: String,
    method: HttpMethod,
    body: B,
    bodyType: KType,
    responseType: KType,
    contentType: String,
    headers: Headers,
    tags: Map<String, Any>,
    deadline: Instant?,
): NetworkResult<T> {
    val bareContentType = ContentNegotiationRegistry.bareContentType(contentType) ?: contentType.trim()
    val converter = contentNegotiation.converterFor(contentType)
        ?: converters.firstOrNull { it.supports(bareContentType) }
        ?: return NetworkResult.Failure(
            error = NetworkError.Serialization(
                phase = SerializationPhase.Encoding,
                rawBody = null,
                cause = IllegalStateException(
                    "No BodyConverter registered for content-type '$contentType'. " +
                        "Register one via CaterKtorBuilder.addConverter().",
                ),
            ),
            durationMs = 0L,
            attempts = 1,
            requestId = generateRequestId(),
        )
    val requestBody: RequestBody = try {
        val encodedBytes = converter.encode(body, bodyType, contentType)
        // Resolve enveloper: per-request tag takes precedence over per-client default
        val enveloper: RequestEnveloper? =
            (tags[CaterKtorKeys.ENVELOPER] as? RequestEnveloper) ?: defaultEnveloper
        enveloper?.envelop(encodedBytes, contentType)
            ?: RequestBody.Bytes(encodedBytes, contentType)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        return NetworkResult.Failure(
            error = NetworkError.Serialization(
                phase = SerializationPhase.Encoding,
                rawBody = null,
                cause = e,
            ),
            durationMs = 0L,
            attempts = 1,
            requestId = generateRequestId(),
        )
    }
    val request = NetworkRequest(method, resolvedUrl, headers, requestBody, tags)
    return call(request, responseType, deadline)
}
