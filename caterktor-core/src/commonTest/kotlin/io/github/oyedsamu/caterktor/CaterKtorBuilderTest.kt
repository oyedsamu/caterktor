@file:OptIn(ExperimentalCaterktor::class)

package io.github.oyedsamu.caterktor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CaterKtorBuilderTest {

    private class NoopTransport : Transport {
        override suspend fun execute(request: NetworkRequest): NetworkResponse = NoopResponse
    }

    private val noopTransport: Transport = NoopTransport()

    private companion object {
        val NoopResponse = NetworkResponse(HttpStatus.OK, Headers.Empty, byteArrayOf())
    }

    private class AuthInterceptor : Interceptor {
        override suspend fun intercept(chain: Chain): NetworkResponse = chain.proceed(chain.request)
    }

    private class LoggerInterceptor : Interceptor {
        override suspend fun intercept(chain: Chain): NetworkResponse = chain.proceed(chain.request)
    }

    @Test
    fun build_failsFastWhenTransportMissing() {
        val ex = assertFailsWith<IllegalStateException> {
            CaterKtor {
                addInterceptor(AuthInterceptor())
            }
        }
        assertTrue(
            "transport" in ex.message.orEmpty(),
            "expected diagnostic about missing transport, got: ${ex.message}",
        )
    }

    @Test
    fun addInterceptor_preservesInsertionOrder() {
        val client = CaterKtor {
            transport = noopTransport
            addInterceptor(AuthInterceptor())
            addInterceptor(LoggerInterceptor())
        }
        assertEquals(
            listOf("AuthInterceptor", "LoggerInterceptor"),
            client.interceptors.map { it::class.simpleName },
        )
    }

    @Test
    fun addInterceptor_returnsBuilderForChaining() {
        val client = CaterKtor {
            transport = noopTransport
            addInterceptor(AuthInterceptor()).addInterceptor(LoggerInterceptor())
        }
        assertEquals(2, client.interceptors.size)
    }

    @Test
    fun interceptorsSnapshot_isIndependentOfBuilderList() {
        lateinit var snapshot: List<Interceptor>
        CaterKtor {
            transport = noopTransport
            addInterceptor(AuthInterceptor())
            snapshot = interceptors
            addInterceptor(LoggerInterceptor())
        }
        assertEquals(1, snapshot.size)
    }

    @Test
    fun describePipeline_listsInterceptorsThenTransport() {
        val client = CaterKtor {
            transport = noopTransport
            addInterceptor(AuthInterceptor())
            addInterceptor(LoggerInterceptor())
        }
        val described = client.describePipeline()
        assertEquals(
            listOf(
                "[0] AuthInterceptor",
                "[1] LoggerInterceptor",
                "[2] Transport(NoopTransport)",
            ),
            described,
        )
    }

    @Test
    fun describePipeline_isStableAcrossCalls() {
        val client = CaterKtor {
            transport = noopTransport
            addInterceptor(AuthInterceptor())
        }
        assertEquals(client.describePipeline(), client.describePipeline())
    }
}
