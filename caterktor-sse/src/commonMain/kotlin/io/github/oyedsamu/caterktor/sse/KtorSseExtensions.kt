package io.github.oyedsamu.caterktor.sse

import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import io.github.oyedsamu.caterktor.Headers
import io.github.oyedsamu.caterktor.KtorTransport
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.sse.sse
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Opens an SSE (Server-Sent Events) connection using this transport's underlying
 * [io.ktor.client.HttpClient] and returns a [Flow] of [SseEvent].
 *
 * The [SSE] Ktor plugin is installed automatically on a derived client so the
 * caller does not need to install it manually. The flow completes when the server
 * closes the connection.
 *
 * ## Auth headers
 *
 * Pass [headers] to inject auth or other per-connection headers.
 *
 * @param url The SSE endpoint URL.
 * @param headers Optional headers to attach to the SSE request.
 * @return A [Flow] that emits each [SseEvent] received from the server.
 */
@ExperimentalCaterktor
public fun KtorTransport.sse(
    url: String,
    headers: Headers = Headers.Empty,
): Flow<SseEvent> = callbackFlow {
    val sseClient = httpClient.config { install(SSE) }
    sseClient.sse(
        urlString = url,
        request = {
            for (name in headers.names) {
                for (value in headers.getAll(name)) {
                    this.headers.append(name, value)
                }
            }
        },
    ) {
        incoming.collect { event ->
            send(
                SseEvent(
                    data = event.data ?: "",
                    event = event.event,
                    id = event.id,
                    retry = event.retry,
                    comments = event.comments,
                )
            )
        }
    }
    close()
    awaitClose()
}
