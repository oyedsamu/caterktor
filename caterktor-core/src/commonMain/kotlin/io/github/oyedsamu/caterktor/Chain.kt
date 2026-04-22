package io.github.oyedsamu.caterktor

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The context an [Interceptor] receives for a single request.
 *
 * A chain exposes the in-flight request, the attempt number, the optional
 * wall-clock deadline, and a single method — [proceed] — that dispatches to
 * the next stage of the pipeline.
 *
 * ## Lifecycle
 *
 * A chain instance is bound to one position in the pipeline and one logical
 * invocation of an interceptor. Calling [proceed] produces a *fresh* sub-chain
 * for the next interceptor; the chain you received is not reused.
 *
 * ## Cancellation and deadlines
 *
 * [deadline] is a wall-clock [Instant] that spans the entire logical request,
 * including retries and refresh waits. Interceptors that sleep, poll, or
 * schedule work must respect it. [kotlin.coroutines.cancellation.CancellationException]
 * propagates unchanged — never caught at the pipeline boundary.
 *
 * @see Interceptor
 * @see NetworkClient
 */
@ExperimentalCaterktor
public interface Chain {
    /**
     * The request currently entering this stage. May differ from the original
     * request submitted to the client if an upstream interceptor rewrote it.
     */
    public val request: NetworkRequest

    /**
     * The attempt number, 1-based. First attempt is `1`; a retry interceptor
     * (a [PrivilegedInterceptor]) bumps it for subsequent attempts.
     */
    public val attempt: Int

    /**
     * The wall-clock deadline for the logical request, spanning all attempts.
     * `null` means no deadline is set.
     */
    public val deadline: Instant?

    /**
     * Dispatch to the next stage with the given (possibly rewritten) request.
     *
     * Must be called at most once per chain instance. Calling it twice on the
     * same chain throws [IllegalStateException] unless the caller is a
     * [PrivilegedInterceptor]. Each call produces a fresh sub-chain, so a
     * privileged retry interceptor may re-dispatch without conflicting with
     * downstream interceptors' own at-most-once guarantee.
     */
    public suspend fun proceed(request: NetworkRequest): NetworkResponse
}

/**
 * The default [Chain] implementation. Walks a fixed, ordered list of
 * [Interceptor]s and terminates in a [Transport].
 *
 * Each instance points at one index in the pipeline. [proceed] advances to the
 * next index, constructing a fresh [RealChain] for the next interceptor, or
 * delegates to the [Transport] when the list is exhausted.
 *
 * The at-most-once rule for [proceed] is enforced per-instance. Because every
 * `proceed` call creates a new downstream chain, a [PrivilegedInterceptor]'s
 * re-entries do not corrupt the invariants of interceptors further down the
 * pipeline — they each see their own fresh chain on every retry.
 */
@OptIn(ExperimentalCaterktor::class)
internal class RealChain(
    override val request: NetworkRequest,
    override val attempt: Int,
    override val deadline: Instant?,
    private val interceptors: List<Interceptor>,
    private val index: Int,
    private val transport: Transport,
    private val callState: CallExecutionState?,
    private val timeoutConfig: TimeoutConfig?,
) : Chain {
    private var calls: Int = 0

    override suspend fun proceed(request: NetworkRequest): NetworkResponse =
        proceed(request, attempt)

    internal suspend fun proceed(request: NetworkRequest, attempt: Int): NetworkResponse {
        if (calls > 0) {
            // `index - 1` is the interceptor that received this chain and is
            // now re-invoking proceed. For the root chain (index == 0) there
            // is no such interceptor; re-entry there is always a bug.
            val caller: Interceptor? = if (index >= 1) interceptors[index - 1] else null
            check(caller is PrivilegedInterceptor) {
                val name = caller?.let { it::class.simpleName ?: "<anonymous>" } ?: "<root>"
                "Interceptor $name called Chain.proceed() more than once on the same chain. " +
                    "Only PrivilegedInterceptor (e.g. retry) may do so."
            }
        }
        calls += 1
        callState?.recordAttempt(attempt)

        if (index >= interceptors.size) {
            return executeTransportAttempt(request)
        }
        val next = RealChain(
            request = request,
            attempt = attempt,
            deadline = deadline,
            interceptors = interceptors,
            index = index + 1,
            transport = transport,
            callState = callState,
            timeoutConfig = timeoutConfig,
        )
        return interceptors[index].intercept(next)
    }

    private suspend fun executeTransportAttempt(request: NetworkRequest): NetworkResponse {
        val requestTimeoutMs = timeoutConfig?.requestTimeoutMs
        val deadlineRemainingMs = deadline?.remainingMs()
        val effectiveTimeoutMs = when {
            requestTimeoutMs != null && deadlineRemainingMs != null -> minOf(requestTimeoutMs, deadlineRemainingMs)
            requestTimeoutMs != null -> requestTimeoutMs
            deadlineRemainingMs != null -> deadlineRemainingMs
            else -> null
        }

        if (effectiveTimeoutMs == null) {
            return transport.execute(request)
        }

        return withTimeoutOrNull(effectiveTimeoutMs) {
            transport.execute(request)
        } ?: throw NetworkErrorException(
            NetworkError.Timeout(
                kind = if (deadlineRemainingMs != null && deadlineRemainingMs <= requestTimeoutMs.orMax()) {
                    TimeoutKind.Deadline
                } else {
                    TimeoutKind.Request
                },
            ),
        )
    }
}

private fun Instant.remainingMs(): Long =
    (toEpochMilliseconds() - Clock.System.now().toEpochMilliseconds()).coerceAtLeast(0L)

private fun Long?.orMax(): Long = this ?: Long.MAX_VALUE

/**
 * Dispatch to the next stage while explicitly recording [attempt].
 *
 * This escape hatch is for [PrivilegedInterceptor] implementations that issue
 * more than one downstream transport call for a single logical request, such as
 * retry and auth-refresh follow-up. Ordinary interceptors should call
 * [Chain.proceed].
 */
@ExperimentalCaterktor
@OptIn(ExperimentalCaterktor::class)
public suspend fun Chain.proceedForAttempt(
    request: NetworkRequest,
    attempt: Int,
): NetworkResponse {
    require(attempt >= 1) { "attempt must be >= 1, was $attempt" }
    return when (this) {
        is RealChain -> proceed(request, attempt)
        else -> {
            check(attempt == this.attempt) {
                "Explicit attempt dispatch requires CaterKtor's built-in Chain implementation."
            }
            proceed(request)
        }
    }
}
