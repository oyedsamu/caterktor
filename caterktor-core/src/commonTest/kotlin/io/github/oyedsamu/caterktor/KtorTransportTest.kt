@file:OptIn(ExperimentalCaterktor::class)

package io.github.oyedsamu.caterktor

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.io.IOException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for [KtorTransport]. Uses Ktor's [MockEngine] to deterministically
 * exercise success, HTTP-error, and exception paths.
 *
 * Note: [kotlinx.coroutines.CancellationException] is intentionally NOT
 * tested here by throwing from the engine — the contract is that the mapper
 * re-throws it verbatim. That behavior is enforced by structured concurrency
 * tests in [InterceptorChainTest] and is asserted implicitly by the
 * [mapKtorErrors] implementation.
 */
class KtorTransportTest {

    @Test
    fun successful_get_returns_status_headers_and_body() = runTest {
        val payload = "hello".encodeToByteArray()
        val engine = MockEngine { request ->
            assertEquals("https://example.test/ping", request.url.toString())
            respond(
                content = payload,
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "text/plain"),
            )
        }
        val transport = KtorTransport(HttpClient(engine))

        val response = transport.execute(
            NetworkRequest(method = HttpMethod.GET, url = "https://example.test/ping"),
        )

        assertEquals(HttpStatus.OK, response.status)
        assertContentEquals(payload, response.body)
        assertEquals("text/plain", response.headers["content-type"])
    }

    @Test
    fun forwards_request_headers_to_engine() = runTest {
        var seenAuth: String? = null
        var seenAccepts: List<String> = emptyList()
        val engine = MockEngine { request ->
            seenAuth = request.headers["Authorization"]
            seenAccepts = request.headers.getAll("Accept") ?: emptyList()
            respond(content = byteArrayOf(), status = HttpStatusCode.NoContent)
        }
        val transport = KtorTransport(HttpClient(engine))

        transport.execute(
            NetworkRequest(
                method = HttpMethod.GET,
                url = "https://example.test/",
                headers = Headers {
                    set("Authorization", "Bearer xyz")
                    add("Accept", "application/json")
                    add("Accept", "text/plain")
                },
            ),
        )

        assertEquals("Bearer xyz", seenAuth)
        assertEquals(listOf("application/json", "text/plain"), seenAccepts)
    }

    @Test
    fun post_with_null_body_succeeds() = runTest {
        val engine = MockEngine { _ ->
            respond(content = byteArrayOf(), status = HttpStatusCode.Created)
        }
        val transport = KtorTransport(HttpClient(engine))

        val response = transport.execute(
            NetworkRequest(method = HttpMethod.POST, url = "https://example.test/create"),
        )

        assertEquals(HttpStatus.Created, response.status)
    }

    @Test
    fun non_null_body_throws_unsupported_operation_exception() = runTest {
        val engine = MockEngine { _ ->
            respond(content = byteArrayOf(), status = HttpStatusCode.OK)
        }
        val transport = KtorTransport(HttpClient(engine))

        val stubBody = UnsupportedRequestBody

        val thrown = assertFailsWith<NetworkErrorException> {
            transport.execute(
                NetworkRequest(
                    method = HttpMethod.POST,
                    url = "https://example.test/",
                    body = stubBody,
                ),
            )
        }
        val cause = thrown.cause
        assertIs<UnsupportedOperationException>(cause)
        assertTrue(cause.message.orEmpty().contains("non-null RequestBody"))
    }

    @Test
    fun io_exception_maps_to_connection_failed() = runTest {
        val engine = MockEngine { _ ->
            throw IOException("network unreachable")
        }
        val transport = KtorTransport(HttpClient(engine))

        val thrown = assertFailsWith<NetworkErrorException> {
            transport.execute(
                NetworkRequest(method = HttpMethod.GET, url = "https://example.test/"),
            )
        }
        val failure = assertIs<NetworkError.ConnectionFailed>(thrown.error)
        assertEquals(ConnectionFailureKind.Unreachable, failure.kind)
        assertIs<IOException>(failure.cause)
    }

    @Test
    fun http_error_status_surfaces_as_response_not_exception() = runTest {
        val engine = MockEngine { _ ->
            respond(
                content = "boom".encodeToByteArray(),
                status = HttpStatusCode.InternalServerError,
            )
        }
        val transport = KtorTransport(HttpClient(engine))

        val response = transport.execute(
            NetworkRequest(method = HttpMethod.GET, url = "https://example.test/"),
        )

        assertEquals(HttpStatus.InternalServerError, response.status)
        assertContentEquals("boom".encodeToByteArray(), response.body)
    }
}
