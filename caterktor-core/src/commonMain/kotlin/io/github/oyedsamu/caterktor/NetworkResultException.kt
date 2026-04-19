package io.github.oyedsamu.caterktor

/**
 * Thrown by [NetworkResult.getOrThrow] and surfaced by [NetworkResult.toKotlinResult]
 * when the underlying result is a [NetworkResult.Failure].
 *
 * The wrapped [error] is available for typed inspection; [cause] is populated
 * from [NetworkError.cause] when present, so existing Throwable-based stack
 * tooling continues to work.
 */
public class NetworkResultException(
    /** The typed error this exception wraps. */
    public val error: NetworkError,
    message: String = error.toString(),
    cause: Throwable? = error.cause,
) : Exception(message, cause)
