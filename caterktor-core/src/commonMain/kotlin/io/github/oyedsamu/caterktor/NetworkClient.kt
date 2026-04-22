package io.github.oyedsamu.caterktor

import kotlin.random.Random
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlin.reflect.KType
import kotlin.time.Instant
import kotlin.time.TimeSource
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

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
    @PublishedApi internal val contentNegotiation: ContentNegotiationRegistry = ContentNegotiationRegistry.Empty,
    @PublishedApi internal val baseUrl: String? = null,
    internal val timeoutConfig: TimeoutConfig? = null,
    @PublishedApi internal val defaultUnwrapper: ResponseUnwrapper? = null,
    @PublishedApi internal val defaultEnveloper: RequestEnveloper? = null,
    /**
     * Maximum number of bytes that [decodeResponse] will materialise into a [ByteArray] before
     * calling [BodyConverter.decode]. When the response `Content-Length` header is present and
     * exceeds this value, decoding is short-circuited with a
     * [NetworkError.Serialization] failure rather than attempting to allocate a potentially
     * heap-busting buffer.
     *
     * Bodies whose `Content-Length` is absent (e.g. chunked transfer) are not guarded by this
     * check — the limit only applies when the server advertises a known size up-front.
     *
     * Defaults to 10 MiB (10 × 1024 × 1024 bytes). Set via [CaterKtorBuilder.maxBodyDecodeBytes].
     */
    @PublishedApi internal val maxBodyDecodeBytes: Int = 10 * 1024 * 1024,
) {

    private val _events: MutableSharedFlow<NetworkEvent> = MutableSharedFlow(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * A [SharedFlow] that emits a [NetworkEvent] for every request processed by
     * this client.
     *
     * The flow is hot and replay-less — only events emitted **after** a collector
     * subscribes are received. Events are emitted with `tryEmit` so a slow
     * collector never blocks the request pipeline.
     *
     * ## Concurrency
     * Multiple concurrent requests emit to the same flow. [NetworkEvent.requestId]
     * links related events.
     */
    public val events: SharedFlow<NetworkEvent> = _events.asSharedFlow()

    @PublishedApi
    internal fun tryEmitEvent(event: NetworkEvent): Unit {
        _events.tryEmit(event)
    }
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
    ): NetworkResponse =
        execute(request, deadline, generateRequestId())

    internal suspend fun execute(
        request: NetworkRequest,
        deadline: Instant? = null,
        requestId: String,
    ): NetworkResponse {
        val callState = coroutineContext[CallExecutionState]
        callState?.recordAttempt(1)
        val chain = RealChain(
            request = request,
            attempt = 1,
            deadline = deadline,
            interceptors = interceptors,
            index = 0,
            transport = transport,
            callState = callState,
            timeoutConfig = timeoutConfig,
            requestId = requestId,
            eventSink = ::tryEmitEvent,
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

    /**
     * Close resources owned by the terminal transport and any [CloseableInterceptor]s, if any.
     */
    public fun close(): Unit {
        (transport as? CloseableTransport)?.close()
        interceptors.forEach { (it as? CloseableInterceptor)?.close() }
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
    val requestForCall = request.withContentNegotiationAccept(contentNegotiation.acceptHeader)
    tryEmitEvent(NetworkEvent.CallStart(requestId, requestForCall))

    val unwrapper: ResponseUnwrapper? =
        (requestForCall.tags[CaterKtorKeys.UNWRAPPER] as? ResponseUnwrapper) ?: defaultUnwrapper

    val callState = CallExecutionState()
    return try {
        val response: NetworkResponse = withContext(callState) {
            execute(requestForCall, deadline, requestId)
        }
        val attempts = callState.attempts

        val durationMs = mark.elapsedNow().inWholeMilliseconds
        tryEmitEvent(NetworkEvent.ResponseReceived(requestId, response.status, response.headers, durationMs, attempts))

        if (response.status.isClientError || response.status.isServerError) {
            val raw = response.rawBody()
            val error = NetworkError.Http(
                status = response.status,
                headers = response.headers,
                body = ErrorBody(raw = raw, parsed = null),
            )
            tryEmitEvent(NetworkEvent.CallFailure(requestId, error, durationMs, attempts))
            NetworkResult.Failure(
                error = error,
                durationMs = durationMs,
                attempts = attempts,
                requestId = requestId,
            )
        } else {
            val result = decodeResponse<T>(response, responseType, durationMs, attempts, requestId, unwrapper)
            when (result) {
                is NetworkResult.Success ->
                    tryEmitEvent(NetworkEvent.CallSuccess(requestId, result.status, durationMs, result.attempts))
                is NetworkResult.Failure ->
                    tryEmitEvent(NetworkEvent.CallFailure(requestId, result.error, durationMs, result.attempts))
            }
            result
        }
    } catch (e: NetworkErrorException) {
        val attempts = callState.attempts
        val durationMs = mark.elapsedNow().inWholeMilliseconds
        tryEmitEvent(NetworkEvent.CallFailure(requestId, e.error, durationMs, attempts))
        NetworkResult.Failure(
            error = e.error,
            durationMs = durationMs,
            attempts = attempts,
            requestId = requestId,
        )
    }
}

@OptIn(ExperimentalCaterktor::class)
private fun <T : Any> NetworkClient.decodeResponse(
    response: NetworkResponse,
    responseType: KType,
    durationMs: Long,
    attempts: Int,
    requestId: String,
    unwrapper: ResponseUnwrapper? = null,
): NetworkResult<T> {
    // Unit type means caller doesn't want a body decoded
    if (responseType.classifier == Unit::class) {
        @Suppress("UNCHECKED_CAST")
        return NetworkResult.Success(
            body = Unit as T,
            status = response.status,
            headers = response.headers,
            durationMs = durationMs,
            attempts = attempts,
            requestId = requestId,
        )
    }
    val contentTypeHeader = response.headers["Content-Type"]
    val bareContentType = ContentNegotiationRegistry.bareContentType(contentTypeHeader) ?: ""
    val converter = contentNegotiation.converterFor(contentTypeHeader)
        ?: converters.firstOrNull { it.supports(bareContentType) }
        ?: return NetworkResult.Failure(
            error = NetworkError.Serialization(
                phase = SerializationPhase.Decoding,
                rawBody = response.rawBody(),
                cause = IllegalStateException(
                    "No BodyConverter registered for content-type '$bareContentType'. " +
                        "Register one via CaterKtorBuilder.addConverter().",
                ),
            ),
            durationMs = durationMs,
            attempts = attempts,
            requestId = requestId,
        )
    val contentLength = response.body.contentLength
    if (contentLength != null && contentLength > maxBodyDecodeBytes) {
        return NetworkResult.Failure(
            error = NetworkError.Serialization(
                phase = SerializationPhase.Decoding,
                rawBody = null,
                cause = IllegalStateException(
                    "Response body ($contentLength bytes) exceeds maxBodyDecodeBytes ($maxBodyDecodeBytes). " +
                        "Access the raw ResponseBody directly or increase the limit.",
                ),
            ),
            durationMs = durationMs,
            attempts = attempts,
            requestId = requestId,
        )
    }
    return try {
        val responseBody: ResponseBody = if (unwrapper != null) {
            unwrapper.unwrap(response.body, bareContentType.ifEmpty { null }, response)
        } else {
            response.body
        }
        val raw = responseBody.rawBody(contentTypeHeader)
        val body = converter.decode<T>(raw, responseType)
        NetworkResult.Success(
            body = body,
            status = response.status,
            headers = response.headers,
            durationMs = durationMs,
            attempts = attempts,
            requestId = requestId,
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        NetworkResult.Failure(
            error = NetworkError.Serialization(
                phase = SerializationPhase.Decoding,
                rawBody = response.rawBody(),
                cause = e,
            ),
            durationMs = durationMs,
            attempts = attempts,
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

private fun NetworkRequest.withContentNegotiationAccept(acceptHeader: String?): NetworkRequest {
    if (acceptHeader == null || "Accept" in headers) return this
    return copy(headers = headers + Headers { set("Accept", acceptHeader) })
}
