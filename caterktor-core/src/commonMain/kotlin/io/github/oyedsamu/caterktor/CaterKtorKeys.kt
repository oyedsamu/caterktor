package io.github.oyedsamu.caterktor

/**
 * Well-known [AttributeKey]s for [NetworkRequest.attributes] used to communicate
 * per-request overrides to the CaterKtor framework.
 *
 * Pass these as attribute keys to any typed call helper:
 * ```kotlin
 * client.get<MyType>(
 *     url = "endpoint",
 *     attributes = Attributes { put(CaterKtorKeys.UNWRAPPER, DataFieldUnwrapper("data")) },
 * )
 * ```
 *
 * Per-request values take precedence over the per-client defaults configured
 * in [CaterKtorBuilder].
 */
@ExperimentalCaterktor
public object CaterKtorKeys {

    /** Per-request [ResponseUnwrapper] override. Set via [NetworkRequest.attributes]. */
    public val UNWRAPPER: AttributeKey<ResponseUnwrapper> = AttributeKey("caterktor.unwrapper")

    /** Per-request [RequestEnveloper] override. Set via [NetworkRequest.attributes]. */
    public val ENVELOPER: AttributeKey<RequestEnveloper> = AttributeKey("caterktor.enveloper")

    /**
     * Set to `true` to skip auth token injection and refresh handling for this request.
     *
     * Set this attribute in [NetworkRequest.attributes] to prevent [BearerAuthInterceptor]
     * (and [AuthRefreshInterceptor]) from adding or refreshing credentials:
     *
     * ```kotlin
     * client.get<Token>(
     *     url = "auth/refresh",
     *     attributes = Attributes { put(CaterKtorKeys.SKIP_AUTH, true) },
     * )
     * ```
     *
     * This is the standard mechanism for avoiding infinite loops when the auth-refresh
     * interceptor issues its own token-refresh network call through the same client.
     */
    public val SKIP_AUTH: AttributeKey<Boolean> = AttributeKey("caterktor.skipAuth")
}
