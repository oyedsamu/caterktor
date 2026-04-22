package io.github.oyedsamu.caterktor

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * A [PrivilegedInterceptor] that retries failed requests according to a
 * [RetryPolicy].
 *
 * ## What is retried
 *
 * By default only **idempotent** methods (GET, HEAD, DELETE, PUT, OPTIONS) are
 * retried. Set [retryNonIdempotent] to `true` to also retry POST and PATCH —
 * only do this if your server guarantees idempotency at the application level
 * (e.g. via idempotency keys).
 *
 * ## Retry triggers
 *
 * The [RetryPolicy] receives both transport errors ([NetworkError]) and raw
 * [NetworkResponse] values (e.g. 503). [RetryInterceptor] calls
 * [RetryPolicy.shouldRetry] for every failure; if `true`, it waits for
 * [RetryPolicy.computeDelayMs] milliseconds before re-dispatching.
 *
 * ## Cancellation
 *
 * [CancellationException] propagates unchanged — retry loops do not swallow
 * cancellation.
 *
 * ## Deadlines
 *
 * If [Chain.deadline] is set, [RetryInterceptor] will not initiate a retry
 * whose computed delay would push past the deadline.
 *
 * ## Example
 *
 * ```kotlin
 * val client = CaterKtor {
 *     transport = OkHttpTransport()
 *     addInterceptor(RetryInterceptor(maxAttempts = 3))
 * }
 * ```
 *
 * @property maxAttempts     Maximum number of attempts including the first.
 *   Must be at least 1. Default: 3.
 * @property policy          The [RetryPolicy] that controls back-off and
 *   retry eligibility. Default: [ExponentialBackoffPolicy].
 * @property retryNonIdempotent If `true`, non-idempotent methods (POST, PATCH)
 *   are also retried. Default: `false`.
 */
@ExperimentalCaterktor
public class RetryInterceptor(
    public val maxAttempts: Int = 3,
    public val policy: RetryPolicy = ExponentialBackoffPolicy(),
    public val retryNonIdempotent: Boolean = false,
) : PrivilegedInterceptor {

    init {
        require(maxAttempts >= 1) { "maxAttempts must be >= 1, was $maxAttempts" }
    }

    override suspend fun intercept(chain: Chain): NetworkResponse {
        val request = chain.request

        // Non-idempotent guard: if the method is not safe to retry and the
        // caller has not opted in, delegate immediately without retry logic.
        if (!retryNonIdempotent && !request.method.isIdempotent) {
            return chain.proceed(request)
        }

        var attempt = 1
        while (true) {
            val response: NetworkResponse?
            val error: NetworkError?

            try {
                val raw = chain.proceed(request)
                // Check if the policy wants to retry a successful-transport response (e.g. 503)
                if (attempt < maxAttempts) {
                    val httpError = if (raw.status.isClientError || raw.status.isServerError) {
                        NetworkError.Http(
                            status = raw.status,
                            headers = raw.headers,
                            body = ErrorBody(
                                raw = RawBody(raw.body, raw.headers["Content-Type"]),
                                parsed = null,
                            ),
                        )
                    } else null

                    if (httpError != null && policy.shouldRetry(attempt, request, raw, httpError)) {
                        val delayMs = policy.computeDelayMs(attempt, request, raw, httpError)
                        if (deadlineAllowsRetry(chain, delayMs)) {
                            delay(delayMs)
                            attempt++
                            continue
                        }
                    }
                }
                return raw
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkErrorException) {
                response = null
                error = e.error
            }

            // We have a transport error; check retry eligibility
            if (attempt >= maxAttempts || !policy.shouldRetry(attempt, request, response, error)) {
                throw NetworkErrorException(error)
            }

            val delayMs = policy.computeDelayMs(attempt, request, response, error)
            if (!deadlineAllowsRetry(chain, delayMs)) {
                throw NetworkErrorException(error)
            }

            delay(delayMs)
            attempt++
        }
    }

    private fun deadlineAllowsRetry(chain: Chain, delayMs: Long): Boolean {
        val deadline = chain.deadline ?: return true
        val nowMs = kotlin.time.Clock.System.now().toEpochMilliseconds()
        val deadlineMs = deadline.toEpochMilliseconds()
        return nowMs + delayMs < deadlineMs
    }
}
