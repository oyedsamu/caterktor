@file:OptIn(ExperimentalCaterktor::class)

package io.github.oyedsamu.caterktor

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * Tests for [KtorTransport.download] — placed in `nonJsTest` because the
 * streaming download path uses `runBlocking` internally, which is not available
 * on Kotlin/JS.
 */
class KtorTransportDownloadTest {

    @Test
    fun download_exposes_streaming_body_inside_block() = runTest {
        val payload = ByteArray(64 * 1024) { (it % 251).toByte() }
        val engine = MockEngine { request ->
            assertEquals("https://example.test/download", request.url.toString())
            respond(
                content = payload,
                status = HttpStatusCode.OK,
                headers = headersOf(
                    "Content-Type" to listOf("application/octet-stream"),
                    "Content-Length" to listOf(payload.size.toString()),
                ),
            )
        }
        val transport = KtorTransport(HttpClient(engine))

        val streamed = transport.download(
            NetworkRequest(method = HttpMethod.GET, url = "https://example.test/download"),
        ) { response ->
            assertEquals(HttpStatus.OK, response.status)
            assertEquals("application/octet-stream", response.headers["content-type"])
            assertEquals(payload.size.toLong(), response.body.contentLength)
            assertIs<ResponseBody.Source>(response.body)
            response.body.bytes()
        }

        assertContentEquals(payload, streamed)
    }

    @Test
    fun download_streaming_body_is_one_shot() = runTest {
        val engine = MockEngine { _ ->
            respond(content = "hello", status = HttpStatusCode.OK)
        }
        val transport = KtorTransport(HttpClient(engine))

        transport.download(
            NetworkRequest(method = HttpMethod.GET, url = "https://example.test/download"),
        ) { response ->
            assertEquals("hello", response.body.bytes().decodeToString())
            assertFailsWith<IllegalStateException> {
                response.body.source()
            }
        }
    }
}
