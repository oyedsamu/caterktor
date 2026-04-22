@file:OptIn(ExperimentalCaterktor::class)

package io.github.oyedsamu.caterktor

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class CancellationPropagationTest {

    @Test
    fun decodeCancellationPropagatesUnchanged() = runTest {
        val sentinel = CancellationException("decode cancelled")
        val client = CaterKtor {
            transport = Transport { jsonResponse() }
            addConverter(object : BodyConverter {
                override fun supports(contentType: String): Boolean = contentType == "application/json"

                override fun <T : Any> encode(value: T, type: KType, contentType: String): ByteArray =
                    byteArrayOf()

                override fun <T : Any> decode(raw: RawBody, type: KType): T {
                    throw sentinel
                }
            })
        }

        val thrown = assertFailsWith<CancellationException> {
            client.get<String>("https://example.test/cancel")
        }
        assertSame(sentinel, thrown)
    }

    @Test
    fun unwrapCancellationPropagatesUnchanged() = runTest {
        val sentinel = CancellationException("unwrap cancelled")
        val client = CaterKtor {
            transport = Transport { jsonResponse() }
            addConverter(returningStringConverter())
        }

        val thrown = assertFailsWith<CancellationException> {
            client.get<String>(
                url = "https://example.test/cancel",
                attributes = Attributes {
                    put(
                        CaterKtorKeys.UNWRAPPER,
                        object : ResponseUnwrapper {
                            override fun unwrap(
                                raw: ByteArray,
                                contentType: String?,
                                response: NetworkResponse,
                            ): ByteArray {
                                throw sentinel
                            }
                        },
                    )
                },
            )
        }
        assertSame(sentinel, thrown)
    }

    @Test
    fun encodeCancellationPropagatesUnchanged() = runTest {
        val sentinel = CancellationException("encode cancelled")
        var transportCalls = 0
        val client = CaterKtor {
            transport = Transport {
                transportCalls += 1
                jsonResponse()
            }
            addConverter(object : BodyConverter {
                override fun supports(contentType: String): Boolean = contentType == "application/json"

                override fun <T : Any> encode(value: T, type: KType, contentType: String): ByteArray {
                    throw sentinel
                }

                override fun <T : Any> decode(raw: RawBody, type: KType): T =
                    error("decode should not be reached")
            })
        }

        val thrown = assertFailsWith<CancellationException> {
            client.post<Unit, String>(
                url = "https://example.test/cancel",
                body = "payload",
            )
        }
        assertSame(sentinel, thrown)
        assertEquals(0, transportCalls)
    }

    @Test
    fun envelopCancellationPropagatesUnchanged() = runTest {
        val sentinel = CancellationException("envelop cancelled")
        var transportCalls = 0
        val client = CaterKtor {
            transport = Transport {
                transportCalls += 1
                jsonResponse()
            }
            addConverter(returningStringConverter())
        }

        val thrown = assertFailsWith<CancellationException> {
            client.post<Unit, String>(
                url = "https://example.test/cancel",
                body = "payload",
                attributes = Attributes {
                    put(
                        CaterKtorKeys.ENVELOPER,
                        object : RequestEnveloper {
                            override fun envelop(encoded: ByteArray, contentType: String): RequestBody {
                                throw sentinel
                            }
                        },
                    )
                },
            )
        }
        assertSame(sentinel, thrown)
        assertEquals(0, transportCalls)
    }

    private fun jsonResponse(): NetworkResponse =
        NetworkResponse(
            status = HttpStatus.OK,
            headers = Headers { set("Content-Type", "application/json") },
            body = "\"ok\"".encodeToByteArray(),
        )

    private fun returningStringConverter(): BodyConverter =
        object : BodyConverter {
            override fun supports(contentType: String): Boolean = contentType == "application/json"

            override fun <T : Any> encode(value: T, type: KType, contentType: String): ByteArray =
                "\"payload\"".encodeToByteArray()

            @Suppress("UNCHECKED_CAST")
            override fun <T : Any> decode(raw: RawBody, type: KType): T = "ok" as T
        }
}
