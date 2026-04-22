package io.github.oyedsamu.caterktor

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Per-logical-call state shared by the internal pipeline while a typed helper
 * is executing.
 *
 * This is deliberately internal: public interceptors see [Chain.attempt], while
 * [NetworkClient] uses this state to report final attempt metadata on
 * [NetworkResult] and [NetworkEvent].
 */
internal class CallExecutionState : AbstractCoroutineContextElement(Key) {
    internal companion object Key : CoroutineContext.Key<CallExecutionState>

    private var maxAttempt: Int = 1

    internal val attempts: Int
        get() = maxAttempt

    internal fun recordAttempt(attempt: Int): Unit {
        if (attempt > maxAttempt) maxAttempt = attempt
    }
}
