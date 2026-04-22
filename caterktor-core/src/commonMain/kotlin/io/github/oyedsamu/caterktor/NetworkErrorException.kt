package io.github.oyedsamu.caterktor

/**
 * Pipeline exception thrown by transports and interceptors when they need to
 * abort a call with a typed [NetworkError].
 *
 * [NetworkClient]'s typed call helpers catch this and build [NetworkResult.Failure].
 * Raw [NetworkClient.execute] callers may observe it directly.
 */
public class NetworkErrorException(
    public val error: NetworkError,
) : Exception(error.cause)
