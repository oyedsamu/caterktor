package io.github.oyedsamu.caterktor.auth

import io.github.oyedsamu.caterktor.CaterKtorBuilder
import io.github.oyedsamu.caterktor.CaterKtorDsl
import io.github.oyedsamu.caterktor.ExperimentalCaterktor

/**
 * Configure authentication interceptors for a CaterKtor client.
 *
 * If only [AuthBuilder.bearer] is configured, this installs a
 * [BearerAuthInterceptor]. If [AuthBuilder.refresh] is also configured, this
 * installs [AuthRefreshInterceptor], which owns both bearer token injection and
 * the 401 refresh retry.
 *
 * ```kotlin
 * val client = CaterKtor {
 *     auth {
 *         bearer {
 *             tokenProvider { tokenStore.accessToken() }
 *         }
 *         refresh {
 *             refreshToken { tokenStore.refreshAccessToken() }
 *         }
 *     }
 * }
 * ```
 */
@ExperimentalCaterktor
public fun CaterKtorBuilder.auth(block: AuthBuilder.() -> Unit): CaterKtorBuilder = apply {
    AuthBuilder().apply(block).installInto(this)
}

/**
 * Builder for CaterKtor authentication.
 */
@ExperimentalCaterktor
@CaterKtorDsl
public class AuthBuilder {

    private var bearer: BearerAuthBuilder? = null
    private var refresh: RefreshAuthBuilder? = null

    /**
     * Configure bearer token injection.
     */
    public fun bearer(block: BearerAuthBuilder.() -> Unit): AuthBuilder = apply {
        bearer = BearerAuthBuilder().apply(block)
    }

    /**
     * Configure bearer token injection with a static token.
     */
    public fun bearer(token: String): AuthBuilder = apply {
        bearer = BearerAuthBuilder().apply { token(token) }
    }

    /**
     * Configure 401 refresh handling for bearer auth.
     */
    public fun refresh(block: RefreshAuthBuilder.() -> Unit): AuthBuilder = apply {
        refresh = RefreshAuthBuilder().apply(block)
    }

    internal fun installInto(builder: CaterKtorBuilder) {
        val bearer = checkNotNull(bearer) {
            "auth { bearer { ... } } must be configured before installing auth."
        }
        val tokenProvider = bearer.buildTokenProvider()
        val refresh = refresh

        if (refresh == null) {
            builder.addInterceptor(BearerAuthInterceptor(tokenProvider))
        } else {
            builder.addInterceptor(
                AuthRefreshInterceptor(
                    tokenProvider = tokenProvider,
                    refreshToken = refresh.buildRefreshTokenProvider(),
                    budget = refresh.budget,
                    onRefreshFailed = refresh.onRefreshFailed,
                ),
            )
        }
    }
}

/**
 * Builder for bearer token injection.
 */
@ExperimentalCaterktor
@CaterKtorDsl
public class BearerAuthBuilder {

    private var tokenProvider: (suspend () -> String)? = null

    /**
     * Use [token] as a static bearer token.
     */
    public fun token(token: String): BearerAuthBuilder = apply {
        tokenProvider = { token }
    }

    /**
     * Provide the bearer token for each request.
     */
    public fun tokenProvider(provider: suspend () -> String): BearerAuthBuilder = apply {
        tokenProvider = provider
    }

    internal fun buildTokenProvider(): suspend () -> String =
        checkNotNull(tokenProvider) {
            "auth { bearer { tokenProvider { ... } } } or auth { bearer(\"token\") } must be configured."
        }
}

/**
 * Builder for bearer token refresh after a 401 response.
 */
@ExperimentalCaterktor
@CaterKtorDsl
public class RefreshAuthBuilder {

    private var refreshTokenProvider: (suspend () -> String)? = null
    internal var onRefreshFailed: suspend (Throwable) -> Unit = {}

    /**
     * Refresh budget. Defaults to one refresh per 60 seconds.
     */
    public var budget: RefreshBudget = RefreshBudget()

    /**
     * Provide a fresh bearer token when the server returns 401.
     */
    public fun refreshToken(provider: suspend () -> String): RefreshAuthBuilder = apply {
        refreshTokenProvider = provider
    }

    /**
     * Set the refresh budget.
     */
    public fun budget(maxRefreshes: Int = 1, windowMs: Long = 60_000L): RefreshAuthBuilder = apply {
        budget = RefreshBudget(maxRefreshes = maxRefreshes, windowMs = windowMs)
    }

    /**
     * Observe refresh failures without replacing the caller-visible auth error.
     */
    public fun onRefreshFailed(observer: suspend (Throwable) -> Unit): RefreshAuthBuilder = apply {
        onRefreshFailed = observer
    }

    internal fun buildRefreshTokenProvider(): suspend () -> String =
        checkNotNull(refreshTokenProvider) {
            "auth { refresh { refreshToken { ... } } } must be configured when refresh is enabled."
        }
}
