package io.github.oyedsamu.caterktor.testing

import io.github.oyedsamu.caterktor.CaterKtor
import io.github.oyedsamu.caterktor.CaterKtorBuilder
import io.github.oyedsamu.caterktor.CloseableTransport
import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import io.github.oyedsamu.caterktor.HttpMethod
import io.github.oyedsamu.caterktor.HttpStatus
import io.github.oyedsamu.caterktor.NetworkClient
import io.github.oyedsamu.caterktor.NetworkRequest
import io.github.oyedsamu.caterktor.NetworkResponse

/**
 * In-memory test server for clients that do not need a real socket.
 */
@ExperimentalCaterktor
public class CaterktorTestServer(
    baseUrl: String = "https://caterktor.test",
) : CloseableTransport {

    public val baseUrl: String = baseUrl.trimEnd('/')
    private val transport = FakeTransport(defaultResponse = testResponse(HttpStatus.NotFound))
    private var closed: Boolean = false

    public val requests: List<NetworkRequest>
        get() = transport.requests

    public fun client(configure: CaterKtorBuilder.() -> Unit = {}): NetworkClient =
        CaterKtor {
            baseUrl = this@CaterktorTestServer.baseUrl
            transport = this@CaterktorTestServer
            configure()
        }

    public fun enqueue(response: NetworkResponse): CaterktorTestServer = apply {
        transport.enqueue(response)
    }

    public fun routes(configure: FakeTransportDsl.() -> Unit): CaterktorTestServer = apply {
        transport.rules(configure = configure, failOnUnmatchedRequests = false)
    }

    public fun route(
        method: HttpMethod,
        path: String,
        response: NetworkResponse,
    ): CaterktorTestServer = apply {
        transport.addRule(
            method = method,
            pathTemplate = path,
            result = FakeTransport.ScriptedResult.Response(response),
            failOnUnmatchedRequests = false,
        )
    }

    public fun clearRequests(): CaterktorTestServer = apply {
        transport.clearRequests()
    }

    override suspend fun execute(request: NetworkRequest): NetworkResponse {
        if (closed) {
            throw IllegalStateException("CaterktorTestServer is closed")
        }
        return transport.execute(request)
    }

    override fun close() {
        closed = true
        transport.close()
    }
}
