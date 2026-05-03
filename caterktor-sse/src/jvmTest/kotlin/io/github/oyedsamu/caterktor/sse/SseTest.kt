@file:OptIn(io.github.oyedsamu.caterktor.ExperimentalCaterktor::class)

package io.github.oyedsamu.caterktor.sse

import io.github.oyedsamu.caterktor.Headers
import io.github.oyedsamu.caterktor.KtorTransport
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.server.application.install
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE as ServerSSE
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Integration tests for [KtorTransport.sse]. Uses an embedded Ktor/CIO server
 * to exercise real SSE traffic within the test JVM process.
 */
class SseTest {

    @Test
    fun receivesTextEvents() = runBlocking {
        val server = embeddedServer(ServerCIO, port = 0) {
            install(ServerSSE)
            routing {
                sse("/sse") {
                    for (i in 1..3) {
                        send(ServerSentEvent(data = "msg$i"))
                    }
                }
            }
        }.start(wait = false)

        val port = server.engine.resolvedConnectors().first().port
        val url = "http://127.0.0.1:$port/sse"
        val transport = KtorTransport(HttpClient(CIO))
        try {
            val events = transport.sse(url).take(3).toList()
            assertEquals(3, events.size)
            assertEquals("msg1", events[0].data)
            assertEquals("msg2", events[1].data)
            assertEquals("msg3", events[2].data)
        } finally {
            transport.close()
            server.stop(0L, 0L)
        }
    }

    @Test
    fun customHeadersReachServer() = runBlocking {
        val server = embeddedServer(ServerCIO, port = 0) {
            install(ServerSSE)
            routing {
                sse("/sse") {
                    val headerValue = call.request.headers["X-Custom-Header"] ?: "missing"
                    send(ServerSentEvent(data = headerValue))
                }
            }
        }.start(wait = false)

        val port = server.engine.resolvedConnectors().first().port
        val url = "http://127.0.0.1:$port/sse"
        val transport = KtorTransport(HttpClient(CIO))
        try {
            val events = transport.sse(
                url = url,
                headers = Headers { set("X-Custom-Header", "test-value") },
            ).take(1).toList()
            assertEquals(1, events.size)
            assertEquals("test-value", events[0].data)
        } finally {
            transport.close()
            server.stop(0L, 0L)
        }
    }

    @Test
    fun eventTypeAndIdArePreserved() = runBlocking {
        val server = embeddedServer(ServerCIO, port = 0) {
            install(ServerSSE)
            routing {
                sse("/sse") {
                    send(ServerSentEvent(data = "d", event = "ping", id = "1"))
                }
            }
        }.start(wait = false)

        val port = server.engine.resolvedConnectors().first().port
        val url = "http://127.0.0.1:$port/sse"
        val transport = KtorTransport(HttpClient(CIO))
        try {
            val events = transport.sse(url).take(1).toList()
            assertEquals(1, events.size)
            assertEquals("ping", events[0].event)
            assertEquals("1", events[0].id)
        } finally {
            transport.close()
            server.stop(0L, 0L)
        }
    }
}
