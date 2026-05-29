package io.github.oyedsamu.caterktor.logging

import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import io.github.oyedsamu.caterktor.NetworkEvent

/**
 * Event-derived logger for [io.github.oyedsamu.caterktor.NetworkClient.events].
 *
 * This logger is intentionally not an interceptor. Collect events from a client
 * and pass them to [log] to keep observability independent from pipeline order.
 *
 * Example:
 * ```
 * val eventLogger = NetworkEventLogger { line -> println(line) }
 *
 * scope.launch {
 *     client.events.collect(eventLogger::log)
 * }
 * ```
 *
 * Progress events are formatted as single-line records, for example:
 * ```
 * event upload_progress requestId=abc bytesSent=4096 totalBytes=8192
 * event download_progress requestId=abc bytesRead=4096 totalBytes=unknown
 * ```
 */
@ExperimentalCaterktor
public class NetworkEventLogger(
    public val logger: (String) -> Unit,
) {
    public fun log(event: NetworkEvent) {
        logger(event.format())
    }
}

@OptIn(ExperimentalCaterktor::class)
private fun NetworkEvent.format(): String =
    when (this) {
        is NetworkEvent.CallStart ->
            "event request_start requestId=$requestId method=${request.method.name} url=${request.url}"

        is NetworkEvent.ResponseReceived ->
            "event response_received requestId=$requestId status=${status.code} durationMs=$durationMs attempts=$attempts"

        is NetworkEvent.CallSuccess ->
            "event request_success requestId=$requestId status=${status.code} durationMs=$durationMs attempts=$attempts"

        is NetworkEvent.CallFailure ->
            "event request_failure requestId=$requestId error=${error::class.simpleName ?: "NetworkError"} durationMs=$durationMs attempts=$attempts"

        is NetworkEvent.UploadProgress ->
            "event upload_progress requestId=$requestId bytesSent=$bytesSent totalBytes=${totalBytes ?: "unknown"}"

        is NetworkEvent.DownloadProgress ->
            "event download_progress requestId=$requestId bytesRead=$bytesRead totalBytes=${totalBytes ?: "unknown"}"

        is NetworkEvent.CircuitBreakerTransition ->
            "event circuit_breaker_transition requestId=$requestId name=$name from=$from to=$to"
    }
