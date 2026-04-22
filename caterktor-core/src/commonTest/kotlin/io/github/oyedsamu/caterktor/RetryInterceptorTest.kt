@file:OptIn(ExperimentalCaterktor::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.oyedsamu.caterktor

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import kotlinx.io.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

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
    fun retryPolicySeesBoundedHttpErrorBody() = runTest {
        var observedRaw: RawBody? = null
        val policy = object : RetryPolicy {
            override fun shouldRetry(
                attempt: Int,
                request: NetworkRequest,
                response: NetworkResponse?,
                error: NetworkError,
            ): Boolean {
                observedRaw = assertIs<NetworkError.Http>(error).body.raw
                return false
            }

            override suspend fun computeDelayMs(
                attempt: Int,
                request: NetworkRequest,
                response: NetworkResponse?,
                error: NetworkError,
            ): Long = 0L
        }
        val client = CaterKtor {
            transport = Transport {
                NetworkResponse(
                    status = HttpStatus.ServiceUnavailable,
                    headers = Headers { set("Content-Type", "text/plain") },
                    body = sourceBody(
                        text = "not-read-because-content-length-exceeds-default-cap",
                        contentLength = Long.MAX_VALUE,
                    ),
                )
            }
            addInterceptor(RetryInterceptor(maxAttempts = 2, policy = policy))
        }

        val response = client.execute(NetworkRequest(HttpMethod.GET, "https://example.test/retry"))

        assertEquals(HttpStatus.ServiceUnavailable, response.status)
        assertEquals(null, observedRaw)
    }

    @Test
    fun nonIdempotentRetryOptInRequiresIdempotencyKeyOrThrows() = runTest {
        val client = CaterKtor {
            transport = Transport { response(HttpStatus.ServiceUnavailable) }
            addInterceptor(
                RetryInterceptor(
                    maxAttempts = 2,
                    policy = ImmediateServiceUnavailableRetryPolicy,
                    retryNonIdempotent = true,
                ),
            )
        }

        // POST with retryNonIdempotent=true but no Idempotency-Key must throw explicitly
        // rather than silently proceeding without retry — misconfiguration must be loud.
        val exception = assertFailsWith<IllegalStateException> {
            client.execute(
                NetworkRequest(
                    method = HttpMethod.POST,
                    url = "https://example.test/create",
                ),
            )
        }
        assertTrue(
            exception.message.orEmpty().contains("Idempotency-Key"),
            "Exception message should name the missing header, was: ${exception.message}",
        )
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

    @Test
    fun exponentialBackoffDefaultsRetry502503504() {
        val policy = ExponentialBackoffPolicy()
        val request = NetworkRequest(HttpMethod.GET, "https://example.test/retry")

        for (status in listOf(HttpStatus.BadGateway, HttpStatus.ServiceUnavailable, HttpStatus.GatewayTimeout)) {
            val response = response(status)
            val error = NetworkError.Http(status, response.headers, ErrorBody.Empty)
            assertTrue(policy.shouldRetry(1, request, response, error), "expected retry for ${status.code}")
        }
    }

    @Test
    fun exponentialBackoffUsesFullJitterWithInjectableRandom() = runTest {
        val policy = ExponentialBackoffPolicy(
            baseDelayMs = 1_000L,
            maxDelayMs = 1_000L,
            randomDouble = { 0.5 },
        )

        val delayMs = policy.computeDelayMs(
            attempt = 1,
            request = NetworkRequest(HttpMethod.GET, "https://example.test/retry"),
            response = null,
            error = NetworkError.Timeout(TimeoutKind.Request),
        )

        assertEquals(500L, delayMs)
    }

    @Test
    fun exponentialBackoffHonorsDecimalRetryAfterSeconds() = runTest {
        val policy = ExponentialBackoffPolicy(randomDouble = { 0.0 })
        val response = NetworkResponse(
            status = HttpStatus.ServiceUnavailable,
            headers = Headers { set("Retry-After", "0.2") },
            body = byteArrayOf(),
        )

        val delayMs = policy.computeDelayMs(
            attempt = 1,
            request = NetworkRequest(HttpMethod.GET, "https://example.test/retry"),
            response = response,
            error = NetworkError.Http(response.status, response.headers, ErrorBody.Empty),
        )

        assertEquals(200L, delayMs)
    }

    @Test
    fun exponentialBackoffHonorsRetryAfterHttpDate() = runTest {
        val nowMs = Instant.parse("2026-04-22T10:00:00Z").toEpochMilliseconds()
        val policy = ExponentialBackoffPolicy(
            randomDouble = { 0.0 },
            currentTimeMillis = { nowMs },
        )
        val response = NetworkResponse(
            status = HttpStatus.ServiceUnavailable,
            headers = Headers { set("Retry-After", "Wed, 22 Apr 2026 10:00:03 GMT") },
            body = byteArrayOf(),
        )

        val delayMs = policy.computeDelayMs(
            attempt = 1,
            request = NetworkRequest(HttpMethod.GET, "https://example.test/retry"),
            response = response,
            error = NetworkError.Http(response.status, response.headers, ErrorBody.Empty),
        )

        assertEquals(3_000L, delayMs)
    }

    private fun response(status: HttpStatus): NetworkResponse =
        NetworkResponse(status = status, headers = Headers.Empty, body = byteArrayOf())

    private fun sourceBody(text: String, contentLength: Long?): ResponseBody =
        ResponseBody.Source(
            sourceFactory = {
                Buffer().also { it.write(text.encodeToByteArray()) }
            },
            contentType = "text/plain",
            contentLength = contentLength,
        )
}
