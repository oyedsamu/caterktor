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
}
