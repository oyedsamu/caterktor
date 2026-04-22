@file:OptIn(ExperimentalCaterktor::class)

package io.github.oyedsamu.caterktor

import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TimeoutDeadlineTest {

    private object RetryRequestTimeoutImmediately : RetryPolicy {
        override fun shouldRetry(
            attempt: Int,
            request: NetworkRequest,
            response: NetworkResponse?,
            error: NetworkError,
        ): Boolean = error is NetworkError.Timeout && error.kind == TimeoutKind.Request

        override suspend fun computeDelayMs(
            attempt: Int,
            request: NetworkRequest,
            response: NetworkResponse?,
            error: NetworkError,
        ): Long = 0L
    }

    private object DelayedServiceUnavailableRetryPolicy : RetryPolicy {
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
        ): Long = 1_000L
    }

    @Test
    fun requestTimeoutIsAppliedPerAttemptInsteadOfWholeRetryLoop() = runTest {
        var transportCalls = 0
        val client = CaterKtor {
            timeout { requestTimeoutMs = 50L }
            transport = Transport {
                transportCalls += 1
                if (transportCalls == 1) {
                    delay(100)
                }
                response(HttpStatus.NoContent)
            }
            addInterceptor(RetryInterceptor(maxAttempts = 2, policy = RetryRequestTimeoutImmediately))
        }

        val result = client.get<Unit>("https://example.test/timeout-then-success")

        val success = assertIs<NetworkResult.Success<Unit>>(result)
        assertEquals(2, success.attempts)
        assertEquals(2, transportCalls)
    }

    @Test
    fun deadlineWinsOverLongAttemptTimeout() = runTest {
        var transportCalls = 0
        val client = CaterKtor {
            timeout { requestTimeoutMs = 500L }
            transport = Transport {
                transportCalls += 1
                delay(100)
                response(HttpStatus.NoContent)
            }
        }

        val result = client.get<Unit>(
            url = "https://example.test/deadline",
            deadline = Clock.System.now() + 50.milliseconds,
        )

        val failure = assertIs<NetworkResult.Failure>(result)
        val timeout = assertIs<NetworkError.Timeout>(failure.error)
        assertEquals(TimeoutKind.Deadline, timeout.kind)
        assertEquals(1, failure.attempts)
        assertEquals(1, transportCalls)
    }

    @Test
    fun retryDelayThatCannotFitDeadlineFailsWithDeadlineTimeout() = runTest {
        var transportCalls = 0
        val client = CaterKtor {
            transport = Transport {
                transportCalls += 1
                response(HttpStatus.ServiceUnavailable)
            }
            addInterceptor(RetryInterceptor(maxAttempts = 2, policy = DelayedServiceUnavailableRetryPolicy))
        }

        val result = client.get<Unit>(
            url = "https://example.test/retry-after-deadline",
            deadline = Clock.System.now() + 50.milliseconds,
        )

        val failure = assertIs<NetworkResult.Failure>(result)
        val timeout = assertIs<NetworkError.Timeout>(failure.error)
        assertEquals(TimeoutKind.Deadline, timeout.kind)
        assertEquals(1, failure.attempts)
        assertEquals(1, transportCalls)
    }

    private fun response(status: HttpStatus): NetworkResponse =
        NetworkResponse(status = status, headers = Headers.Empty, body = byteArrayOf())
}
