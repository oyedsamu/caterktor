package io.github.oyedsamu.caterktor.websocket

import io.github.oyedsamu.caterktor.ExperimentalCaterktor

/**
 * A WebSocket frame that can be sent or received over a [WebSocketSession].
 */
@ExperimentalCaterktor
public sealed interface WebSocketFrame {
    /** A UTF-8 text frame. */
    public data class Text(val text: String) : WebSocketFrame

    /** A binary frame. */
    public data class Binary(val data: ByteArray) : WebSocketFrame {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Binary) return false
            return data.contentEquals(other.data)
        }

        override fun hashCode(): Int = data.contentHashCode()
    }

    /** A ping frame. */
    public data object Ping : WebSocketFrame

    /** A pong frame. */
    public data object Pong : WebSocketFrame

    /** A close frame with an optional status code and reason. */
    public data class Close(val code: Short = 1000, val reason: String = "") : WebSocketFrame
}
