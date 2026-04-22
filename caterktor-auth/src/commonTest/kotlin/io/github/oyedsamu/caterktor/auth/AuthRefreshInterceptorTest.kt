@file:OptIn(ExperimentalCaterktor::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.oyedsamu.caterktor.auth

import io.github.oyedsamu.caterktor.Attributes
import io.github.oyedsamu.caterktor.CaterKtor
import io.github.oyedsamu.caterktor.CaterKtorKeys
import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import io.github.oyedsamu.caterktor.Headers
import io.github.oyedsamu.caterktor.HttpStatus
import io.github.oyedsamu.caterktor.NetworkError
import io.github.oyedsamu.caterktor.NetworkResponse
import io.github.oyedsamu.caterktor.NetworkResult
import io.github.oyedsamu.caterktor.Transport
import io.github.oyedsamu.caterktor.get
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AuthRefreshInterceptorTest {

    @Test
    fun refreshesUnauthorizedResponseAndRetriesWithNewToken() = runTest {
        val seenAuth = mutableListOf<String?>()
        var refreshCalls = 0
        val client = CaterKtor {
            transport = Transport { request ->
                val auth = request.headers["Authorization"]
                seenAuth += auth
                if (auth == "Bearer new") {
                    response(HttpStatus.NoContent)
                } else {
                    response(HttpStatus.Unauthorized)
                }
            }
            addInterceptor(
                AuthRefreshInterceptor(
                    tokenProvider = { "old" },
                    refreshToken = {
                        refreshCalls += 1
                        "new"
                    },
                ),
            )
        }

        val result = client.get<Unit>("https://example.test/private")

        val success = assertIs<NetworkResult.Success<Unit>>(result)
        assertEquals(2, success.attempts)
        assertEquals(listOf<String?>("Bearer old", "Bearer new"), seenAuth)
        assertEquals(1, refreshCalls)
    }

    @Test
    fun concurrentUnauthorizedResponsesShareOneRefresh() = runTest {
        val requestCount = 5
        val oldTokenCalls = SuspendCounter()
        val newTokenCalls = SuspendCounter()
        val refreshCalls = SuspendCounter()
        val allUnauthorized = CompletableDeferred<Unit>()
        val client = CaterKtor {
            transport = Transport { request ->
                when (request.headers["Authorization"]) {
                    "Bearer old" -> {
                        if (oldTokenCalls.increment() == requestCount) {
                            allUnauthorized.complete(Unit)
                        }
                        response(HttpStatus.Unauthorized)
                    }
                    "Bearer new" -> {
                        newTokenCalls.increment()
                        response(HttpStatus.NoContent)
                    }
                    else -> response(HttpStatus.BadRequest)
                }
            }
            addInterceptor(
                AuthRefreshInterceptor(
                    tokenProvider = { "old" },
                    refreshToken = {
                        refreshCalls.increment()
                        allUnauthorized.await()
                        "new"
                    },
                ),
            )
        }

        val results = List(requestCount) {
            async { client.get<Unit>("https://example.test/private") }
        }.map { it.await() }

        results.forEach { assertIs<NetworkResult.Success<Unit>>(it) }
        assertEquals(1, refreshCalls.value())
        assertEquals(requestCount, oldTokenCalls.value())
        assertEquals(requestCount, newTokenCalls.value())
    }

    @Test
    fun failedRefreshFansOutUnauthorizedFailure() = runTest {
        val requestCount = 3
        val oldTokenCalls = SuspendCounter()
        val refreshCalls = SuspendCounter()
        val allUnauthorized = CompletableDeferred<Unit>()
        val client = CaterKtor {
            transport = Transport { request ->
                if (request.headers["Authorization"] == "Bearer old") {
                    if (oldTokenCalls.increment() == requestCount) {
                        allUnauthorized.complete(Unit)
                    }
                }
                response(HttpStatus.Unauthorized)
            }
            addInterceptor(
                AuthRefreshInterceptor(
                    tokenProvider = { "old" },
                    refreshToken = {
                        refreshCalls.increment()
                        allUnauthorized.await()
                        error("refresh backend down")
                    },
                ),
            )
        }

        val results = List(requestCount) {
            async { client.get<Unit>("https://example.test/private") }
        }.map { it.await() }

        results.forEach { result ->
            val failure = assertIs<NetworkResult.Failure>(result)
            val error = assertIs<NetworkError.Http>(failure.error)
            assertEquals(HttpStatus.Unauthorized, error.status)
            assertIs<AuthRefreshFailedException>(error.cause)
        }
        assertEquals(1, refreshCalls.value())
    }

    @Test
    fun cancelledWaiterDoesNotCancelSharedRefresh() = runTest {
        val oldTokenCalls = SuspendCounter()
        val allUnauthorized = CompletableDeferred<Unit>()
        val releaseRefresh = CompletableDeferred<Unit>()
        val refreshCalls = SuspendCounter()
        val client = CaterKtor {
            transport = Transport { request ->
                when (request.headers["Authorization"]) {
                    "Bearer old" -> {
                        if (oldTokenCalls.increment() == 2) {
                            allUnauthorized.complete(Unit)
                        }
                        response(HttpStatus.Unauthorized)
                    }
                    "Bearer new" -> response(HttpStatus.NoContent)
                    else -> response(HttpStatus.BadRequest)
                }
            }
            addInterceptor(
                AuthRefreshInterceptor(
                    tokenProvider = { "old" },
                    refreshToken = {
                        refreshCalls.increment()
                        allUnauthorized.await()
                        releaseRefresh.await()
                        "new"
                    },
                ),
            )
        }

        val cancelled = async { client.get<Unit>("https://example.test/private") }
        val surviving = async { client.get<Unit>("https://example.test/private") }

        allUnauthorized.await()
        cancelled.cancelAndJoin()
        releaseRefresh.complete(Unit)

        assertIs<NetworkResult.Success<Unit>>(surviving.await())
        assertEquals(1, refreshCalls.value())
    }

    @Test
    fun skipAuthBypassesInjectionAndRefresh() = runTest {
        var refreshCalls = 0
        val client = CaterKtor {
            transport = Transport { request ->
                assertEquals(null, request.headers["Authorization"])
                response(HttpStatus.Unauthorized)
            }
            addInterceptor(
                AuthRefreshInterceptor(
                    tokenProvider = { "old" },
                    refreshToken = {
                        refreshCalls += 1
                        "new"
                    },
                ),
            )
        }

        val result = client.get<Unit>(
            url = "https://example.test/refresh",
            attributes = Attributes { put(CaterKtorKeys.SKIP_AUTH, true) },
        )

        assertIs<NetworkResult.Failure>(result)
        assertEquals(0, refreshCalls)
    }

    @Test
    fun exhaustedBudgetFailsUnauthorizedWithCause() = runTest {
        var refreshCalls = 0
        val client = CaterKtor {
            transport = Transport { response(HttpStatus.Unauthorized) }
            addInterceptor(
                AuthRefreshInterceptor(
                    tokenProvider = { "old" },
                    refreshToken = {
                        refreshCalls += 1
                        "new"
                    },
                    budget = RefreshBudget(maxRefreshes = 1, windowMs = 60_000L),
                ),
            )
        }

        assertIs<NetworkResult.Failure>(client.get<Unit>("https://example.test/private"))
        val second = assertIs<NetworkResult.Failure>(client.get<Unit>("https://example.test/private"))
        val error = assertIs<NetworkError.Http>(second.error)

        assertIs<AuthRefreshBudgetExceededException>(error.cause)
        assertEquals(1, refreshCalls)
    }

    private fun response(status: HttpStatus): NetworkResponse =
        NetworkResponse(status = status, headers = Headers.Empty, body = byteArrayOf())

    private class SuspendCounter {
        private val mutex = Mutex()
        private var count: Int = 0

        suspend fun increment(): Int = mutex.withLock {
            count += 1
            count
        }

        suspend fun value(): Int = mutex.withLock { count }
    }
}
