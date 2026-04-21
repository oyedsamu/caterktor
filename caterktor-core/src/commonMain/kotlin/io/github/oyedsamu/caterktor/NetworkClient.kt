package io.github.oyedsamu.caterktor

import kotlin.random.Random
import kotlin.reflect.KType
import kotlin.time.Instant
import kotlin.time.TimeSource

/**
 * The CaterKtor client — a constructed, disposable object that walks a request
 * through an ordered interceptor pipeline and a terminal [Transport].
 *
 * Instances are created via the [CaterKtor] builder. The client itself holds
 * no global state; every collaborator (interceptors, transport, converters) is
 * supplied at construction time and may be substituted freely in tests.
 *
 * ## Pipeline semantics
 *
 * On [execute], the client constructs a fresh root [Chain] and invokes
 * `proceed`. The request traverses each interceptor in registration order;
 * the response traverses them in reverse. Ordering is authoritative, explicit,
 * and introspectable via [describePipeline] — a deliberate choice over the
 * event-based plugin pipelines that sit below us in Ktor.
 *
 * ## Typed helpers
 *
 * The raw [execute] surface is complemented by the reified typed helpers
 * (`get<T>`, `post<T, B>`, …) defined as extensions in `NetworkClientExtensions.kt`.
 * Those helpers resolve URLs against [baseUrl], encode request bodies via the
 * registered [converters], and map the resulting [NetworkResponse] into a
 * [NetworkResult].
 */
@ExperimentalCaterktor
public class NetworkClient internal constructor(
    private val transport: Transport,
    internal val interceptors: List<Interceptor>,
    @PublishedApi internal val converters: List<BodyConverter> = emptyList(),
    @PublishedApi internal val baseUrl: String? = null,
) {
    /**
     * Execute [request] through the full interceptor pipeline.
     *
     * @param request The request to dispatch.
     * @param deadline Optional wall-clock deadline spanning every attempt and
     *   wait performed on behalf of this call. `null` disables the deadline.
     */
    public suspend fun execute(
        request: NetworkRequest,
        deadline: Instant? = null,
    ): NetworkResponse {
        val chain = RealChain(
            request = request,
            attempt = 1,
            deadline = deadline,
            interceptors = interceptors,
            index = 0,
            transport = transport,
        )
        return chain.proceed(request)
    }

    /**
     * Return the ordered pipeline as a list of human-readable names, terminating
     * in the transport. Intended for logging, diagnostics, and documentation —
     * not for programmatic identification of interceptors.
     *
     * Example output:
     * ```
     * [0] AuthInterceptor
     * [1] RetryInterceptor
     * [2] LoggerInterceptor
     * [3] Transport(KtorTransport)
     * ```
     */
    public fun describePipeline(): List<String> = buildList(interceptors.size + 1) {
        interceptors.forEachIndexed { i, interceptor ->
            add("[$i] ${interceptor.displayName()}")
        }
        add("[${interceptors.size}] Transport(${transport.displayName()})")
    }

    private fun Interceptor.displayName(): String = this::class.simpleName ?: "<anonymous>"
    private fun Transport.displayName(): String = this::class.simpleName ?: "<anonymous>"
}

/**
 * Non-inline core of the typed call helpers. Runs [request] through the
 * pipeline, classifies the outcome, and decodes the response body (when the
 * status is a success) into [T].
 *
 * Cancellation is preserved: only [NetworkErrorException] is caught here.
 * `CancellationException` propagates freely.
 */
@PublishedApi
@OptIn(ExperimentalCaterktor::class)
internal suspend fun <T : Any> NetworkClient.call(
    request: NetworkRequest,
    responseType: KType,
    deadline: Instant? = null,
): NetworkResult<T> {
    val requestId = generateRequestId()
    val mark = TimeSource.Monotonic.markNow()
    return try {
        val response = execute(request, deadline)
        val durationMs = mark.elapsedNow().inWholeMilliseconds
        if (response.status.isClientError || response.status.isServerError) {
            val raw = RawBody(response.body, response.headers["Content-Type"])
            NetworkResult.Failure(
                error = NetworkError.Http(
                    status = response.status,
                    headers = response.headers,
                    body = ErrorBody(raw = raw, parsed = null),
                ),
                durationMs = durationMs,
                attempts = 1,
                requestId = requestId,
            )
        } else {
            decodeResponse(response, responseType, durationMs, requestId)
        }
    } catch (e: NetworkErrorException) {
        NetworkResult.Failure(
            error = e.error,
            durationMs = mark.elapsedNow().inWholeMilliseconds,
            attempts = 1,
            requestId = requestId,
        )
    }
}

@OptIn(ExperimentalCaterktor::class)
private fun <T : Any> NetworkClient.decodeResponse(
    response: NetworkResponse,
    responseType: KType,
    durationMs: Long,
    requestId: String,
): NetworkResult<T> {
    // Unit type means caller doesn't want a body decoded
    if (responseType.classifier == Unit::class) {
        @Suppress("UNCHECKED_CAST")
        return NetworkResult.Success(
            body = Unit as T,
            status = response.status,
            headers = response.headers,
            durationMs = durationMs,
            attempts = 1,
            requestId = requestId,
        )
    }
    val contentTypeHeader = response.headers["Content-Type"]
    val bareContentType = contentTypeHeader?.substringBefore(';')?.trim() ?: ""
    val converter = converters.firstOrNull { it.supports(bareContentType) }
        ?: return NetworkResult.Failure(
            error = NetworkError.Serialization(
                phase = SerializationPhase.Decoding,
                rawBody = RawBody(response.body, contentTypeHeader),
                cause = IllegalStateException(
                    "No BodyConverter registered for content-type '$bareContentType'. " +
                        "Register one via CaterKtorBuilder.addConverter().",
                ),
            ),
            durationMs = durationMs,
            attempts = 1,
            requestId = requestId,
        )
    return try {
        val raw = RawBody(response.body, contentTypeHeader)
        val body = converter.decode<T>(raw, responseType)
        NetworkResult.Success(
            body = body,
            status = response.status,
            headers = response.headers,
            durationMs = durationMs,
            attempts = 1,
            requestId = requestId,
        )
    } catch (e: Exception) {
        NetworkResult.Failure(
            error = NetworkError.Serialization(
                phase = SerializationPhase.Decoding,
                rawBody = RawBody(response.body, contentTypeHeader),
                cause = e,
            ),
            durationMs = durationMs,
            attempts = 1,
            requestId = requestId,
        )
    }
}

/**
 * Generate a 32-char hex correlation ID. Not a UUID — just high-entropy
 * enough to disambiguate in-flight requests for log correlation.
 */
@PublishedApi
internal fun generateRequestId(): String = buildString(32) {
    repeat(4) { append(Random.nextLong().toULong().toString(16).padStart(16, '0')) }
}
