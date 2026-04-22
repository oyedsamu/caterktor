@file:OptIn(ExperimentalCaterktor::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.oyedsamu.caterktor

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CircuitBreakerTest {

    @Test
    fun opensAfterFailureThresholdAndRejectsWithoutCallingTransport() = runTest {
        var transportCalls = 0
        val breaker = CircuitBreaker(
            failureThreshold = 2,
            openDurationMs = 1_000L,
            currentTimeMillis = { 0L },
        )
        val client = CaterKtor {
            transport = Transport {
                transportCalls += 1
                response(HttpStatus.InternalServerError)
            }
            addInterceptor(breaker)
        }

        assertIs<NetworkResult.Failure>(client.get<Unit>("https://example.test/one"))
        assertIs<NetworkResult.Failure>(client.get<Unit>("https://example.test/two"))
        val rejected = assertIs<NetworkResult.Failure>(client.get<Unit>("https://example.test/three"))

        assertEquals(2, transportCalls)
        assertEquals(CircuitBreakerState.Open, breaker.currentState)
        val error = assertIs<NetworkError.CircuitOpen>(rejected.error)
        assertEquals(CircuitBreakerState.Open, error.state)
    }

    @Test
    fun halfOpenSuccessClosesBreakerAndEmitsTransitions() = runTest {
        var nowMs = 0L
        var transportCalls = 0
        val breaker = CircuitBreaker(
            name = "api",
            failureThreshold = 1,
            openDurationMs = 1_000L,
            currentTimeMillis = { nowMs },
        )
        val client = CaterKtor {
            transport = Transport {
                transportCalls += 1
                if (transportCalls == 1) response(HttpStatus.InternalServerError) else response(HttpStatus.NoContent)
            }
            addInterceptor(breaker)
        }
        val transitions = mutableListOf<NetworkEvent.CircuitBreakerTransition>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            client.events
                .filter { it is NetworkEvent.CircuitBreakerTransition }
                .take(3)
                .collect { transitions += it as NetworkEvent.CircuitBreakerTransition }
        }

        assertIs<NetworkResult.Failure>(client.get<Unit>("https://example.test/open"))
        nowMs = 1_001L
        assertIs<NetworkResult.Success<Unit>>(client.get<Unit>("https://example.test/probe"))
        collector.join()

        assertEquals(CircuitBreakerState.Closed, breaker.currentState)
        assertEquals(
            listOf(
                CircuitBreakerState.Open,
                CircuitBreakerState.HalfOpen,
                CircuitBreakerState.Closed,
            ),
            transitions.map { it.to },
        )
        assertEquals(listOf("api", "api", "api"), transitions.map { it.name })
    }

    @Test
    fun halfOpenFailureReopensBreaker() = runTest {
        var nowMs = 0L
        val breaker = CircuitBreaker(
            failureThreshold = 1,
            openDurationMs = 1_000L,
            currentTimeMillis = { nowMs },
        )
        val client = CaterKtor {
            transport = Transport { response(HttpStatus.InternalServerError) }
            addInterceptor(breaker)
        }

        assertIs<NetworkResult.Failure>(client.get<Unit>("https://example.test/open"))
        nowMs = 1_001L
        val probe = assertIs<NetworkResult.Failure>(client.get<Unit>("https://example.test/probe"))

        assertEquals(CircuitBreakerState.Open, breaker.currentState)
        assertIs<NetworkError.Http>(probe.error)
    }

    private fun response(status: HttpStatus): NetworkResponse =
        NetworkResponse(status = status, headers = Headers.Empty, body = byteArrayOf())
}
