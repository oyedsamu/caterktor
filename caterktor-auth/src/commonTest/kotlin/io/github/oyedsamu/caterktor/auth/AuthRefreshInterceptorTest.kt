@file:OptIn(ExperimentalCaterktor::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.oyedsamu.caterktor.auth

import io.github.oyedsamu.caterktor.Attributes
import io.github.oyedsamu.caterktor.CaterKtor
import io.github.oyedsamu.caterktor.CaterKtorKeys
import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import io.github.oyedsamu.caterktor.Headers
import io.github.oyedsamu.caterktor.HttpStatus
import io.github.oyedsamu.caterktor.NetworkClient
import io.github.oyedsamu.caterktor.NetworkError
import io.github.oyedsamu.caterktor.NetworkResponse
import io.github.oyedsamu.caterktor.NetworkResult
import io.github.oyedsamu.caterktor.ResponseBody
import io.github.oyedsamu.caterktor.TimeoutKind
import io.github.oyedsamu.caterktor.Transport
import io.github.oyedsamu.caterktor.get
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.io.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.time.Instant

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
                    currentTimeMillis = { kotlin.time.Clock.System.now().toEpochMilliseconds() },
                    refreshDispatcher = coroutineContext[ContinuationInterceptor]!!,
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
    fun concurrentUnauthorizedResponsesPropertyDeduplicatesRefreshForRequestCountsTwoThroughTwenty() = runTest {
        for (requestCount in 2..20) {
            val scenario = runSingleFlightSuccessScenario(requestCount)

            scenario.results.forEach { result ->
                val success = assertIs<NetworkResult.Success<Unit>>(result)
                assertEquals(2, success.attempts, "requestCount=$requestCount")
            }
            assertEquals(1, scenario.refreshCalls, "requestCount=$requestCount")
            assertEquals(requestCount, scenario.oldTokenCalls, "requestCount=$requestCount")
            assertEquals(requestCount, scenario.newTokenCalls, "requestCount=$requestCount")
        }
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
                    currentTimeMillis = { kotlin.time.Clock.System.now().toEpochMilliseconds() },
                    refreshDispatcher = coroutineContext[ContinuationInterceptor]!!,
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
    fun unauthorizedResponseInFlightDuringRefreshSharesCompletedRefresh() = runTest {
        val oldTokenCalls = SuspendCounter()
        val refreshCalls = SuspendCounter()
        val secondUnauthorizedEntered = CompletableDeferred<Unit>()
        val releaseSecondUnauthorized = CompletableDeferred<Unit>()
        val client = CaterKtor {
            transport = Transport { request ->
                when (request.headers["Authorization"]) {
                    "Bearer old" -> {
                        if (oldTokenCalls.increment() == 2) {
                            secondUnauthorizedEntered.complete(Unit)
                            releaseSecondUnauthorized.await()
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
                        secondUnauthorizedEntered.await()
                        "new"
                    },
                ),
            )
        }

        try {
            val first = async { client.get<Unit>("https://example.test/private/first") }
            while (refreshCalls.value() == 0) {
                yield()
            }

            val second = async { client.get<Unit>("https://example.test/private/second") }
            secondUnauthorizedEntered.await()

            assertIs<NetworkResult.Success<Unit>>(first.await())
            releaseSecondUnauthorized.complete(Unit)
            assertIs<NetworkResult.Success<Unit>>(second.await())
            assertEquals(1, refreshCalls.value())
        } finally {
            client.close()
        }
    }

    @Test
    fun failedRefreshPropertyFansOutUnauthorizedFailureForRequestCountsTwoThroughTwenty() = runTest {
        for (requestCount in 2..20) {
            val scenario = runFailedRefreshFanOutScenario(requestCount)

            scenario.results.forEach { result ->
                val failure = assertIs<NetworkResult.Failure>(result)
                val error = assertIs<NetworkError.Http>(failure.error)
                assertEquals(HttpStatus.Unauthorized, error.status, "requestCount=$requestCount")
                assertIs<AuthRefreshFailedException>(error.cause, "requestCount=$requestCount")
            }
            assertEquals(1, scenario.refreshCalls, "requestCount=$requestCount")
            assertEquals(1, scenario.failureNotifications, "requestCount=$requestCount")
        }
    }

    @Test
    fun failedRefreshDropsOversizedUnauthorizedBody() = runTest {
        val client = CaterKtor {
            transport = Transport {
                NetworkResponse(
                    status = HttpStatus.Unauthorized,
                    headers = Headers { set("Content-Type", "text/plain") },
                    body = sourceBody(
                        text = "not-read-because-content-length-exceeds-default-cap",
                        contentLength = Long.MAX_VALUE,
                    ),
                )
            }
            addInterceptor(
                AuthRefreshInterceptor(
                    tokenProvider = { "old" },
                    refreshToken = { error("refresh backend down") },
                ),
            )
        }

        val result = client.get<Unit>("https://example.test/private")

        val failure = assertIs<NetworkResult.Failure>(result)
        val error = assertIs<NetworkError.Http>(failure.error)
        assertEquals(null, error.body.raw)
    }

    @Test
    fun refreshCancellationPropagatesUnchanged() = runTest {
        val sentinel = CancellationException("refresh cancelled")
        val client = CaterKtor {
            transport = Transport { response(HttpStatus.Unauthorized) }
            addInterceptor(
                AuthRefreshInterceptor(
                    tokenProvider = { "old" },
                    refreshToken = { throw sentinel },
                ),
            )
        }

        val thrown = assertFailsWith<CancellationException> {
            client.get<Unit>("https://example.test/private")
        }

        assertEquals(sentinel.message, thrown.message)
    }

    @Test
    fun refreshFailureObserverCancellationPropagatesUnchanged() = runTest {
        val sentinel = CancellationException("observer cancelled")
        val client = CaterKtor {
            transport = Transport { response(HttpStatus.Unauthorized) }
            addInterceptor(
                AuthRefreshInterceptor(
                    tokenProvider = { "old" },
                    refreshToken = { error("refresh backend down") },
                    onRefreshFailed = { throw sentinel },
                ),
            )
        }

        val thrown = assertFailsWith<CancellationException> {
            client.get<Unit>("https://example.test/private")
        }

        assertEquals(sentinel.message, thrown.message)
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
    fun cancelledWaiterPropertyDoesNotCancelSharedRefreshForRepresentativeRequestCounts() = runTest {
        for (requestCount in listOf(2, 3, 5, 8, 13, 20)) {
            val scenario = runCancelledWaiterScenario(requestCount)

            scenario.survivingResults.forEach { result ->
                assertIs<NetworkResult.Success<Unit>>(result, "requestCount=$requestCount")
            }
            assertEquals(1, scenario.refreshCalls, "requestCount=$requestCount")
            assertEquals(requestCount, scenario.oldTokenCalls, "requestCount=$requestCount")
            assertEquals(requestCount - 1, scenario.newTokenCalls, "requestCount=$requestCount")
        }
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
    fun refreshTokenCanCallSameClientWithSkipAuthWithoutDeadlock() = runTest {
        val seen = mutableListOf<Pair<String, String?>>()
        lateinit var client: NetworkClient
        client = CaterKtor {
            transport = Transport { request ->
                val path = request.url.substringAfter("https://example.test")
                seen += path to request.headers["Authorization"]
                when {
                    path == "/auth/token" -> response(HttpStatus.NoContent)
                    request.headers["Authorization"] == "Bearer new" -> response(HttpStatus.NoContent)
                    else -> response(HttpStatus.Unauthorized)
                }
            }
            addInterceptor(
                AuthRefreshInterceptor(
                    tokenProvider = { "old" },
                    refreshToken = {
                        val refreshResult = client.get<Unit>(
                            url = "https://example.test/auth/token",
                            attributes = Attributes { put(CaterKtorKeys.SKIP_AUTH, true) },
                        )
                        assertIs<NetworkResult.Success<Unit>>(refreshResult)
                        "new"
                    },
                ),
            )
        }

        val result = try {
            client.get<Unit>("https://example.test/private")
        } finally {
            client.close()
        }

        assertIs<NetworkResult.Success<Unit>>(result)
        assertEquals(
            listOf(
                "/private" to "Bearer old",
                "/auth/token" to null,
                "/private" to "Bearer new",
            ),
            seen,
        )
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

    @Test
    fun refreshBudgetStateMachineNotifiesOncePerWindowAndResetsAfterWindow() = runTest {
        val clock = ManualClock()
        var refreshCalls = 0
        val notifications = mutableListOf<Throwable>()
        val client = CaterKtor {
            transport = Transport { response(HttpStatus.Unauthorized) }
            addInterceptor(
                AuthRefreshInterceptor(
                    tokenProvider = { "old" },
                    refreshToken = {
                        refreshCalls += 1
                        "new-$refreshCalls"
                    },
                    budget = RefreshBudget(maxRefreshes = 1, windowMs = 1_000L),
                    onRefreshFailed = { cause -> notifications += cause },
                    currentTimeMillis = clock::nowMs,
                ),
            )
        }

        try {
            assertHttpFailure(client.get<Unit>("https://example.test/private/1"))
            assertEquals(1, refreshCalls)
            assertEquals(0, notifications.size)

            val second = assertHttpFailure(client.get<Unit>("https://example.test/private/2"))
            assertIs<AuthRefreshBudgetExceededException>(second.cause)
            assertEquals(1, refreshCalls)
            assertEquals(1, notifications.size)

            val third = assertHttpFailure(client.get<Unit>("https://example.test/private/3"))
            assertIs<AuthRefreshBudgetExceededException>(third.cause)
            assertEquals(1, refreshCalls)
            assertEquals(1, notifications.size)

            clock.advanceBy(1_000L)

            val fourth = assertHttpFailure(client.get<Unit>("https://example.test/private/4"))
            assertEquals(null, fourth.cause)
            assertEquals(2, refreshCalls)
            assertEquals(1, notifications.size)

            val fifth = assertHttpFailure(client.get<Unit>("https://example.test/private/5"))
            assertIs<AuthRefreshBudgetExceededException>(fifth.cause)
            assertEquals(2, refreshCalls)
            assertEquals(2, notifications.size)
        } finally {
            client.close()
        }
    }

    @Test
    fun refreshWaitHonorsAlreadyExhaustedDeadline() = runTest {
        val clock = ManualClock()
        val neverCompletes = CompletableDeferred<String>()
        val client = CaterKtor {
            transport = Transport { response(HttpStatus.Unauthorized) }
            addInterceptor(
                AuthRefreshInterceptor(
                    tokenProvider = { "old" },
                    refreshToken = { neverCompletes.await() },
                    currentTimeMillis = clock::nowMs,
                ),
            )
        }

        val result = try {
            client.get<Unit>(
                url = "https://example.test/private",
                deadline = Instant.fromEpochMilliseconds(clock.nowMs()),
            )
        } finally {
            client.close()
        }

        val failure = assertIs<NetworkResult.Failure>(result)
        val error = assertIs<NetworkError.Timeout>(failure.error)
        assertEquals(TimeoutKind.Deadline, error.kind)
    }

    private suspend fun runSingleFlightSuccessScenario(requestCount: Int): SingleFlightSuccessScenario =
        coroutineScope {
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
                        currentTimeMillis = { kotlin.time.Clock.System.now().toEpochMilliseconds() },
                        refreshDispatcher = coroutineContext[ContinuationInterceptor]!!,
                    ),
                )
            }

            try {
                val results = List(requestCount) { index ->
                    async { client.get<Unit>("https://example.test/private/$index") }
                }.map { it.await() }
                SingleFlightSuccessScenario(
                    results = results,
                    refreshCalls = refreshCalls.value(),
                    oldTokenCalls = oldTokenCalls.value(),
                    newTokenCalls = newTokenCalls.value(),
                )
            } finally {
                client.close()
            }
        }

    private suspend fun runFailedRefreshFanOutScenario(requestCount: Int): FailedRefreshScenario =
        coroutineScope {
            val oldTokenCalls = SuspendCounter()
            val refreshCalls = SuspendCounter()
            val failureNotifications = SuspendCounter()
            val allUnauthorized = CompletableDeferred<Unit>()
            val releaseRefreshFailure = CompletableDeferred<Unit>()
            val client = CaterKtor {
                transport = Transport { request ->
                    if (request.headers["Authorization"] == "Bearer old" &&
                        oldTokenCalls.increment() == requestCount
                    ) {
                        allUnauthorized.complete(Unit)
                    }
                    response(HttpStatus.Unauthorized)
                }
                addInterceptor(
                    AuthRefreshInterceptor(
                        tokenProvider = { "old" },
                        refreshToken = {
                            refreshCalls.increment()
                            allUnauthorized.await()
                            releaseRefreshFailure.await()
                            error("refresh backend down")
                        },
                        onRefreshFailed = {
                            failureNotifications.increment()
                        },
                        currentTimeMillis = { kotlin.time.Clock.System.now().toEpochMilliseconds() },
                        refreshDispatcher = coroutineContext[ContinuationInterceptor]!!,
                    ),
                )
            }

            try {
                val requests = List(requestCount) { index ->
                    async { client.get<Unit>("https://example.test/private/$index") }
                }

                allUnauthorized.await()
                while (refreshCalls.value() == 0) {
                    yield()
                }
                repeat(requestCount) {
                    yield()
                }
                releaseRefreshFailure.complete(Unit)

                val results = requests.map { it.await() }
                FailedRefreshScenario(
                    results = results,
                    refreshCalls = refreshCalls.value(),
                    failureNotifications = failureNotifications.value(),
                )
            } finally {
                client.close()
            }
        }

    private suspend fun runCancelledWaiterScenario(requestCount: Int): CancelledWaiterScenario =
        coroutineScope {
            val oldTokenCalls = SuspendCounter()
            val newTokenCalls = SuspendCounter()
            val refreshCalls = SuspendCounter()
            val allUnauthorized = CompletableDeferred<Unit>()
            val releaseRefresh = CompletableDeferred<Unit>()
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
                            releaseRefresh.await()
                            "new"
                        },
                    ),
                )
            }

            try {
                val requests = List(requestCount) { index ->
                    async { client.get<Unit>("https://example.test/private/$index") }
                }

                allUnauthorized.await()
                requests.first().cancelAndJoin()
                releaseRefresh.complete(Unit)

                CancelledWaiterScenario(
                    survivingResults = requests.drop(1).map { it.await() },
                    refreshCalls = refreshCalls.value(),
                    oldTokenCalls = oldTokenCalls.value(),
                    newTokenCalls = newTokenCalls.value(),
                )
            } finally {
                client.close()
            }
        }

    private fun assertHttpFailure(result: NetworkResult<Unit>): NetworkError.Http {
        val failure = assertIs<NetworkResult.Failure>(result)
        return assertIs<NetworkError.Http>(failure.error)
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

    private class SuspendCounter {
        private val mutex = Mutex()
        private var count: Int = 0

        suspend fun increment(): Int = mutex.withLock {
            count += 1
            count
        }

        suspend fun value(): Int = mutex.withLock { count }
    }

    private data class SingleFlightSuccessScenario(
        val results: List<NetworkResult<Unit>>,
        val refreshCalls: Int,
        val oldTokenCalls: Int,
        val newTokenCalls: Int,
    )

    private data class FailedRefreshScenario(
        val results: List<NetworkResult<Unit>>,
        val refreshCalls: Int,
        val failureNotifications: Int,
    )

    private data class CancelledWaiterScenario(
        val survivingResults: List<NetworkResult<Unit>>,
        val refreshCalls: Int,
        val oldTokenCalls: Int,
        val newTokenCalls: Int,
    )

    private class ManualClock(
        private var nowMs: Long = 10_000L,
    ) {
        fun nowMs(): Long = nowMs

        fun advanceBy(deltaMs: Long) {
            nowMs += deltaMs
        }
    }
}
