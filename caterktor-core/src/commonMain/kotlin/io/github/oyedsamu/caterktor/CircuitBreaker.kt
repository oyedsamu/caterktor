package io.github.oyedsamu.caterktor

import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Circuit breaker state.
 */
public enum class CircuitBreakerState {
    Closed,
    Open,
    HalfOpen,
}

/**
 * Interceptor that protects downstream transports from repeated failures.
 *
 * The breaker starts [CircuitBreakerState.Closed]. After [failureThreshold]
 * consecutive failures it moves to [CircuitBreakerState.Open] and rejects
 * calls until [openDurationMs] has elapsed. The next allowed call moves it to
 * [CircuitBreakerState.HalfOpen]; success closes the breaker, while failure
 * opens it again.
 *
 * State transitions are emitted as [NetworkEvent.CircuitBreakerTransition].
 */
@ExperimentalCaterktor
public class CircuitBreaker(
    public val name: String = "default",
    public val failureThreshold: Int = 10,
    public val openDurationMs: Long = 30_000L,
    public val halfOpenMaxCalls: Int = 1,
    public val shouldRecordFailure: (NetworkResponse?, NetworkError?) -> Boolean = DefaultFailurePredicate,
    public val currentTimeMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : Interceptor {

    private val mutex = Mutex()
    private var state: CircuitBreakerState = CircuitBreakerState.Closed
    private var consecutiveFailures: Int = 0
    private var openedAtMs: Long = 0L
    private var halfOpenInFlight: Int = 0
    private var halfOpenSuccesses: Int = 0

    init {
        require(name.isNotBlank()) { "name must not be blank" }
        require(failureThreshold >= 1) { "failureThreshold must be >= 1, was $failureThreshold" }
        require(openDurationMs >= 0L) { "openDurationMs must be >= 0, was $openDurationMs" }
        require(halfOpenMaxCalls >= 1) { "halfOpenMaxCalls must be >= 1, was $halfOpenMaxCalls" }
    }

    public val currentState: CircuitBreakerState
        get() = state

    override suspend fun intercept(chain: Chain): NetworkResponse {
        val rejectedState = acquirePermit(chain)
        if (rejectedState != null) {
            throw NetworkErrorException(NetworkError.CircuitOpen(name, rejectedState))
        }

        return try {
            val response = chain.proceed(chain.request)
            recordResult(chain, shouldRecordFailure(response, null))
            response
        } catch (e: CancellationException) {
            throw e
        } catch (e: NetworkErrorException) {
            recordResult(chain, shouldRecordFailure(null, e.error))
            throw e
        } catch (e: Throwable) {
            recordResult(chain, true)
            throw e
        }
    }

    private suspend fun acquirePermit(chain: Chain): CircuitBreakerState? {
        val nowMs = currentTimeMillis()
        var transition: StateTransition? = null
        var rejectedState: CircuitBreakerState? = null

        mutex.withLock {
            when (state) {
                CircuitBreakerState.Closed -> {
                    // Calls flow normally.
                }
                CircuitBreakerState.Open -> {
                    if (nowMs - openedAtMs >= openDurationMs) {
                        transition = transitionTo(CircuitBreakerState.HalfOpen, nowMs)
                        halfOpenInFlight += 1
                    } else {
                        rejectedState = CircuitBreakerState.Open
                    }
                }
                CircuitBreakerState.HalfOpen -> {
                    if (halfOpenInFlight < halfOpenMaxCalls) {
                        halfOpenInFlight += 1
                    } else {
                        rejectedState = CircuitBreakerState.HalfOpen
                    }
                }
            }
        }

        transition?.emit(chain)
        return rejectedState
    }

    private suspend fun recordResult(chain: Chain, failed: Boolean) {
        val nowMs = currentTimeMillis()
        var transition: StateTransition? = null

        mutex.withLock {
            when (state) {
                CircuitBreakerState.Closed -> {
                    if (failed) {
                        consecutiveFailures += 1
                        if (consecutiveFailures >= failureThreshold) {
                            transition = transitionTo(CircuitBreakerState.Open, nowMs)
                        }
                    } else {
                        consecutiveFailures = 0
                    }
                }
                CircuitBreakerState.Open -> {
                    // Another in-flight call may have opened the breaker first.
                }
                CircuitBreakerState.HalfOpen -> {
                    halfOpenInFlight = (halfOpenInFlight - 1).coerceAtLeast(0)
                    if (failed) {
                        transition = transitionTo(CircuitBreakerState.Open, nowMs)
                    } else {
                        halfOpenSuccesses += 1
                        if (halfOpenSuccesses >= halfOpenMaxCalls || halfOpenInFlight == 0) {
                            transition = transitionTo(CircuitBreakerState.Closed, nowMs)
                        }
                    }
                }
            }
        }

        transition?.emit(chain)
    }

    private fun transitionTo(next: CircuitBreakerState, nowMs: Long): StateTransition? {
        val previous = state
        if (previous == next) return null
        state = next
        when (next) {
            CircuitBreakerState.Closed -> {
                consecutiveFailures = 0
                halfOpenInFlight = 0
                halfOpenSuccesses = 0
            }
            CircuitBreakerState.Open -> {
                openedAtMs = nowMs
                consecutiveFailures = 0
                halfOpenInFlight = 0
                halfOpenSuccesses = 0
            }
            CircuitBreakerState.HalfOpen -> {
                halfOpenInFlight = 0
                halfOpenSuccesses = 0
            }
        }
        return StateTransition(previous, next)
    }

    private fun StateTransition.emit(chain: Chain) {
        chain.emitEvent(
            NetworkEvent.CircuitBreakerTransition(
                requestId = chain.requestId,
                name = name,
                from = from,
                to = to,
            ),
        )
    }

    private data class StateTransition(
        val from: CircuitBreakerState,
        val to: CircuitBreakerState,
    )

    public companion object {
        public val DefaultFailurePredicate: (NetworkResponse?, NetworkError?) -> Boolean = { response, error ->
            when {
                response?.status?.isServerError == true -> true
                error is NetworkError.Http -> error.status.isServerError
                error is NetworkError.Timeout -> true
                error is NetworkError.ConnectionFailed -> true
                else -> false
            }
        }
    }
}
