package io.github.oyedsamu.caterktor.logging

import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import io.github.oyedsamu.caterktor.NetworkEvent

/**
 * Event-derived logger for [io.github.oyedsamu.caterktor.NetworkClient.events].
 *
 * This logger is intentionally not an interceptor. Collect events from a client
 * and pass them to [log] to keep observability independent from pipeline order.
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

        is NetworkEvent.CircuitBreakerTransition ->
            "event circuit_breaker_transition requestId=$requestId name=$name from=$from to=$to"
    }
