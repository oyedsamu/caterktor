package io.github.oyedsamu.caterktor

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Execute [block] and translate any Ktor-originated exception into a
 * [NetworkErrorException] carrying the appropriate [NetworkError] variant.
 *
 * ## Cancellation
 *
 * [CancellationException] is re-thrown verbatim and is NEVER wrapped.
 * Catching and wrapping it would break structured concurrency — see
 * [NetworkError]'s KDoc and PRD-v2 §5.3.
 *
 * The broad `Throwable` fallback explicitly re-checks for
 * [CancellationException] in case it is thrown from a subclass position
 * not caught by the dedicated clauses above.
 */
internal suspend inline fun <T> mapKtorErrors(block: () -> T): T {
    return try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: ConnectTimeoutException) {
        throw NetworkErrorException(NetworkError.Timeout(TimeoutKind.Connect, cause = e))
    } catch (e: SocketTimeoutException) {
        throw NetworkErrorException(NetworkError.Timeout(TimeoutKind.Socket, cause = e))
    } catch (e: HttpRequestTimeoutException) {
        throw NetworkErrorException(NetworkError.Timeout(TimeoutKind.Request, cause = e))
    } catch (e: IOException) {
        throw NetworkErrorException(
            NetworkError.ConnectionFailed(ConnectionFailureKind.Unreachable, cause = e),
        )
    } catch (e: Throwable) {
        if (e is CancellationException) throw e
        throw NetworkErrorException(NetworkError.Unknown(cause = e))
    }
}
