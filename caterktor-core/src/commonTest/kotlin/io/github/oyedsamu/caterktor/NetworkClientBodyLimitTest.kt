@file:OptIn(ExperimentalCaterktor::class)

package io.github.oyedsamu.caterktor

import kotlin.reflect.KType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer

class NetworkClientBodyLimitTest {

    @Test
    fun unknownLengthBodyBeyondDecodeLimitFailsBeforeDecode() = runTest {
        var decodeCalls = 0
        val client = CaterKtor {
            maxBodyDecodeBytes(5)
            transport = Transport {
                NetworkResponse(
                    status = HttpStatus.OK,
                    headers = Headers { set("Content-Type", "text/plain") },
                    body = sourceBody("123456", contentLength = null),
                )
            }
            addConverter(
                stringConverter {
                    decodeCalls += 1
                    "should not decode"
                },
            )
        }

        val result = client.get<String>("https://example.test/large")

        val failure = assertIs<NetworkResult.Failure>(result)
        val error = assertIs<NetworkError.Serialization>(failure.error)
        assertEquals(0, decodeCalls)
        assertEquals(null, error.rawBody)
        assertTrue(error.cause?.message.orEmpty().contains("maxBodyDecodeBytes (5)"))
    }

    @Test
    fun unwrapperOutputBeyondDecodeLimitFailsBeforeDecode() = runTest {
        var decodeCalls = 0
        val client = CaterKtor {
            maxBodyDecodeBytes(5)
            transport = Transport {
                NetworkResponse(
                    status = HttpStatus.OK,
                    headers = Headers { set("Content-Type", "text/plain") },
                    body = "small".encodeToByteArray(),
                )
            }
            unwrapper(
                object : ResponseUnwrapper {
                    override fun unwrap(
                        body: ResponseBody,
                        contentType: String?,
                        response: NetworkResponse,
                    ): ResponseBody = sourceBody("123456", contentLength = null)
                },
            )
            addConverter(
                stringConverter {
                    decodeCalls += 1
                    "should not decode"
                },
            )
        }

        val result = client.get<String>("https://example.test/enveloped")

        val failure = assertIs<NetworkResult.Failure>(result)
        val error = assertIs<NetworkError.Serialization>(failure.error)
        assertEquals(0, decodeCalls)
        assertEquals(null, error.rawBody)
        assertTrue(error.cause?.message.orEmpty().contains("maxBodyDecodeBytes (5)"))
    }

    @Test
    fun unknownLengthBodyWithinDecodeLimitDecodesNormally() = runTest {
        val client = CaterKtor {
            maxBodyDecodeBytes(16)
            transport = Transport {
                NetworkResponse(
                    status = HttpStatus.OK,
                    headers = Headers { set("Content-Type", "text/plain") },
                    body = sourceBody("hello", contentLength = null),
                )
            }
            addConverter(stringConverter { raw -> raw.asString() })
        }

        val result = client.get<String>("https://example.test/small")

        val success = assertIs<NetworkResult.Success<String>>(result)
        assertEquals("hello", success.body)
    }

    @Test
    fun httpErrorBodyBeyondDecodeLimitIsDropped() = runTest {
        val client = CaterKtor {
            maxBodyDecodeBytes(5)
            transport = Transport {
                NetworkResponse(
                    status = HttpStatus.InternalServerError,
                    headers = Headers { set("Content-Type", "text/plain") },
                    body = sourceBody("123456", contentLength = null),
                )
            }
            addConverter(stringConverter { raw -> raw.asString() })
        }

        val result = client.get<String>("https://example.test/error")

        val failure = assertIs<NetworkResult.Failure>(result)
        val error = assertIs<NetworkError.Http>(failure.error)
        assertEquals(null, error.body.raw)
    }

    private fun sourceBody(text: String, contentLength: Long?): ResponseBody =
        ResponseBody.Source(
            sourceFactory = {
                Buffer().also { it.write(text.encodeToByteArray()) }
            },
            contentType = "text/plain",
            contentLength = contentLength,
        )

    private fun stringConverter(decode: (RawBody) -> String): BodyConverter =
        object : BodyConverter {
            override fun supports(contentType: String): Boolean = contentType == "text/plain"

            override fun <T : Any> encode(value: T, type: KType, contentType: String): ByteArray =
                value.toString().encodeToByteArray()

            @Suppress("UNCHECKED_CAST")
            override fun <T : Any> decode(raw: RawBody, type: KType): T = decode(raw) as T
        }
}
