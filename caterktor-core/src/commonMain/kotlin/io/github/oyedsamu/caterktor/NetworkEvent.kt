package io.github.oyedsamu.caterktor

/**
 * An observable event emitted by [NetworkClient] for every request it
 * processes. Collect [NetworkClient.events] to receive them.
 *
 * Lifecycle events are emitted in order within a single request:
 * [CallStart] -> optional interceptor events -> [ResponseReceived] ->
 * [CallSuccess] or [CallFailure]. Byte-level progress events may appear while
 * a streaming request body is written or a streaming response body is read.
 * If the transport throws before a response is received (timeout, connection
 * failure), only [CallStart] and [CallFailure] are emitted.
 *
 * [requestId] links every event that belongs to the same logical request.
 *
 * Example:
 * ```
 * scope.launch {
 *     client.events.collect { event ->
 *         when (event) {
 *             is NetworkEvent.CallStart -> metrics.started(event.requestId)
 *             is NetworkEvent.UploadProgress ->
 *                 metrics.uploaded(event.requestId, event.bytesSent, event.totalBytes)
 *             is NetworkEvent.DownloadProgress ->
 *                 metrics.downloaded(event.requestId, event.bytesRead, event.totalBytes)
 *             is NetworkEvent.CallSuccess -> metrics.succeeded(event.requestId)
 *             is NetworkEvent.CallFailure -> metrics.failed(event.requestId, event.error)
 *             is NetworkEvent.ResponseReceived -> Unit
 *             is NetworkEvent.CircuitBreakerTransition -> Unit
 *         }
 *     }
 * }
 * ```
 *
 * ## Cancellation
 * Events are emitted with `tryEmit` — they are non-blocking and do not
 * suspend the request if no subscriber is listening. A slow collector does not
 * slow the pipeline; the [SharedFlow] is configured with a buffer and
 * [kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST].
 */
@ExperimentalCaterktor
public sealed interface NetworkEvent {

    /** Correlation ID linking all events for the same logical request. */
    public val requestId: String

    /**
     * The request entered [NetworkClient] and is about to traverse the
     * interceptor pipeline. Emitted before any interceptor runs.
     *
     * @property requestId Correlation ID linking all events for the same logical request.
     * @property request The outgoing request, as submitted to the pipeline.
     */
    public data class CallStart(
        override val requestId: String,
        public val request: NetworkRequest,
    ) : NetworkEvent

    /**
     * The transport returned a raw [NetworkResponse]. Emitted once per
     * logical call (after all retries from a [PrivilegedInterceptor] have
     * settled on a final response).
     *
     * @property requestId Correlation ID linking all events for the same logical request.
     * @property status The HTTP status code.
     * @property headers The response headers.
     * @property durationMs Wall-clock time from [CallStart] to this event.
     * @property attempts Total number of transport attempts (1 = no retries).
     */
    public data class ResponseReceived(
        override val requestId: String,
        public val status: HttpStatus,
        public val headers: Headers,
        public val durationMs: Long,
        public val attempts: Int,
    ) : NetworkEvent

    /**
     * The request succeeded: the response was a 2xx and the body decoded
     * without error. Emitted after [ResponseReceived].
     *
     * @property requestId Correlation ID linking all events for the same logical request.
     * @property status The HTTP status code.
     * @property durationMs Total wall-clock duration of the call.
     * @property attempts Total number of attempts.
     */
    public data class CallSuccess(
        override val requestId: String,
        public val status: HttpStatus,
        public val durationMs: Long,
        public val attempts: Int,
    ) : NetworkEvent

    /**
     * The request failed for any reason: HTTP error, transport error, or
     * body decode error. Emitted instead of [CallSuccess].
     *
     * @property requestId Correlation ID linking all events for the same logical request.
     * @property error The typed failure reason.
     * @property durationMs Total wall-clock duration up to the point of failure.
     * @property attempts Total number of attempts made.
     */
    public data class CallFailure(
        override val requestId: String,
        public val error: NetworkError,
        public val durationMs: Long,
        public val attempts: Int,
    ) : NetworkEvent

    /**
     * Bytes were written for a streaming request body.
     *
     * Emitted for source-backed uploads, including multipart file parts, when
     * the body source is consumed by the transport. [totalBytes] is `null` when
     * the request body length is unknown.
     *
     * Example:
     * ```
     * is NetworkEvent.UploadProgress -> {
     *     val percent = event.totalBytes?.let { (event.bytesSent * 100) / it }
     *     uploadUi.update(event.requestId, event.bytesSent, percent)
     * }
     * ```
     *
     * @property bytesSent Cumulative bytes written for this body.
     * @property totalBytes Total bytes expected, when known.
     */
    public data class UploadProgress(
        override val requestId: String,
        public val bytesSent: Long,
        public val totalBytes: Long?,
    ) : NetworkEvent

    /**
     * Bytes were read from a streaming response body.
     *
     * Emitted for [ResponseBody.Source] values as the caller consumes the
     * source. [totalBytes] is `null` when the response body length is unknown.
     *
     * Example:
     * ```
     * is NetworkEvent.DownloadProgress -> {
     *     val percent = event.totalBytes?.let { (event.bytesRead * 100) / it }
     *     downloadUi.update(event.requestId, event.bytesRead, percent)
     * }
     * ```
     *
     * @property bytesRead Cumulative bytes read from this response body.
     * @property totalBytes Total bytes expected, when known.
     */
    public data class DownloadProgress(
        override val requestId: String,
        public val bytesRead: Long,
        public val totalBytes: Long?,
    ) : NetworkEvent

    /**
     * A circuit breaker changed state while processing this request.
     *
     * @property name Human-readable breaker name.
     * @property from Previous state.
     * @property to New state.
     */
    public data class CircuitBreakerTransition(
        override val requestId: String,
        public val name: String,
        public val from: CircuitBreakerState,
        public val to: CircuitBreakerState,
    ) : NetworkEvent
}
