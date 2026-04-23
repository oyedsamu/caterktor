package io.github.oyedsamu.caterktor.auth

import io.github.oyedsamu.caterktor.CaterKtorKeys
import io.github.oyedsamu.caterktor.Chain
import io.github.oyedsamu.caterktor.CloseableInterceptor
import io.github.oyedsamu.caterktor.ErrorBody
import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import io.github.oyedsamu.caterktor.Headers
import io.github.oyedsamu.caterktor.HttpStatus
import io.github.oyedsamu.caterktor.NetworkError
import io.github.oyedsamu.caterktor.NetworkErrorException
import io.github.oyedsamu.caterktor.NetworkRequest
import io.github.oyedsamu.caterktor.NetworkResponse
import io.github.oyedsamu.caterktor.PrivilegedInterceptor
import io.github.oyedsamu.caterktor.RawBody
import io.github.oyedsamu.caterktor.ResponseBody
import io.github.oyedsamu.caterktor.TimeoutKind
import io.github.oyedsamu.caterktor.buffered
import io.github.oyedsamu.caterktor.proceedForAttempt
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Limits how often an [AuthRefreshInterceptor] may start a token refresh.
 *
 * Concurrent callers that join an already-running refresh share its result and
 * do not consume additional budget.
 */
@ExperimentalCaterktor
public data class RefreshBudget(
    public val maxRefreshes: Int = 1,
    public val windowMs: Long = 60_000L,
) {
    init {
        require(maxRefreshes > 0) { "maxRefreshes must be positive, was $maxRefreshes" }
        require(windowMs > 0) { "windowMs must be positive, was $windowMs" }
    }
}

/**
 * Raised when the refresh callback failed.
 */
@ExperimentalCaterktor
public class AuthRefreshFailedException(
    public val originalCause: Throwable?,
) : IllegalStateException("Auth refresh failed", originalCause)

/**
 * Raised when refresh cannot start because the configured [RefreshBudget] is exhausted.
 */
@ExperimentalCaterktor
public class AuthRefreshBudgetExceededException(
    public val budget: RefreshBudget,
) : IllegalStateException(
    "Auth refresh budget exhausted: max ${budget.maxRefreshes} refresh(es) per ${budget.windowMs}ms",
)

/**
 * Adds Bearer auth, refreshes on 401, and retries once with the refreshed token.
 *
 * The interceptor performs single-flight refresh: concurrent 401 responses join
 * the same refresh job, then each waiting request retries with the shared token.
 * Requests that were already in flight while a refresh was running also share
 * that result if their 401 response arrives just after the refresh completes.
 * A caller cancelled while waiting does not cancel the shared refresh for other
 * callers.
 *
 * Requests with `CaterKtorKeys.SKIP_AUTH = true` in their attributes bypass both token
 * injection and refresh handling, which is the required attribute for refresh calls
 * issued through the same [io.github.oyedsamu.caterktor.NetworkClient].
 *
 * Requests that already contain an `Authorization` header are treated as
 * caller-owned and are not modified or refreshed.
 */
@ExperimentalCaterktor
public class AuthRefreshInterceptor : PrivilegedInterceptor, CloseableInterceptor {

    public val tokenProvider: suspend () -> String
    public val refreshToken: suspend () -> String
    public val budget: RefreshBudget
    public val onRefreshFailed: suspend (Throwable) -> Unit
    private val currentTimeMillis: () -> Long

    private val mutex = Mutex()
    private val refreshScope: CoroutineScope
    private var refreshGeneration: Long = 0L
    private var inFlightRefresh: RefreshFlight? = null
    private var windowStartMs: Long? = null
    private var failureNotifiedWindowStartMs: Long? = null
    private var refreshesInWindow: Int = 0

    public constructor(
        tokenProvider: suspend () -> String,
        refreshToken: suspend () -> String,
        budget: RefreshBudget = RefreshBudget(),
        onRefreshFailed: suspend (Throwable) -> Unit = {},
    ) : this(
        tokenProvider = tokenProvider,
        refreshToken = refreshToken,
        budget = budget,
        onRefreshFailed = onRefreshFailed,
        currentTimeMillis = { Clock.System.now().toEpochMilliseconds() },
        refreshDispatcher = Dispatchers.Default,
    )

    internal constructor(
        tokenProvider: suspend () -> String,
        refreshToken: suspend () -> String,
        budget: RefreshBudget = RefreshBudget(),
        onRefreshFailed: suspend (Throwable) -> Unit = {},
        currentTimeMillis: () -> Long,
        refreshDispatcher: CoroutineContext = Dispatchers.Default,
    ) {
        this.tokenProvider = tokenProvider
        this.refreshToken = refreshToken
        this.budget = budget
        this.onRefreshFailed = onRefreshFailed
        this.currentTimeMillis = currentTimeMillis
        this.refreshScope = CoroutineScope(SupervisorJob() + refreshDispatcher)
    }

    /**
     * Cancels the internal [refreshScope], terminating any in-flight token refresh.
     *
     * Must be called when the owning [io.github.oyedsamu.caterktor.NetworkClient] is closed.
     * Idempotent — repeated calls have no effect after the first.
     */
    override fun close(): Unit {
        refreshScope.cancel()
    }

