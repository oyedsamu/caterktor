@file:OptIn(ExperimentalCaterktor::class)

package io.github.oyedsamu.caterktor.testing

import io.github.oyedsamu.caterktor.CaterKtor
import io.github.oyedsamu.caterktor.CaterKtorBuilder
import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import io.github.oyedsamu.caterktor.Headers
import io.github.oyedsamu.caterktor.HttpMethod
import io.github.oyedsamu.caterktor.HttpStatus
import io.github.oyedsamu.caterktor.KtorTransport
import io.github.oyedsamu.caterktor.NetworkClient
import io.github.oyedsamu.caterktor.NetworkRequest
import io.github.oyedsamu.caterktor.NetworkResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking

/**
 * A JVM-only real-TCP test server for integration-level tests.
 *
 * Binds an OS-assigned ephemeral port on loopback. [baseUrl] is available
 * immediately after construction. Routing and response scripting delegate to
 * [FakeTransport] internally, so the same [route], [routes], and [enqueue]
 * API that [CaterktorTestServer] exposes is available here — but backed by a
 * real socket rather than in-memory dispatch.
 *
 * ```kotlin
 * CaterktorHttpServer().use { server ->
 *     server.route(HttpMethod.GET, "/ping", jsonResponse("""{"ok":true}"""))
 *     val response = server.client().execute(
 *         NetworkRequest(HttpMethod.GET, "${server.baseUrl}/ping"),
 *     )
 *     response.assertThat { hasStatus(HttpStatus.OK) }
 * }
 * ```
 */
@ExperimentalCaterktor
public class CaterktorHttpServer : AutoCloseable {

    private val transport = FakeTransport(defaultResponse = testResponse(HttpStatus(500)))

    private val server: EmbeddedServer<*, *> = embeddedServer(CIO, port = 0) {
        routing {
            route("{...}") {
                handle {
                    val networkRequest = call.toNetworkRequest()
                    val networkResponse = transport.execute(networkRequest)
                    call.response.status(HttpStatusCode.fromValue(networkResponse.status.code))
                    for (name in networkResponse.headers.names) {
                        for (value in networkResponse.headers.getAll(name)) {
                            call.response.headers.append(name, value, safeOnly = false)
                        }
                    }
                    call.respondBytes(networkResponse.body.bytes())
                }
            }
        }
    }.start(wait = false)

    /** The base URL of the server, e.g. `http://127.0.0.1:54321`. */
    public val baseUrl: String

    init {
        val port = runBlocking { server.engine.resolvedConnectors().first().port }
        baseUrl = "http://127.0.0.1:$port"
    }

    /** All requests received so far, in arrival order. Thread-safe snapshot. */
    public val requests: List<NetworkRequest>
        get() = transport.requests

    /**
     * Enqueue a scripted [NetworkResponse] to be served FIFO.
     *
     * Queued responses are consumed before route matches.
     */
    public fun enqueue(response: NetworkResponse): CaterktorHttpServer = apply {
        transport.enqueue(response)
    }

    /** Convenience overload that builds a [NetworkResponse] from parts. */
    public fun enqueue(
        status: HttpStatus,
        headers: Headers = Headers.Empty,
        body: ByteArray = byteArrayOf(),
    ): CaterktorHttpServer = enqueue(testResponse(status = status, headers = headers, body = body))

    /**
     * Register a static route. First-match wins.
     *
     * Path templates like `/users/{id}` are supported.
     */
    public fun route(
        method: HttpMethod,
        path: String,
        response: NetworkResponse,
    ): CaterktorHttpServer = apply {
        transport.addRule(
            method = method,
            pathTemplate = path,
            result = FakeTransport.ScriptedResult.Response(response),
            failOnUnmatchedRequests = false,
        )
    }

    /**
     * Register multiple routes via the [FakeTransportDsl].
     */
    public fun routes(configure: FakeTransportDsl.() -> Unit): CaterktorHttpServer = apply {
        transport.rules(configure = configure, failOnUnmatchedRequests = false)
    }

    /**
     * Build a [NetworkClient] pointed at this server using a CIO transport.
     */
    public fun client(configure: CaterKtorBuilder.() -> Unit = {}): NetworkClient =
        CaterKtor {
            baseUrl = this@CaterktorHttpServer.baseUrl
            transport = KtorTransport(HttpClient(ClientCIO), ownsHttpClient = true)
            configure()
        }

    /** Clear the recorded request list. Does not affect the queue or routes. */
    public fun clearRequests(): CaterktorHttpServer = apply {
        transport.clearRequests()
    }

    /** Stop the embedded server and release all resources. */
    override fun close() {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 500)
    }
}

private fun ApplicationCall.toNetworkRequest(): NetworkRequest {
    val method = when (request.httpMethod) {
        io.ktor.http.HttpMethod.Get -> HttpMethod.GET
        io.ktor.http.HttpMethod.Head -> HttpMethod.HEAD
        io.ktor.http.HttpMethod.Post -> HttpMethod.POST
        io.ktor.http.HttpMethod.Put -> HttpMethod.PUT
        io.ktor.http.HttpMethod.Patch -> HttpMethod.PATCH
        io.ktor.http.HttpMethod.Delete -> HttpMethod.DELETE
        io.ktor.http.HttpMethod.Options -> HttpMethod.OPTIONS
        else -> throw IllegalArgumentException("Unsupported HTTP method: ${request.httpMethod.value}")
    }
    val headers = Headers {
        for ((name, values) in request.headers.entries()) {
            for (value in values) {
                add(name, value)
            }
        }
    }
    return NetworkRequest(method = method, url = request.uri, headers = headers)
}
