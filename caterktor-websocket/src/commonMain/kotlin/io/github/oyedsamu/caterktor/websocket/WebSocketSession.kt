package io.github.oyedsamu.caterktor.websocket

import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import kotlinx.coroutines.flow.Flow

/**
 * A live WebSocket session.
 *
 * [incoming] is a hot [Flow] that emits frames as they arrive. Cancelling
 * the session's coroutine scope or calling [close] terminates the flow.
 */
@ExperimentalCaterktor
public interface WebSocketSession {
    /**
     * A hot [Flow] that emits incoming [WebSocketFrame]s as they arrive from
     * the remote peer. The flow completes when the connection is closed.
     */
    public val incoming: Flow<WebSocketFrame>

    /** Send a [WebSocketFrame] to the remote peer. */
    public suspend fun send(frame: WebSocketFrame)

    /**
     * Close the connection with the given [code] and [reason].
     * The default code `1000` signals a normal closure.
     */
    public suspend fun close(code: Short = 1000, reason: String = "")
}
