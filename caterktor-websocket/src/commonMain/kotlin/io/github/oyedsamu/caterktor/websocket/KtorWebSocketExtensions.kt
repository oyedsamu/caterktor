package io.github.oyedsamu.caterktor.websocket

import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import io.github.oyedsamu.caterktor.Headers
import io.github.oyedsamu.caterktor.KtorTransport
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Open a WebSocket connection using this transport's underlying [io.ktor.client.HttpClient].
 *
 * The [WebSockets] Ktor plugin is installed automatically on a derived client
 * so the caller does not need to install it manually. The caller's [block]
 * runs inside the session; the connection is closed when [block] returns.
 *
 * ## Auth headers
 *
 * Pass [headers] to inject auth or other per-connection headers. For CaterKtor
 * auth-interceptor integration, derive the headers from a prior token exchange
 * or supply them directly.
 *
 * ## Single-threaded dispatchers (iOS)
 *
 * Ktor's Darwin WebSocket engine is non-blocking and safe to call from any
 * dispatcher. The [WebSocketSession.incoming] flow delivers frames on the
 * calling coroutine's dispatcher.
 */
@ExperimentalCaterktor
public suspend fun <T> KtorTransport.webSocket(
    url: String,
    headers: Headers = Headers.Empty,
    block: suspend WebSocketSession.() -> T,
): T {
    val wsClient = httpClient.config { install(WebSockets) }
    val requestHeaders = headers
    val rawSession = wsClient.webSocketSession(urlString = url) {
        for (name in requestHeaders.names) {
            for (value in requestHeaders.getAll(name)) {
                this.headers.append(name, value)
            }
        }
    }
    return try {
        block(KtorWebSocketSessionWrapper(rawSession))
    } finally {
        rawSession.close(CloseReason(CloseReason.Codes.NORMAL, ""))
    }
}

@OptIn(ExperimentalCaterktor::class)
private class KtorWebSocketSessionWrapper(
    private val session: DefaultClientWebSocketSession,
) : WebSocketSession {

    override val incoming: Flow<WebSocketFrame> = session.incoming
        .receiveAsFlow()
        .mapNotNull { frame ->
            @Suppress("REDUNDANT_ELSE_IN_WHEN")
            when (frame) {
                is Frame.Text -> WebSocketFrame.Text(frame.readText())
                is Frame.Binary -> WebSocketFrame.Binary(frame.readBytes())
                is Frame.Ping -> WebSocketFrame.Ping
                is Frame.Pong -> WebSocketFrame.Pong
                is Frame.Close -> {
                    val reason = frame.readReason()
                    WebSocketFrame.Close(
                        code = reason?.code ?: 1000,
                        reason = reason?.message ?: "",
                    )
                }
                else -> null
            }
        }

    override suspend fun send(frame: WebSocketFrame) {
        when (frame) {
            is WebSocketFrame.Text -> session.send(Frame.Text(frame.text))
            is WebSocketFrame.Binary -> session.send(Frame.Binary(true, frame.data))
            is WebSocketFrame.Ping -> session.send(Frame.Ping(byteArrayOf()))
            is WebSocketFrame.Pong -> session.send(Frame.Pong(byteArrayOf()))
            is WebSocketFrame.Close -> session.close(CloseReason(frame.code, frame.reason))
        }
    }

    override suspend fun close(code: Short, reason: String) {
        session.close(CloseReason(code, reason))
    }
}
