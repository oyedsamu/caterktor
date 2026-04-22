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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun basicLevelLogsTransportFailureBeforeRethrowing() = runTest {
        val lines = mutableListOf<String>()
        val client = CaterKtor {
            transport = Transport { throw IllegalStateException("socket closed") }
            addInterceptor(LoggerInterceptor(logger = lines::add))
        }

        val thrown = assertFailsWith<IllegalStateException> {
            client.execute(
                NetworkRequest(
                    method = HttpMethod.GET,
                    url = "https://example.test/items",
                ),
            )
        }

        assertEquals("socket closed", thrown.message)
        assertTrue(lines.any { it.contains("GET https://example.test/items") })
        assertTrue(lines.any { it.contains("<! IllegalStateException") && it.contains("socket closed") })
        assertFalse(lines.any { it.startsWith("<-") })
    }

    @Test
    fun basicLevelDoesNotLogCancellationAsFailure() = runTest {
        val lines = mutableListOf<String>()
        val client = CaterKtor {
            transport = Transport { throw CancellationException("call cancelled") }
            addInterceptor(LoggerInterceptor(logger = lines::add))
        }

        val thrown = assertFailsWith<CancellationException> {
            client.execute(
                NetworkRequest(
                    method = HttpMethod.GET,
                    url = "https://example.test/items",
                ),
            )
        }

        assertEquals("call cancelled", thrown.message)
        assertTrue(lines.any { it.contains("GET https://example.test/items") })
        assertFalse(lines.any { it.startsWith("<!") })
        assertFalse(lines.any { it.startsWith("<-") })
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

    @Test
    fun bodyLevelRedactsQueryParametersAndJsonFields() = runTest {
        val lines = mutableListOf<String>()
        val client = CaterKtor {
            transport = Transport {
                response(
                    status = HttpStatus.OK,
                    headers = Headers { set("Content-Type", "application/json") },
                    body = """{"token":"server-secret","visible":"ok"}""".encodeToByteArray(),
                )
            }
            addInterceptor(LoggerInterceptor(level = LogLevel.Body, logger = lines::add))
        }

        val response = client.execute(
            NetworkRequest(
                method = HttpMethod.POST,
                url = "https://example.test/private?access_token=query-secret&visible=yes",
                body = RequestBody.Text(
                    text = """{"password":"body-secret","profile":{"token":"nested-secret","name":"Ada"}}""",
                    contentType = "application/json",
                ),
            ),
        )

        assertEquals(HttpStatus.OK, response.status)
        assertTrue(lines.any { it.contains("access_token=***") })
        assertTrue(lines.any { it.contains("visible=yes") })
        assertTrue(lines.any { it.contains("\"password\":\"***\"") })
        assertTrue(lines.any { it.contains("\"token\":\"***\"") })
        assertTrue(lines.any { it.contains("\"name\":\"Ada\"") })
        assertFalse(lines.any { it.contains("query-secret") })
        assertFalse(lines.any { it.contains("body-secret") })
        assertFalse(lines.any { it.contains("nested-secret") })
        assertFalse(lines.any { it.contains("server-secret") })
    }

    @Test
    fun regexRulesApplyToNonSensitiveHeaderValues() = runTest {
        val lines = mutableListOf<String>()
        val redaction = RedactionEngine(
            regexRules = listOf(
                RegexRedactionRule(Regex("[0-9]{3}-[0-9]{2}-[0-9]{4}")),
            ),
        )
        val client = CaterKtor {
            transport = Transport { response(HttpStatus.NoContent) }
            addInterceptor(
                LoggerInterceptor(
                    level = LogLevel.Headers,
                    redaction = redaction,
                    logger = lines::add,
                ),
            )
        }

        val response = client.execute(
            NetworkRequest(
                method = HttpMethod.GET,
                url = "https://example.test/private",
                headers = Headers { set("X-Subject", "id=123-45-6789") },
            ),
        )

        assertEquals(HttpStatus.NoContent, response.status)
        assertTrue(lines.any { it == "  x-subject: id=***" })
        assertFalse(lines.any { it.contains("123-45-6789") })
    }

    @Test
    fun bodyLevelHonorsMaxBodyBytes() = runTest {
        val lines = mutableListOf<String>()
        val redaction = RedactionEngine(maxBodyBytes = 4)
        val client = CaterKtor {
            transport = Transport { response(HttpStatus.OK, body = "too-large".encodeToByteArray()) }
            addInterceptor(
                LoggerInterceptor(
                    level = LogLevel.Body,
                    redaction = redaction,
                    logger = lines::add,
                ),
            )
        }

        val response = client.execute(
            NetworkRequest(
                method = HttpMethod.POST,
                url = "https://example.test/upload",
                body = RequestBody.Text("too-large"),
            ),
        )

        assertEquals(HttpStatus.OK, response.status)
        assertTrue(lines.any { it.contains("<body 9 bytes exceeds max 4 bytes>") })
        assertFalse(lines.any { it.contains("too-large") })
    }

    private fun response(
        status: HttpStatus,
        headers: Headers = Headers.Empty,
        body: ByteArray = byteArrayOf(),
    ): NetworkResponse = NetworkResponse(status = status, headers = headers, body = body)
}
