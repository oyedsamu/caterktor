package io.github.oyedsamu.caterktor

import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Instant

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
 * HTTP 502, 503, and 504 are retried by default. Note that retrying
 * non-idempotent methods on 5xx can cause duplicate side-effects;
 * [RetryInterceptor] guards against this by default.
 *
 * A `Retry-After` header in the response overrides the computed delay. Both
 * delay-seconds (including decimal seconds such as `0.2`) and IMF-fixdate
 * HTTP dates are supported.
 *
 * @property baseDelayMs   Initial delay in milliseconds. Default: 500 ms.
 * @property maxDelayMs    Upper bound on the delay before jitter. Default: 30 s.
 * @property jitterFactor  Fraction of the computed delay used as the random
 *   upper bound. Must be in `[0.0, 1.0]`. Default: 1.0, which is full jitter.
 * @property retryOnHttpStatus Predicate that returns `true` if the HTTP status
 *   should trigger a retry. Default: retries on 502, 503, and 504.
 * @property randomDouble Source of random values in `[0.0, 1.0)`, injectable
 *   for deterministic tests.
 * @property currentTimeMillis Wall-clock source for `Retry-After` HTTP-date
 *   parsing, injectable for deterministic tests.
 */
@ExperimentalCaterktor
public class ExponentialBackoffPolicy(
    public val baseDelayMs: Long = 500L,
    public val maxDelayMs: Long = 30_000L,
    public val jitterFactor: Double = 1.0,
    public val retryOnHttpStatus: (HttpStatus) -> Boolean = { status ->
        status.code == 502 || status.code == 503 || status.code == 504
    },
    public val randomDouble: () -> Double = { Random.nextDouble() },
    public val currentTimeMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
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
        val retryAfterHeader = response?.headers?.get("Retry-After")
        retryAfterHeader?.retryAfterDelayMs(currentTimeMillis())?.let { delayMs ->
            return delayMs
        }

        // Exponential back-off: base * 2^(attempt-1), capped at max
        val exponential = minOf(baseDelayMs * (1L shl (attempt - 1).coerceAtMost(30)), maxDelayMs)
        // Full jitter: uniform in [0, exponential] by default.
        val jitterCeiling = (exponential * jitterFactor).toLong()
        val random = randomDouble().coerceIn(0.0, 0.999_999_999)
        return (jitterCeiling * random).toLong()
    }
}

private fun String.retryAfterDelayMs(nowMs: Long): Long? {
    val trimmed = trim()
    if (trimmed.isEmpty()) return null

    val delaySeconds = trimmed.toDoubleOrNull()
    if (delaySeconds != null) {
        return if (delaySeconds >= 0.0) (delaySeconds * 1_000.0).toLong() else null
    }

    val epochMs = trimmed.parseImfFixdateEpochMs() ?: return null
    return (epochMs - nowMs).coerceAtLeast(0L)
}

private fun String.parseImfFixdateEpochMs(): Long? {
    val parts = trim().split(Regex("\\s+"))
    if (parts.size != 6) return null
    if (!parts[0].endsWith(",")) return null
    if (!parts[5].equals("GMT", ignoreCase = true)) return null

    val day = parts[1].toIntOrNull()?.takeIf { it in 1..31 } ?: return null
    val month = monthNumber(parts[2]) ?: return null
    val year = parts[3].toIntOrNull()?.takeIf { it >= 0 } ?: return null
    val timeParts = parts[4].split(':')
    if (timeParts.size != 3) return null
    val hour = timeParts[0].toIntOrNull()?.takeIf { it in 0..23 } ?: return null
    val minute = timeParts[1].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
    val second = timeParts[2].toIntOrNull()?.takeIf { it in 0..59 } ?: return null

    val iso = buildString {
        append(year.toString().padStart(4, '0'))
        append('-')
        append(month.toString().padStart(2, '0'))
        append('-')
        append(day.toString().padStart(2, '0'))
        append('T')
        append(hour.toString().padStart(2, '0'))
        append(':')
        append(minute.toString().padStart(2, '0'))
        append(':')
        append(second.toString().padStart(2, '0'))
        append('Z')
    }

    return try {
        Instant.parse(iso).toEpochMilliseconds()
    } catch (_: IllegalArgumentException) {
        null
    }
}

private fun monthNumber(month: String): Int? =
    when (month.lowercase()) {
        "jan" -> 1
        "feb" -> 2
        "mar" -> 3
        "apr" -> 4
        "may" -> 5
        "jun" -> 6
        "jul" -> 7
        "aug" -> 8
        "sep" -> 9
        "oct" -> 10
        "nov" -> 11
        "dec" -> 12
        else -> null
    }
