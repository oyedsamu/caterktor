package io.github.oyedsamu.caterktor

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay

/**
 * A [PrivilegedInterceptor] that retries failed requests according to a
 * [RetryPolicy].
 *
 * ## What is retried
 *
 * By default only **idempotent** methods (GET, HEAD, DELETE, PUT, OPTIONS) are
 * retried. Set [retryNonIdempotent] to `true` to permit POST and PATCH retries,
 * but those requests still need an `Idempotency-Key` header.
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
 *   are also retried — but **only** when the request carries an `Idempotency-Key`
 *   header. A POST or PATCH without that header will throw [IllegalStateException]
 *   at runtime to prevent silent duplicate writes. Default: `false`.
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

        // Non-idempotent guard: POST/PATCH require both caller opt-in and an
        // Idempotency-Key header. Fail loudly instead of silently skipping retry
        // — a missing key with retryNonIdempotent=true is always a misconfiguration.
        if (!request.method.isIdempotent) {
            if (!retryNonIdempotent) return chain.proceed(request)
            check("Idempotency-Key" in request.headers) {
                "RetryInterceptor: retryNonIdempotent=true but ${request.method.name} request " +
                    "to ${request.url} has no Idempotency-Key header. Add the header or set " +
                    "retryNonIdempotent=false to avoid retrying non-idempotent requests."
            }
        }

        var attempt = chain.attempt
        while (true) {
            val response: NetworkResponse?
            val error: NetworkError?

            try {
                val raw = chain.proceedForAttempt(request, attempt)
                // Check if the policy wants to retry a successful-transport response (e.g. 503)
                if (attempt < maxAttempts) {
                    val httpError = if (raw.status.isClientError || raw.status.isServerError) {
                        NetworkError.Http(
                            status = raw.status,
                            headers = raw.headers,
                            body = ErrorBody(
                                raw = raw.body.rawBodyOrNull(
                                    contentTypeOverride = raw.headers["Content-Type"],
                                ),
                                parsed = null,
                            ),
                        )
                    } else null

                    if (httpError != null && policy.shouldRetry(attempt, request, raw, httpError)) {
                        val delayMs = policy.computeDelayMs(attempt, request, raw, httpError)
                        val deadlineError = deadlineErrorIfRetryWouldMiss(chain, delayMs)
                        if (deadlineError == null) {
                            delay(delayMs)
                            attempt++
                            continue
                        } else {
                            throw NetworkErrorException(deadlineError)
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
            val deadlineError = deadlineErrorIfRetryWouldMiss(chain, delayMs)
            if (deadlineError != null) {
                throw NetworkErrorException(deadlineError)
            }

            delay(delayMs)
            attempt++
        }
    }

    private fun deadlineErrorIfRetryWouldMiss(chain: Chain, delayMs: Long): NetworkError.Timeout? {
        val deadline = chain.deadline ?: return null
        val nowMs = kotlin.time.Clock.System.now().toEpochMilliseconds()
        val deadlineMs = deadline.toEpochMilliseconds()
        return if (nowMs + delayMs < deadlineMs) {
            null
        } else {
            NetworkError.Timeout(TimeoutKind.Deadline)
        }
    }

    private fun NetworkRequest.isRetryableByMethod(): Boolean =
        method.isIdempotent || (retryNonIdempotent && "Idempotency-Key" in headers)
}
