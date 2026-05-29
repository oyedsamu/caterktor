package io.github.oyedsamu.caterktor.sample

import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import io.github.oyedsamu.caterktor.NetworkClient
import io.github.oyedsamu.caterktor.NetworkError
import io.github.oyedsamu.caterktor.NetworkResult
import io.github.oyedsamu.caterktor.get

@OptIn(ExperimentalCaterktor::class)
public class UserRepository(
    private val client: NetworkClient,
) {
    public suspend fun me(): User {
        return when (val result = client.get<User>("/users/me")) {
            is NetworkResult.Success -> result.body
            is NetworkResult.Failure -> error("Could not load /users/me: ${result.error.describe()}")
        }
    }
}

private fun NetworkError.describe(): String =
    when (this) {
        is NetworkError.Http -> "HTTP ${status.code}"
        is NetworkError.ConnectionFailed -> "connection failed: $kind"
        is NetworkError.Timeout -> "timeout: $kind"
        is NetworkError.Serialization -> "serialization failed: $phase"
        is NetworkError.Protocol -> "protocol error: $message"
        is NetworkError.CircuitOpen -> "circuit open: $name"
        is NetworkError.Offline -> "offline"
        is NetworkError.Unknown -> "unknown error: ${cause.message}"
    }