    override suspend fun intercept(chain: Chain): NetworkResponse {
        val originalRequest = chain.request
        if (originalRequest.attributes.getOrNull(CaterKtorKeys.SKIP_AUTH) == true) {
            return chain.proceed(originalRequest)
        }

        if ("Authorization" in originalRequest.headers) {
            return chain.proceed(originalRequest)
        }

        val authorizedRequest = originalRequest.withBearerToken(tokenProvider())
        val refreshSnapshot = refreshSnapshot()
        val response = chain.proceed(authorizedRequest)
        if (response.status != HttpStatus.Unauthorized) {
            return response
        }

        val refreshedToken = refreshAfterUnauthorized(chain, response, refreshSnapshot)
        return chain.proceedForAttempt(
            originalRequest.withBearerToken(refreshedToken),
            attempt = chain.attempt + 1,
        )
    }

    private suspend fun refreshAfterUnauthorized(
        chain: Chain,
        response: NetworkResponse,
        refreshSnapshot: RefreshSnapshot,
    ): String {
        val refresh = try {
            singleFlightRefresh(refreshSnapshot)
        } catch (e: AuthRefreshBudgetExceededException) {
            failRefresh(response, e)
        }

        return when (val outcome = refresh.awaitRespecting(chain.deadline)) {
            is RefreshOutcome.Success -> outcome.token
            is RefreshOutcome.Cancelled -> throw outcome.cause
            is RefreshOutcome.Failure -> failRefresh(response, AuthRefreshFailedException(outcome.cause))
        }
    }

    private suspend fun refreshSnapshot(): RefreshSnapshot = mutex.withLock {
        RefreshSnapshot(
            generation = refreshGeneration,
            activeFlightGeneration = inFlightRefresh
                ?.takeIf { it.refresh.isActive }
                ?.generation,
        )
    }

    private suspend fun singleFlightRefresh(snapshot: RefreshSnapshot): Deferred<RefreshOutcome> = mutex.withLock {
        inFlightRefresh?.let { flight ->
            if (flight.refresh.isActive || flight.wasActiveFor(snapshot)) {
                return@withLock flight.refresh
            }
        }
        inFlightRefresh = null

        consumeBudget()
        val generation = refreshGeneration + 1L
        refreshGeneration = generation
        val refresh = refreshScope.async {
            try {
                RefreshOutcome.Success(refreshToken())
            } catch (e: CancellationException) {
                RefreshOutcome.Cancelled(e)
            } catch (t: Exception) {
                RefreshOutcome.Failure(t)
            }
        }
        inFlightRefresh = RefreshFlight(generation, refresh)
        refresh
    }

    private fun RefreshFlight.wasActiveFor(snapshot: RefreshSnapshot): Boolean =
        generation > snapshot.generation || generation == snapshot.activeFlightGeneration

    private fun consumeBudget() {
        val nowMs = currentTimeMillis()
        val startMs = windowStartMs
        if (startMs == null || nowMs - startMs >= budget.windowMs) {
            windowStartMs = nowMs
            refreshesInWindow = 0
            failureNotifiedWindowStartMs = null
        }
        if (refreshesInWindow >= budget.maxRefreshes) {
            throw AuthRefreshBudgetExceededException(budget)
        }
        refreshesInWindow += 1
    }

    private suspend fun Deferred<RefreshOutcome>.awaitRespecting(deadline: Instant?): RefreshOutcome {
        val remainingMs = deadline?.remainingMs()
        if (remainingMs == null) {
            return await()
        }
        if (remainingMs <= 0L) {
            throw NetworkErrorException(NetworkError.Timeout(TimeoutKind.Deadline))
        }
        return withTimeoutOrNull(remainingMs) {
            await()
        } ?: throw NetworkErrorException(NetworkError.Timeout(TimeoutKind.Deadline))
    }

    private suspend fun failRefresh(
        response: NetworkResponse,
        cause: Throwable,
    ): Nothing {
        notifyRefreshFailed(cause)
        throw NetworkErrorException(
            NetworkError.Http(
                status = HttpStatus.Unauthorized,
                headers = response.headers,
                body = ErrorBody(
                    raw = response.body.rawBodyOrNull(response.headers["Content-Type"]),
                    parsed = null,
                ),
                cause = cause,
            ),
        )
    }

    private suspend fun notifyRefreshFailed(cause: Throwable) {
        if (!shouldNotifyRefreshFailed()) return
        try {
            onRefreshFailed(cause)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Failure observers must not replace the auth failure seen by callers.
        }
    }

    private suspend fun shouldNotifyRefreshFailed(): Boolean = mutex.withLock {
        val startMs = windowStartMs ?: currentTimeMillis().also { windowStartMs = it }
        if (failureNotifiedWindowStartMs == startMs) {
            false
        } else {
            failureNotifiedWindowStartMs = startMs
            true
        }
    }

    private fun NetworkRequest.withBearerToken(token: String): NetworkRequest =
        copy(headers = headers + Headers { set("Authorization", "Bearer $token") })

    private fun Instant.remainingMs(): Long =
        (toEpochMilliseconds() - currentTimeMillis()).coerceAtLeast(0L)

    private sealed interface RefreshOutcome {
        data class Success(val token: String) : RefreshOutcome
        data class Cancelled(val cause: CancellationException) : RefreshOutcome
        data class Failure(val cause: Exception) : RefreshOutcome
    }

    private data class RefreshSnapshot(
        val generation: Long,
        val activeFlightGeneration: Long?,
    )

    private data class RefreshFlight(
        val generation: Long,
        val refresh: Deferred<RefreshOutcome>,
    )
}

private const val DEFAULT_ERROR_BODY_BYTES: Int = 10 * 1024 * 1024

private fun ResponseBody.rawBodyOrNull(contentTypeOverride: String?): RawBody? =
    try {
        buffered(DEFAULT_ERROR_BODY_BYTES).rawBody(contentTypeOverride)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
