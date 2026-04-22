@file:OptIn(ExperimentalCaterktor::class)

package io.github.oyedsamu.caterktor.logging

import io.github.oyedsamu.caterktor.CaterKtor
import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import io.github.oyedsamu.caterktor.Headers
import io.github.oyedsamu.caterktor.HttpMethod
import io.github.oyedsamu.caterktor.HttpStatus
import io.github.oyedsamu.caterktor.NetworkRequest
import io.github.oyedsamu.caterktor.NetworkResponse
import io.github.oyedsamu.caterktor.RequestBody
import io.github.oyedsamu.caterktor.Transport
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoggerInterceptorTest {

    @Test
    fun basicLevelLogsRequestAndResponse() = runTest {
        val lines = mutableListOf<String>()
        val client = CaterKtor {
            transport = Transport { response(HttpStatus.NoContent) }
            addInterceptor(LoggerInterceptor(logger = lines::add))
        }

        val response = client.execute(
            NetworkRequest(
                method = HttpMethod.GET,
                url = "https://example.test/items",
            ),
        )

        assertEquals(HttpStatus.NoContent, response.status)
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("GET https://example.test/items"))
        assertTrue(lines[1].contains("204"))
    }

    @Test
    fun headersLevelRedactsSensitiveHeaders() = runTest {
        val lines = mutableListOf<String>()
        val client = CaterKtor {
            transport = Transport {
                response(
                    HttpStatus.OK,
                    Headers {
                        set("Set-Cookie", "session=secret")
                        set("X-Trace", "visible")
                    },
                )
            }
            addInterceptor(LoggerInterceptor(level = LogLevel.Headers, logger = lines::add))
        }

        val response = client.execute(
            NetworkRequest(
                method = HttpMethod.GET,
                url = "https://example.test/private",
                headers = Headers {
                    set("Authorization", "Bearer secret")
                    set("X-Client", "android")
                },
            ),
        )

        assertEquals(HttpStatus.OK, response.status)
        assertTrue(lines.any { it == "  authorization: ***" })
        assertTrue(lines.any { it == "  set-cookie: ***" })
        assertTrue(lines.any { it == "  x-client: android" })
        assertTrue(lines.any { it == "  x-trace: visible" })
        assertFalse(lines.any { it.contains("secret") })
    }

    @Test
    fun bodyLevelSuppressesBinaryBodies() = runTest {
        val lines = mutableListOf<String>()
        val client = CaterKtor {
            transport = Transport {
                response(HttpStatus.OK, body = byteArrayOf(0x00, 0x01, 0x02))
            }
            addInterceptor(LoggerInterceptor(level = LogLevel.Body, logger = lines::add))
        }

        val response = client.execute(
            NetworkRequest(
                method = HttpMethod.POST,
                url = "https://example.test/upload",
                body = RequestBody.Bytes(
                    bytes = "hello".encodeToByteArray(),
                    contentType = "text/plain",
                ),
            ),
        )

        assertEquals(HttpStatus.OK, response.status)
        assertTrue(lines.any { it.contains("Body (text/plain): hello") })
        assertTrue(lines.any { it.contains("<binary 3 bytes>") })
    }

    private fun response(
        status: HttpStatus,
        headers: Headers = Headers.Empty,
        body: ByteArray = byteArrayOf(),
    ): NetworkResponse = NetworkResponse(status = status, headers = headers, body = body)
}
