@file:OptIn(ExperimentalCaterktor::class)

package io.github.oyedsamu.caterktor.testing

import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import io.github.oyedsamu.caterktor.Headers
import io.github.oyedsamu.caterktor.HttpMethod
import io.github.oyedsamu.caterktor.HttpStatus
import io.github.oyedsamu.caterktor.NetworkRequest
import io.github.oyedsamu.caterktor.NetworkResponse
import io.github.oyedsamu.caterktor.RetryInterceptor
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CaterktorHttpServerTest {

    // ── 1. Basic route match ───────────────────────────────────────────────────

    @Test
    fun basicRouteMatch() = runTest {
        CaterktorHttpServer().use { server ->
            server.route(
                method = HttpMethod.GET,
                path = "/users",
                response = jsonResponse("""{"users":[]}"""),
            )
            val client = server.client()

            val response = client.execute(
                NetworkRequest(HttpMethod.GET, "${server.baseUrl}/users"),
            )

            response.assertThat {
                hasStatus(HttpStatus.OK)
                hasHeader("Content-Type", "application/json; charset=UTF-8")
                hasBodyText("""{"users":[]}""")
            }
        }
    }

    // ── 2. Queue-based response ────────────────────────────────────────────────

    @Test
    fun queueBasedResponse() = runTest {
        CaterktorHttpServer().use { server ->
            server.enqueue(HttpStatus.NotFound)
            val client = server.client()

            val response = client.execute(
                NetworkRequest(HttpMethod.GET, "${server.baseUrl}/missing"),
            )

            response.assertThat {
                hasStatus(HttpStatus.NotFound)
            }
        }
    }

    // ── 3. Request recording ───────────────────────────────────────────────────

    @Test
    fun requestRecording() = runTest {
        CaterktorHttpServer().use { server ->
            server.enqueue(HttpStatus.OK)
            val client = server.client()

            client.execute(
                NetworkRequest(
                    method = HttpMethod.POST,
                    url = "${server.baseUrl}/items",
                    headers = Headers { set("X-Custom", "test-value") },
                ),
            )

            assertEquals(1, server.requests.size)
            val recorded = server.requests.single()
            assertEquals(HttpMethod.POST, recorded.method)
            assertTrue(recorded.url.contains("/items"), "URL should contain path: ${recorded.url}")
        }
    }

    // ── 4. Chunked / large body ────────────────────────────────────────────────

    @Test
    fun largeBodyIsTransferredFully() = runTest {
        val twoMb = ByteArray(2 * 1024 * 1024) { it.toByte() }
        CaterktorHttpServer().use { server ->
            server.enqueue(
                NetworkResponse(
                    status = HttpStatus.OK,
                    headers = Headers { set("Content-Type", "application/octet-stream") },
                    body = twoMb,
                ),
            )
            val client = server.client()

            val response = client.execute(
                NetworkRequest(HttpMethod.GET, "${server.baseUrl}/big"),
            )

            response.assertThat {
                hasStatus(HttpStatus.OK)
            }
            val receivedBytes = response.body.bytes()
            assertEquals(twoMb.size, receivedBytes.size, "Response body size must match")
            assertTrue(receivedBytes.contentEquals(twoMb), "Response body content must match")
        }
    }

    // ── 5. 503 + Retry-After → retry interceptor gets the 200 ─────────────────

    @Test
    fun retryInterceptorRetriesOnServiceUnavailable() = runTest {
        CaterktorHttpServer().use { server ->
            // First: 503 with Retry-After: 0 (no actual delay)
            server.enqueue(
                NetworkResponse(
                    status = HttpStatus.ServiceUnavailable,
                    headers = Headers { set("Retry-After", "0") },
                    body = byteArrayOf(),
                ),
            )
            // Second: 200 OK
            server.enqueue(HttpStatus.OK, body = """{"ok":true}""".toByteArray())

            val client = server.client {
                addInterceptor(RetryInterceptor(maxAttempts = 3))
            }

            val response = client.execute(
                NetworkRequest(HttpMethod.GET, "${server.baseUrl}/flaky"),
            )

            response.assertThat {
                hasStatus(HttpStatus.OK)
                hasBodyText("""{"ok":true}""")
            }
            // Two requests should have been recorded (initial + retry)
            assertEquals(2, server.requests.size)
        }
    }

    // ── 6. 301 redirect ───────────────────────────────────────────────────────

    @Test
    fun redirectIsFollowed() = runTest {
        CaterktorHttpServer().use { server ->
            // First: 301 pointing to /new-path
            server.enqueue(
                NetworkResponse(
                    status = HttpStatus.MovedPermanently,
                    headers = Headers { set("Location", "/new-path") },
                    body = byteArrayOf(),
                ),
            )
            // Second: 200 on /new-path
            server.enqueue(HttpStatus.OK, body = """{"redirected":true}""".toByteArray())

            // Ktor CIO client follows redirects by default
            val client = server.client()

            val response = client.execute(
                NetworkRequest(HttpMethod.GET, "${server.baseUrl}/old-path"),
            )

            response.assertThat {
                hasStatus(HttpStatus.OK)
                hasBodyText("""{"redirected":true}""")
            }
        }
    }

    // ── 7. Server is closed after test ────────────────────────────────────────

    @Test
    fun serverIsClosedAfterUse() {
        val server = CaterktorHttpServer()
        assertTrue(server.baseUrl.startsWith("http://127.0.0.1:"), "Server should have a loopback URL")
        server.close()
        // Closing again should be safe (idempotent)
        server.close()
    }
}
