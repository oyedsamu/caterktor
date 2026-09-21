package io.github.oyedsamu.caterktor

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Guards the stable surface: this file carries no `@OptIn(ExperimentalCaterktor::class)`,
 * so it only compiles while building a client, writing an interceptor and implementing a
 * transport all stay free of an opt-in requirement.
 *
 * Marking any of those experimental again breaks this file at compile time, which is the
 * point — a stable API that needs an opt-in at every call site is not stable.
 */
class StableApiTest {

    private class EchoTransport : Transport {
        var lastRequest: NetworkRequest? = null

        override suspend fun execute(request: NetworkRequest): NetworkResponse {
            lastRequest = request
            return NetworkResponse(HttpStatus.OK, Headers.Empty, byteArrayOf())
        }
    }

    private class TaggingInterceptor(private val name: String) : Interceptor {
        override suspend fun intercept(chain: Chain): NetworkResponse {
            val tagged = chain.request.copy(
                headers = chain.request.headers.toBuilder().set("X-Tag", name).build(),
            )
            return chain.proceed(tagged)
        }
    }

    @Test
    fun a_client_can_be_built_and_used_without_opting_in() = runTest {
        val transport = EchoTransport()

        val client = CaterKtor {
            this.transport = transport
            baseUrl = "https://example.test"
            addInterceptor(TaggingInterceptor("first"))
            defaultHeader("X-App", "caterktor")
            timeout { requestTimeoutMs = 5_000 }
        }

        val response = client.execute(
            NetworkRequest(method = HttpMethod.GET, url = "https://example.test/ping"),
        )

        assertEquals(HttpStatus.OK, response.status)
        assertEquals("first", transport.lastRequest?.headers?.get("X-Tag"))
        assertEquals("caterktor", transport.lastRequest?.headers?.get("X-App"))
    }
}
