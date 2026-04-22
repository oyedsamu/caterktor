package io.github.oyedsamu.caterktor.auth

import io.github.oyedsamu.caterktor.CaterKtorKeys
import io.github.oyedsamu.caterktor.Chain
import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import io.github.oyedsamu.caterktor.Headers
import io.github.oyedsamu.caterktor.Interceptor
import io.github.oyedsamu.caterktor.NetworkResponse

/**
 * An [Interceptor] that adds an `Authorization: Bearer <token>` header to every outgoing request.
 *
 * The token is obtained by calling [tokenProvider] inside the interceptor pipeline (a coroutine
 * context) so it may suspend — e.g. to read from a secure store or a `StateFlow`.
 *
 * A static token convenience constructor is provided.
 *
 * The header is **not added** if the request already contains an `Authorization` header
 * (per-request header wins).
 *
 * The header is **not added** if the request carries `CaterKtorKeys.SKIP_AUTH = true` in its
 * [io.github.oyedsamu.caterktor.NetworkRequest.attributes] — this prevents infinite loops when
 * an auth-refresh interceptor issues its own unauthenticated request through the same client.
 *
 * For non-refreshing auth, register this interceptor after retry if you need a
 * fresh token lookup for each retry attempt. For 401 refresh flows, prefer
 * [AuthRefreshInterceptor], which owns both token injection and the refresh
 * follow-up retry.
 */
@ExperimentalCaterktor
public class BearerAuthInterceptor(
    /**
     * Called once per request to obtain the current Bearer token.
     * May suspend — e.g. to await a refreshed token from a store.
     * Must not throw [kotlinx.coroutines.CancellationException] (or rather: if it does,
     * propagate it — never catch it).
     */
    public val tokenProvider: suspend () -> String,
) : Interceptor {

    /**
     * Convenience constructor for a static, non-rotating token.
     *
     * @param token The fixed token string. No suspension occurs.
     */
    public constructor(token: String) : this({ token })

    override suspend fun intercept(chain: Chain): NetworkResponse {
        val request = chain.request

        // Honour explicit skip-auth tag (e.g. set by an auth-refresh interceptor to avoid loops)
        if (request.attributes.getOrNull(CaterKtorKeys.SKIP_AUTH) == true) {
            return chain.proceed(request)
        }

        // If the request already carries an Authorization header, respect it and skip injection
        if ("Authorization" in request.headers) {
            return chain.proceed(request)
        }

        val token = tokenProvider()
        val authHeaders = Headers { set("Authorization", "Bearer $token") }
        return chain.proceed(request.copy(headers = request.headers + authHeaders))
    }
}
