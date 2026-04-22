@file:OptIn(ExperimentalCaterktor::class)

package io.github.oyedsamu.caterktor

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.io.IOException
import kotlinx.io.Buffer
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
        assertContentEquals(payload, response.bodyBytes)
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
    fun bytes_body_is_sent_with_content_type_header() = runTest {
        var seenBody: ByteArray? = null
        var seenContentType: String? = null
        val engine = MockEngine { request ->
            seenBody = request.body.toByteArray()
            seenContentType = request.body.contentType?.toString()
                ?: request.headers["Content-Type"]
            respond(content = byteArrayOf(), status = HttpStatusCode.Created)
        }
        val transport = KtorTransport(HttpClient(engine))

        val payload = """{"x":1}""".encodeToByteArray()
        val response = transport.execute(
            NetworkRequest(
                method = HttpMethod.POST,
                url = "https://example.test/",
                body = RequestBody.Bytes(payload, "application/json"),
            ),
        )

        assertEquals(HttpStatus.Created, response.status)
        assertContentEquals(payload, seenBody)
        assertTrue(
            seenContentType.orEmpty().contains("application/json"),
            "expected Content-Type to contain application/json, got: $seenContentType",
        )
    }

    @Test
    fun bytes_body_content_type_overrides_conflicting_header() = runTest {
        var seenBodyContentType: String? = null
        var seenContentTypeHeaders: List<String> = emptyList()
        val engine = MockEngine { request ->
            seenBodyContentType = request.body.contentType?.toString()
            seenContentTypeHeaders = request.headers.getAll("Content-Type") ?: emptyList()
            respond(content = byteArrayOf(), status = HttpStatusCode.Created)
        }
        val transport = KtorTransport(HttpClient(engine))

        transport.execute(
            NetworkRequest(
                method = HttpMethod.POST,
                url = "https://example.test/",
                headers = Headers { set("Content-Type", "text/plain") },
                body = RequestBody.Bytes("""{"x":1}""".encodeToByteArray(), "application/json"),
            ),
        )

        assertTrue(
            seenBodyContentType.orEmpty().contains("application/json"),
            "expected body Content-Type to contain application/json, got: $seenBodyContentType",
        )
        assertTrue(
            "text/plain" !in seenContentTypeHeaders,
            "conflicting Content-Type header should not be forwarded: $seenContentTypeHeaders",
        )
    }

    @Test
    fun source_body_is_sent_through_streaming_content() = runTest {
        var seenBody: ByteArray? = null
        var seenContentType: String? = null
        var seenContentLength: Long? = null
        val engine = MockEngine { request ->
            seenBody = request.body.toByteArray()
            seenContentType = request.body.contentType?.toString()
            seenContentLength = request.body.contentLength
            respond(content = byteArrayOf(), status = HttpStatusCode.Created)
        }
        val transport = KtorTransport(HttpClient(engine))

        val payload = ByteArray(128) { it.toByte() }
        val response = transport.execute(
            NetworkRequest(
                method = HttpMethod.POST,
                url = "https://example.test/upload",
                body = RequestBody.Source(
                    sourceFactory = { Buffer().also { it.write(payload) } },
                    contentType = "application/octet-stream",
                    contentLength = payload.size.toLong(),
                ),
            ),
        )

        assertEquals(HttpStatus.Created, response.status)
        assertContentEquals(payload, seenBody)
        assertTrue(
            seenContentType.orEmpty().contains("application/octet-stream"),
            "expected Content-Type to contain application/octet-stream, got: $seenContentType",
        )
        assertEquals(payload.size.toLong(), seenContentLength)
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
        assertContentEquals("boom".encodeToByteArray(), response.bodyBytes)
    }
}
