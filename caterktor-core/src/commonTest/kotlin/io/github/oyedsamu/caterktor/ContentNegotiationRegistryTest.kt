@file:OptIn(ExperimentalCaterktor::class)

package io.github.oyedsamu.caterktor

import kotlinx.coroutines.test.runTest
import kotlin.reflect.KType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class ContentNegotiationRegistryTest {

    private data class Payload(val value: String)

    private class PayloadConverter : BodyConverter {
        override fun supports(contentType: String): Boolean =
            contentType == "application/vnd.payload"

        override fun <T : Any> encode(value: T, type: KType, contentType: String): ByteArray =
            (value as Payload).value.encodeToByteArray()

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> decode(raw: RawBody, type: KType): T =
            Payload(raw.bytes.decodeToString()) as T
    }

    private val converter = PayloadConverter()

    @Test
    fun registerNormalizesBareContentTypeAndBuildsAcceptHeader() {
        val registry = ContentNegotiationRegistry {
            register("Application/Vnd.Payload; charset=UTF-8", converter)
            register("application/backup", converter, quality = 0.5)
        }

        assertEquals(listOf("application/vnd.payload", "application/backup"), registry.acceptedContentTypes)
        assertEquals("application/vnd.payload, application/backup; q=0.5", registry.acceptHeader)
        assertSame(converter, registry.converterFor("application/vnd.payload; charset=UTF-8"))
    }

    @Test
    fun duplicateBareContentTypeFailsFast() {
        assertFailsWith<IllegalArgumentException> {
            ContentNegotiationRegistry {
                register("application/vnd.payload", converter)
                register("application/vnd.payload; charset=UTF-8", converter)
            }
        }
    }

    @Test
    fun typedCallAddsDefaultAcceptHeaderAndDecodesWithRegistryConverter() = runTest {
        var seenAccept: String? = null
        val client = CaterKtor {
            transport = Transport { request ->
                seenAccept = request.headers["Accept"]
                NetworkResponse(
                    status = HttpStatus.OK,
                    headers = Headers { set("Content-Type", "application/vnd.payload; charset=UTF-8") },
                    body = "from-registry".encodeToByteArray(),
                )
            }
            contentNegotiation {
                register("application/vnd.payload", converter)
            }
        }

        val result = client.get<Payload>("https://example.test/payload")

        val success = assertIs<NetworkResult.Success<Payload>>(result)
        assertEquals(Payload("from-registry"), success.body)
        assertEquals("application/vnd.payload", seenAccept)
    }

    @Test
    fun explicitAcceptHeaderWinsOverRegistryDefault() = runTest {
        var seenAccept: String? = null
        val client = CaterKtor {
            transport = Transport { request ->
                seenAccept = request.headers["Accept"]
                NetworkResponse(
                    status = HttpStatus.OK,
                    headers = Headers { set("Content-Type", "application/vnd.payload") },
                    body = "body".encodeToByteArray(),
                )
            }
            contentNegotiation {
                register("application/vnd.payload", converter)
            }
        }

        client.get<Payload>(
            url = "https://example.test/payload",
            headers = Headers { set("Accept", "application/special") },
        )

        assertEquals("application/special", seenAccept)
    }

    @Test
    fun registryConverterIsUsedForEncodingWithParameterizedContentType() = runTest {
        var seenBody: ByteArray? = null
        val client = CaterKtor {
            transport = Transport { request ->
                seenBody = request.body?.bytes()
                NetworkResponse(
                    status = HttpStatus.NoContent,
                    headers = Headers.Empty,
                    body = byteArrayOf(),
                )
            }
            contentNegotiation {
                register("application/vnd.payload", converter)
            }
        }

        val result = client.post<Unit, Payload>(
            url = "https://example.test/payload",
            body = Payload("encoded"),
            contentType = "application/vnd.payload; charset=UTF-8",
        )

        assertIs<NetworkResult.Success<Unit>>(result)
        assertEquals("encoded", seenBody?.decodeToString())
    }
}
