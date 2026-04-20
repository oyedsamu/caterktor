package io.github.oyedsamu.caterktor

/**
 * A single stage in the CaterKtor request pipeline.
 *
 * An interceptor observes or transforms a [NetworkRequest], calls [Chain.proceed]
 * to delegate to the next stage, and observes or transforms the resulting
 * [NetworkResponse]. Every CaterKtor subsystem that participates in the request
 * lifecycle — authentication, retry, logging, metrics, envelope handling — is
 * implemented as an interceptor.
 *
 * ## Ordering
 *
 * Interceptors run in the order they are added to the builder. The request
 * traverses them top-down; the response traverses them bottom-up. Ordering is
 * explicit and printable via [NetworkClient.describePipeline].
 *
 * ## Contract
 *
 * Implementations must:
 *  - call [Chain.proceed] **at most once**, unless they are a
 *    [PrivilegedInterceptor] (see the retry subsystem);
 *  - honor [Chain.deadline] — reject or shorten work that cannot complete in time;
 *  - allow [kotlinx.coroutines.CancellationException] to propagate without
 *    catching, wrapping, or converting it to a result.
 *
 * ## Example
 *
 * ```kotlin
 * class UserAgentInterceptor(private val agent: String) : Interceptor {
 *     override suspend fun intercept(chain: Chain): NetworkResponse {
 *         val request = chain.request.copy(
 *             headers = chain.request.headers + Headers { add("User-Agent", agent) },
 *         )
 *         return chain.proceed(request)
 *     }
 * }
 * ```
 */
@ExperimentalCaterktor
public interface Interceptor {
    public suspend fun intercept(chain: Chain): NetworkResponse
}

/**
 * An interceptor that is permitted to call [Chain.proceed] more than once on the
 * same [Chain] instance.
 *
 * This marker exists for interceptors whose semantics require issuing multiple
 * downstream calls for a single logical request — notably retry. Normal
 * interceptors that invoke `proceed` twice indicate a pipeline bug and will
 * fail fast with an [IllegalStateException].
 *
 * ## When to use
 *
 * Implement this interface **only** when the interceptor genuinely owns the
 * decision to re-issue a call — e.g. retry, follow-up after redirect, or
 * single-flight auth refresh re-dispatch. Logging, metrics, and envelope
 * handling never need this privilege.
 *
 * Each call to `proceed` still produces a fresh downstream sub-chain; state on
 * the *caller's* chain (e.g. the call count used to enforce the at-most-once
 * rule for normal interceptors) is the only thing shared across re-entries.
 */
@ExperimentalCaterktor
public interface PrivilegedInterceptor : Interceptor
