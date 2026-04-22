package io.github.oyedsamu.caterktor

import kotlin.random.Random

/**
 * Decides whether and how long to wait before retrying a failed request.
 *
 * Implement this interface to supply custom retry logic. For most cases the
 * built-in [ExponentialBackoffPolicy] is sufficient.
 *
 * [RetryInterceptor] calls [shouldRetry] immediately after a failure.
 * If it returns `true`, [computeDelayMs] is called to determine the wait
 * before the next attempt.
 *
 * ## Thread safety
 * Implementations must be stateless or thread-safe — the same instance is
 * used concurrently for all in-flight requests.
 */
@ExperimentalCaterktor
public interface RetryPolicy {

    /**
     * Return `true` if the request should be retried.
     *
     * @param attempt      The attempt that just failed, 1-based.
     * @param request      The request that was attempted.
     * @param response     The raw response, if the transport returned one
     *   (e.g. a 503). `null` if the transport threw before receiving any
     *   response (timeout, connection failure).
     * @param error        The [NetworkError] that caused the failure.
     */
    public fun shouldRetry(
        attempt: Int,
        request: NetworkRequest,
        response: NetworkResponse?,
        error: NetworkError,
    ): Boolean

    /**
     * Return how many milliseconds to wait before the next attempt.
     *
     * Only called when [shouldRetry] returned `true`. Implementations may
     * inspect [response] for a `Retry-After` header to honour server-side
     * back-pressure.
     *
     * @param attempt   The attempt that just failed, 1-based.
     * @param request   The request that was attempted.
     * @param response  The raw response, if available.
     * @param error     The [NetworkError] that caused the failure.
     */
    public suspend fun computeDelayMs(
        attempt: Int,
        request: NetworkRequest,
        response: NetworkResponse?,
        error: NetworkError,
    ): Long
}

/**
 * Exponential back-off with full jitter — the recommended default for most
 * production scenarios.
 *
 * Delay formula (before jitter): `min(baseDelayMs * 2^(attempt-1), maxDelayMs)`
 * Jitter: uniformly random value in `[0, computed]`, controlled by [jitterFactor].
 *
 * By default retries on [NetworkError.Timeout] and [NetworkError.ConnectionFailed].
 * HTTP error responses (4xx/5xx) are **not** retried by default — pass a custom
 * [retryOnHttpStatus] predicate to opt in. Note that retrying non-idempotent
 * methods on 5xx can cause duplicate side-effects; [RetryInterceptor] guards
 * against this by default.
 *
 * A `Retry-After` header in the response overrides the computed delay.
 *
 * @property baseDelayMs   Initial delay in milliseconds. Default: 500 ms.
 * @property maxDelayMs    Upper bound on the delay before jitter. Default: 30 s.
 * @property jitterFactor  Fraction of the computed delay to randomize over
 *   `[0, jitterFactor]`. Must be in `[0.0, 1.0]`. Default: 0.25.
 * @property retryOnHttpStatus Predicate that returns `true` if the HTTP status
 *   should trigger a retry. Default: retries on 503 only.
 */
@ExperimentalCaterktor
public class ExponentialBackoffPolicy(
    public val baseDelayMs: Long = 500L,
    public val maxDelayMs: Long = 30_000L,
    public val jitterFactor: Double = 0.25,
    public val retryOnHttpStatus: (HttpStatus) -> Boolean = { it.code == 503 },
) : RetryPolicy {

    init {
        require(baseDelayMs > 0) { "baseDelayMs must be positive, was $baseDelayMs" }
        require(maxDelayMs >= baseDelayMs) { "maxDelayMs ($maxDelayMs) must be >= baseDelayMs ($baseDelayMs)" }
        require(jitterFactor in 0.0..1.0) { "jitterFactor must be in [0.0, 1.0], was $jitterFactor" }
    }

    override fun shouldRetry(
        attempt: Int,
        request: NetworkRequest,
        response: NetworkResponse?,
        error: NetworkError,
    ): Boolean = when (error) {
        is NetworkError.Timeout -> true
        is NetworkError.ConnectionFailed -> true
        is NetworkError.Http -> response != null && retryOnHttpStatus(error.status)
        else -> false
    }

    override suspend fun computeDelayMs(
        attempt: Int,
        request: NetworkRequest,
        response: NetworkResponse?,
        error: NetworkError,
    ): Long {
        // Honour Retry-After header if present (seconds or HTTP-date not supported yet — integers only)
        val retryAfterHeader = response?.headers?.get("Retry-After")
        if (retryAfterHeader != null) {
            val retryAfterSeconds = retryAfterHeader.trim().toLongOrNull()
            if (retryAfterSeconds != null && retryAfterSeconds > 0) {
                return retryAfterSeconds * 1_000L
            }
        }

        // Exponential back-off: base * 2^(attempt-1), capped at max
        val exponential = minOf(baseDelayMs * (1L shl (attempt - 1).coerceAtMost(30)), maxDelayMs)
        // Full jitter: uniform in [0, exponential * jitterFactor]
        val jitter = (exponential * jitterFactor * Random.nextDouble()).toLong()
        return exponential - (exponential * jitterFactor).toLong() + jitter
    }
}
