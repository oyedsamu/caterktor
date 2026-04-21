package io.github.oyedsamu.caterktor

/**
 * Internal pipeline exception thrown by [KtorTransport] when a network-level
 * error occurs. [NetworkClient]'s typed call helpers catch this and build
 * [NetworkResult.Failure].
 *
 * Never thrown through the public API — it is converted before reaching callers.
 */
internal class NetworkErrorException(
    val error: NetworkError,
) : Exception(error.cause)
