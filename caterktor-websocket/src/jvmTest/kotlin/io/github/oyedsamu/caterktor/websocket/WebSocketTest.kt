@file:OptIn(io.github.oyedsamu.caterktor.ExperimentalCaterktor::class)

package io.github.oyedsamu.caterktor.websocket

import io.github.oyedsamu.caterktor.Headers
import io.github.oyedsamu.caterktor.KtorTransport
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.server.application.install
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets as ServerWebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds
import java.util.concurrent.atomic.AtomicReference

/**
 * Integration tests for [KtorTransport.webSocket]. Uses an embedded Ktor/CIO
 * server to exercise real WebSocket traffic within the test JVM process.
 */
class WebSocketTest {

    @Test
    fun sendAndReceiveTextFrame() = runTest(timeout = 10.seconds) {
        val server = embeddedServer(ServerCIO, port = 0) {
            install(ServerWebSockets)
            routing {
                webSocket("/echo") {
                    for (frame in incoming) {
                        if (frame is Frame.Text) send(Frame.Text(frame.readText()))
                    }
                }
            }
        }.start(wait = false)

        val port = server.engine.resolvedConnectors().first().port
        val transport = KtorTransport(HttpClient(CIO))
        try {
            transport.webSocket("ws://127.0.0.1:$port/echo") {
                send(WebSocketFrame.Text("hello"))
                val received = incoming.first()
                assertIs<WebSocketFrame.Text>(received)
                assertEquals("hello", received.text)
            }
        } finally {
            transport.close()
            server.stop(0L, 0L)
        }
    }

    @Test
    fun customHeadersAreSentToServer() = runTest(timeout = 10.seconds) {
        val capturedAuth = AtomicReference<String?>(null)

        val server = embeddedServer(ServerCIO, port = 0) {
            install(ServerWebSockets)
            routing {
                webSocket("/headers") {
                    capturedAuth.set(call.request.headers["Authorization"])
                    // Echo one message so the client can complete cleanly
                    send(Frame.Text("ok"))
                    for (frame in incoming) { /* drain */ }
                }
            }
        }.start(wait = false)

        val port = server.engine.resolvedConnectors().first().port
        val transport = KtorTransport(HttpClient(CIO))
        try {
            transport.webSocket(
                url = "ws://127.0.0.1:$port/headers",
                headers = Headers {
                    set("Authorization", "Bearer token")
                },
            ) {
                // Wait for the server's acknowledgment frame
                incoming.first()
            }
        } finally {
            transport.close()
            server.stop(0L, 0L)
        }

        assertEquals("Bearer token", capturedAuth.get())
    }

    @Test
    fun closeFrameTerminatesIncomingFlow() = runTest(timeout = 10.seconds) {
        val server = embeddedServer(ServerCIO, port = 0) {
            install(ServerWebSockets)
            routing {
                webSocket("/close-after-one") {
                    send(Frame.Text("only"))
                    close()
                }
            }
        }.start(wait = false)

        val port = server.engine.resolvedConnectors().first().port
        val transport = KtorTransport(HttpClient(CIO))
        try {
            transport.webSocket("ws://127.0.0.1:$port/close-after-one") {
                // Collect only text frames; the flow completes when server closes
                val frames = incoming
                    .take(1)
                    .toList()
                assertEquals(1, frames.size)
                assertIs<WebSocketFrame.Text>(frames[0])
                assertEquals("only", (frames[0] as WebSocketFrame.Text).text)
            }
        } finally {
            transport.close()
            server.stop(0L, 0L)
        }
    }
}
