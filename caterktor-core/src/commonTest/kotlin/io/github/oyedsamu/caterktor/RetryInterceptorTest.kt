@file:OptIn(ExperimentalCaterktor::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.oyedsamu.caterktor

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RetryInterceptorTest {

    private object ImmediateServiceUnavailableRetryPolicy : RetryPolicy {
        override fun shouldRetry(
            attempt: Int,
            request: NetworkRequest,
            response: NetworkResponse?,
            error: NetworkError,
        ): Boolean = error is NetworkError.Http && error.status == HttpStatus.ServiceUnavailable

        override suspend fun computeDelayMs(
            attempt: Int,
            request: NetworkRequest,
            response: NetworkResponse?,
            error: NetworkError,
        ): Long = 0L
    }

    @Test
    fun retry_updatesDownstreamChainAttemptAndSuccessResultAttempts() = runTest {
        val observedAttempts = mutableListOf<Int>()
        var transportCalls = 0
        val client = CaterKtor {
            transport = Transport {
                transportCalls += 1
                if (transportCalls == 1) response(HttpStatus.ServiceUnavailable) else response(HttpStatus.NoContent)
            }
            addInterceptor(RetryInterceptor(maxAttempts = 2, policy = ImmediateServiceUnavailableRetryPolicy))
            addInterceptor(object : Interceptor {
                override suspend fun intercept(chain: Chain): NetworkResponse {
                    observedAttempts += chain.attempt
                    return chain.proceed(chain.request)
                }
            })
        }

        val result = client.get<Unit>("https://example.test/retry")

        val success = assertIs<NetworkResult.Success<Unit>>(result)
        assertEquals(2, success.attempts)
        assertEquals(2, transportCalls)
        assertEquals(listOf(1, 2), observedAttempts)
    }

    @Test
    fun retry_updatesEventAttemptsWithFinalAttemptCount() = runTest {
        var transportCalls = 0
        val client = CaterKtor {
            transport = Transport {
                transportCalls += 1
                if (transportCalls == 1) response(HttpStatus.ServiceUnavailable) else response(HttpStatus.NoContent)
            }
            addInterceptor(RetryInterceptor(maxAttempts = 2, policy = ImmediateServiceUnavailableRetryPolicy))
        }
        val events = mutableListOf<NetworkEvent>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            client.events.take(3).toList(events)
        }

        val result = client.get<Unit>("https://example.test/retry")
        collector.join()

        assertIs<NetworkResult.Success<Unit>>(result)
        assertEquals(3, events.size)
        assertEquals(2, (events[1] as NetworkEvent.ResponseReceived).attempts)
        assertEquals(2, (events[2] as NetworkEvent.CallSuccess).attempts)
    }

    @Test
    fun retry_reportsFinalAttemptCountOnHttpFailure() = runTest {
        var transportCalls = 0
        val client = CaterKtor {
            transport = Transport {
                transportCalls += 1
                response(HttpStatus.ServiceUnavailable)
            }
            addInterceptor(RetryInterceptor(maxAttempts = 2, policy = ImmediateServiceUnavailableRetryPolicy))
        }

        val result = client.get<Unit>("https://example.test/retry")

        val failure = assertIs<NetworkResult.Failure>(result)
        assertEquals(2, failure.attempts)
        assertEquals(2, transportCalls)
    }

    @Test
    fun nonIdempotentRetryOptInStillRequiresIdempotencyKey() = runTest {
        var transportCalls = 0
        val client = CaterKtor {
            transport = Transport {
                transportCalls += 1
                response(HttpStatus.ServiceUnavailable)
            }
            addInterceptor(
                RetryInterceptor(
                    maxAttempts = 2,
                    policy = ImmediateServiceUnavailableRetryPolicy,
                    retryNonIdempotent = true,
                ),
            )
        }

        val response = client.execute(
            NetworkRequest(
                method = HttpMethod.POST,
                url = "https://example.test/create",
            ),
        )

        assertEquals(HttpStatus.ServiceUnavailable, response.status)
        assertEquals(1, transportCalls)
    }

    @Test
    fun nonIdempotentRetryOptInRetriesWhenIdempotencyKeyIsPresent() = runTest {
        var transportCalls = 0
        val client = CaterKtor {
            transport = Transport {
                transportCalls += 1
                if (transportCalls == 1) response(HttpStatus.ServiceUnavailable) else response(HttpStatus.NoContent)
            }
            addInterceptor(
                RetryInterceptor(
                    maxAttempts = 2,
                    policy = ImmediateServiceUnavailableRetryPolicy,
                    retryNonIdempotent = true,
                ),
            )
        }

        val response = client.execute(
            NetworkRequest(
                method = HttpMethod.POST,
                url = "https://example.test/create",
                headers = Headers.of("Idempotency-Key" to "create-1"),
            ),
        )

        assertEquals(HttpStatus.NoContent, response.status)
        assertEquals(2, transportCalls)
    }

    private fun response(status: HttpStatus): NetworkResponse =
        NetworkResponse(status = status, headers = Headers.Empty, body = byteArrayOf())
}
