@file:OptIn(ExperimentalCaterktor::class)

package io.github.oyedsamu.caterktor

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class InterceptorChainTest {

    private val okResponse = NetworkResponse(
        status = HttpStatus.OK,
        headers = Headers.Empty,
        body = byteArrayOf(),
    )

    private fun request(url: String = "https://example.test/") =
        NetworkRequest(method = HttpMethod.GET, url = url)

    private fun transportReturning(response: NetworkResponse): Transport =
        Transport { response }

    private fun transportCapturing(seen: MutableList<NetworkRequest>): Transport =
        Transport { req -> seen += req; okResponse }

    private class TaggingInterceptor(private val tag: String, private val order: MutableList<String>) : Interceptor {
        override suspend fun intercept(chain: Chain): NetworkResponse {
            order += "enter:$tag"
            val response = chain.proceed(chain.request)
            order += "exit:$tag"
            return response
        }
    }

    @Test
    fun chain_dispatchesInterceptorsInRegistrationOrder() = runTest {
        val order = mutableListOf<String>()
        val client = CaterKtor {
            transport = transportCapturing(mutableListOf())
            addInterceptor(TaggingInterceptor("a", order))
            addInterceptor(TaggingInterceptor("b", order))
            addInterceptor(TaggingInterceptor("c", order))
        }
        client.execute(request())
        assertEquals(
            listOf("enter:a", "enter:b", "enter:c", "exit:c", "exit:b", "exit:a"),
            order,
        )
    }

    @Test
    fun chain_deliversRewrittenRequestToTransport() = runTest {
        val seen = mutableListOf<NetworkRequest>()
        val client = CaterKtor {
            transport = transportCapturing(seen)
            addInterceptor(object : Interceptor {
                override suspend fun intercept(chain: Chain): NetworkResponse {
                    val rewritten = chain.request.copy(url = chain.request.url + "?tagged=1")
                    return chain.proceed(rewritten)
                }
            })
        }
        client.execute(request("https://example.test/users"))
        assertEquals(1, seen.size)
        assertEquals("https://example.test/users?tagged=1", seen[0].url)
    }

    @Test
    fun chain_firstAttemptIsOne() = runTest {
        var observed: Int = -1
        val client = CaterKtor {
            transport = transportReturning(okResponse)
            addInterceptor(object : Interceptor {
                override suspend fun intercept(chain: Chain): NetworkResponse {
                    observed = chain.attempt
                    return chain.proceed(chain.request)
                }
            })
        }
        client.execute(request())
        assertEquals(1, observed)
    }

    @Test
    fun chain_deadlinePropagatesThroughEveryStage() = runTest {
        val deadline: kotlin.time.Instant = kotlin.time.Instant.fromEpochMilliseconds(
            kotlin.time.Clock.System.now().toEpochMilliseconds() + 300_000,
        )
        val observed = mutableListOf<kotlin.time.Instant?>()
        val client = CaterKtor {
            transport = transportReturning(okResponse)
            addInterceptor(object : Interceptor {
                override suspend fun intercept(chain: Chain): NetworkResponse {
                    observed += chain.deadline
                    return chain.proceed(chain.request)
                }
            })
            addInterceptor(object : Interceptor {
                override suspend fun intercept(chain: Chain): NetworkResponse {
                    observed += chain.deadline
                    return chain.proceed(chain.request)
                }
            })
        }
        client.execute(request(), deadline = deadline)
        assertEquals(listOf<kotlin.time.Instant?>(deadline, deadline), observed)
    }

    @Test
    fun chain_deadlineDefaultsToNull() = runTest {
        var observed: kotlin.time.Instant? = kotlin.time.Instant.fromEpochMilliseconds(1)
        val client = CaterKtor {
            transport = transportReturning(okResponse)
            addInterceptor(object : Interceptor {
                override suspend fun intercept(chain: Chain): NetworkResponse {
                    observed = chain.deadline
                    return chain.proceed(chain.request)
                }
            })
        }
        client.execute(request())
        assertNull(observed)
    }

    @Test
    fun chain_throwsWhenNonPrivilegedInterceptorCallsProceedTwice() = runTest {
        val client = CaterKtor {
            transport = transportReturning(okResponse)
            addInterceptor(object : Interceptor {
                override suspend fun intercept(chain: Chain): NetworkResponse {
                    chain.proceed(chain.request)
                    // Second call on the same chain instance — illegal.
                    return chain.proceed(chain.request)
                }
            })
        }
        val ex = assertFailsWith<IllegalStateException> { client.execute(request()) }
        assertTrue(
            "proceed()" in ex.message.orEmpty(),
            "expected diagnostic about proceed(), got: ${ex.message}",
        )
    }

    @Test
    fun chain_allowsPrivilegedInterceptorToCallProceedMultipleTimes() = runTest {
        var transportCalls = 0
        val transport = Transport { _ ->
            transportCalls += 1
            okResponse
        }

        val retry = object : PrivilegedInterceptor {
            override suspend fun intercept(chain: Chain): NetworkResponse {
                chain.proceed(chain.request)
                chain.proceed(chain.request)
                return chain.proceed(chain.request)
            }
        }

        val client = CaterKtor {
            this.transport = transport
            addInterceptor(retry)
        }
        client.execute(request())
        assertEquals(3, transportCalls)
    }

    @Test
    fun chain_eachPrivilegedReentryGetsFreshDownstreamChain() = runTest {
        // Downstream interceptor must still enforce its own at-most-once rule,
        // even when an upstream privileged interceptor re-dispatches. Each
        // privileged re-entry gives it a brand-new chain.
        var downstreamEntries = 0
        val downstream = object : Interceptor {
            override suspend fun intercept(chain: Chain): NetworkResponse {
                downstreamEntries += 1
                return chain.proceed(chain.request)
            }
        }
        val retry = object : PrivilegedInterceptor {
            override suspend fun intercept(chain: Chain): NetworkResponse {
                chain.proceed(chain.request)
                return chain.proceed(chain.request)
            }
        }
        val client = CaterKtor {
            transport = transportReturning(okResponse)
            addInterceptor(retry)
            addInterceptor(downstream)
        }
        client.execute(request())
        assertEquals(2, downstreamEntries)
    }

    @Test
    fun chain_propagatesCancellationExceptionUnchanged() = runTest {
        val sentinel = CancellationException("user cancelled")
        val client = CaterKtor {
            transport = Transport { _ -> throw sentinel }
            addInterceptor(object : Interceptor {
                override suspend fun intercept(chain: Chain): NetworkResponse {
                    // Deliberately do NOT catch CancellationException.
                    return chain.proceed(chain.request)
                }
            })
        }
        val thrown = assertFailsWith<CancellationException> { client.execute(request()) }
        assertSame(sentinel, thrown)
    }

    @Test
    fun chain_interceptorCanShortCircuitWithoutCallingProceed() = runTest {
        var transportCalls = 0
        val shortCircuit = NetworkResponse(HttpStatus.NoContent, Headers.Empty, byteArrayOf())
        val client = CaterKtor {
            transport = Transport { _ -> transportCalls += 1; okResponse }
            addInterceptor(object : Interceptor {
                override suspend fun intercept(chain: Chain): NetworkResponse = shortCircuit
            })
        }
        val response = client.execute(request())
        assertEquals(shortCircuit, response)
        assertEquals(0, transportCalls)
    }

    @Test
    fun emptyPipeline_dispatchesDirectlyToTransport() = runTest {
        val seen = mutableListOf<NetworkRequest>()
        val client = CaterKtor {
            transport = transportCapturing(seen)
        }
        val req = request("https://example.test/direct")
        client.execute(req)
        assertEquals(1, seen.size)
        assertEquals(req.url, seen[0].url)
    }
}
