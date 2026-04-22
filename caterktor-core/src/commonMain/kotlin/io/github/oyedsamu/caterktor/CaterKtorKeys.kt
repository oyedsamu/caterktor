package io.github.oyedsamu.caterktor

/**
 * Well-known keys for the [NetworkRequest.tags] bag used to communicate
 * per-request overrides to the CaterKtor framework.
 *
 * Pass these as tag keys to any typed call helper:
 * ```kotlin
 * client.get<MyType>(
 *     url = "endpoint",
 *     tags = mapOf(CaterKtorKeys.UNWRAPPER to DataFieldUnwrapper("data")),
 * )
 * ```
 *
 * Per-request values take precedence over the per-client defaults configured
 * in [CaterKtorBuilder].
 */
public object CaterKtorKeys {

    /**
     * Tag key for a per-request [ResponseUnwrapper] override.
     * Value must be an instance of [ResponseUnwrapper].
     */
    public const val UNWRAPPER: String = "caterktor.unwrapper"

    /**
     * Tag key for a per-request [RequestEnveloper] override.
     * Value must be an instance of [RequestEnveloper].
     */
    public const val ENVELOPER: String = "caterktor.enveloper"

    /**
     * Tag key to signal that a request should bypass auth interceptors.
     *
     * Set this to `true` in [NetworkRequest.tags] to prevent [BearerAuthInterceptor]
     * (and the upcoming H3 auth-refresh interceptor) from adding or refreshing credentials:
     *
     * ```kotlin
     * client.get<Token>(
     *     url = "auth/refresh",
     *     tags = mapOf(CaterKtorKeys.SKIP_AUTH to true),
     * )
     * ```
     *
     * This is the standard mechanism for avoiding infinite loops when the auth-refresh
     * interceptor issues its own token-refresh network call through the same client.
     */
    public const val SKIP_AUTH: String = "caterktor.skipAuth"
}
