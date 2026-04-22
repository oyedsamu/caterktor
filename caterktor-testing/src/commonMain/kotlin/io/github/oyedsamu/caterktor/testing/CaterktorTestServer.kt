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
    private val fallback = FakeTransport(defaultResponse = testResponse(HttpStatus.NotFound))
    private val routes: MutableList<Route> = mutableListOf()
    private val recordedRequests: MutableList<NetworkRequest> = mutableListOf()
    private var closed: Boolean = false

    public val requests: List<NetworkRequest>
        get() = recordedRequests.toList()

    public fun client(configure: CaterKtorBuilder.() -> Unit = {}): NetworkClient =
        CaterKtor {
            baseUrl = this@CaterktorTestServer.baseUrl
            transport = this@CaterktorTestServer
            configure()
        }

    public fun enqueue(response: NetworkResponse): CaterktorTestServer = apply {
        fallback.enqueue(response)
    }

    public fun route(
        method: HttpMethod,
        path: String,
        response: NetworkResponse,
    ): CaterktorTestServer = apply {
        routes += Route(method = method, path = path.normalizePath(), response = response)
    }

    public fun clearRequests(): CaterktorTestServer = apply {
        recordedRequests.clear()
        fallback.clearRequests()
    }

    override suspend fun execute(request: NetworkRequest): NetworkResponse {
        if (closed) {
            throw IllegalStateException("CaterktorTestServer is closed")
        }
        recordedRequests += request
        val route = routes.firstOrNull { it.method == request.method && it.path == request.pathRelativeTo(baseUrl) }
        return route?.response ?: fallback.execute(request)
    }

    override fun close() {
        closed = true
        fallback.close()
    }

    private data class Route(
        val method: HttpMethod,
        val path: String,
        val response: NetworkResponse,
    )
}

private fun String.normalizePath(): String {
    val withoutQuery = substringBefore('?')
    val path = if (withoutQuery.startsWith('/')) withoutQuery else "/$withoutQuery"
    return path.ifEmpty { "/" }
}

@OptIn(ExperimentalCaterktor::class)
private fun NetworkRequest.pathRelativeTo(baseUrl: String): String {
    val withoutBase = if (url.startsWith(baseUrl)) url.removePrefix(baseUrl) else url
    return withoutBase.normalizePath()
}
