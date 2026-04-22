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
import io.github.oyedsamu.caterktor.TimeoutKind
import io.github.oyedsamu.caterktor.proceedForAttempt
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
public class AuthRefreshInterceptor(
    public val tokenProvider: suspend () -> String,
    public val refreshToken: suspend () -> String,
    public val budget: RefreshBudget = RefreshBudget(),
    public val onRefreshFailed: suspend (Throwable) -> Unit = {},
) : PrivilegedInterceptor, CloseableInterceptor {

    private val mutex = Mutex()
    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var inFlightRefresh: Deferred<RefreshOutcome>? = null
    private var windowStartMs: Long = 0L
    private var refreshesInWindow: Int = 0

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
        val response = chain.proceed(authorizedRequest)
        if (response.status != HttpStatus.Unauthorized) {
            return response
        }

        val refreshedToken = refreshAfterUnauthorized(chain, response)
        return chain.proceedForAttempt(
            originalRequest.withBearerToken(refreshedToken),
            attempt = chain.attempt + 1,
        )
    }

    private suspend fun refreshAfterUnauthorized(
        chain: Chain,
        response: NetworkResponse,
    ): String {
        val refresh = try {
            singleFlightRefresh()
        } catch (e: AuthRefreshBudgetExceededException) {
            failRefresh(response, e)
        }

        return when (val outcome = refresh.awaitRespecting(chain.deadline)) {
            is RefreshOutcome.Success -> outcome.token
            is RefreshOutcome.Cancelled -> throw outcome.cause
            is RefreshOutcome.Failure -> failRefresh(response, AuthRefreshFailedException(outcome.cause))
        }
    }

    private suspend fun singleFlightRefresh(): Deferred<RefreshOutcome> = mutex.withLock {
        inFlightRefresh?.takeIf { it.isActive }?.let { return@withLock it }
        inFlightRefresh = null

        consumeBudget()
        val refresh = refreshScope.async {
            try {
                RefreshOutcome.Success(refreshToken())
            } catch (e: CancellationException) {
                RefreshOutcome.Cancelled(e)
            } catch (t: Exception) {
                RefreshOutcome.Failure(t)
            }
        }
        inFlightRefresh = refresh
        refresh
    }

    private fun consumeBudget() {
        val nowMs = Clock.System.now().toEpochMilliseconds()
        if (windowStartMs == 0L || nowMs - windowStartMs >= budget.windowMs) {
            windowStartMs = nowMs
            refreshesInWindow = 0
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
                body = ErrorBody(raw = response.rawBody(), parsed = null),
                cause = cause,
            ),
        )
    }

    private suspend fun notifyRefreshFailed(cause: Throwable) {
        try {
            onRefreshFailed(cause)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Failure observers must not replace the auth failure seen by callers.
        }
    }

    private fun NetworkRequest.withBearerToken(token: String): NetworkRequest =
        copy(headers = headers + Headers { set("Authorization", "Bearer $token") })

    private fun Instant.remainingMs(): Long =
        (toEpochMilliseconds() - Clock.System.now().toEpochMilliseconds()).coerceAtLeast(0L)

    private sealed interface RefreshOutcome {
        data class Success(val token: String) : RefreshOutcome
        data class Cancelled(val cause: CancellationException) : RefreshOutcome
        data class Failure(val cause: Exception) : RefreshOutcome
    }
}
