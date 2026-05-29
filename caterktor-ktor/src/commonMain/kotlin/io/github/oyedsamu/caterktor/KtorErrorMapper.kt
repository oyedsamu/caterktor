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
internal suspend fun <T> mapKtorErrors(block: suspend () -> T): T {
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
            NetworkError.ConnectionFailed(e.connectionFailureKind(), cause = e),
        )
    } catch (e: Throwable) {
        if (e is CancellationException) throw e
        throw NetworkErrorException(NetworkError.Unknown(cause = e))
    }
}

private fun IOException.connectionFailureKind(): ConnectionFailureKind {
    var current: Throwable? = this
    while (current != null) {
        val signal = current.failureSignal()
        when {
            signal.isTlsHandshakeFailure() -> return ConnectionFailureKind.TlsHandshake
            signal.isDnsFailure() -> return ConnectionFailureKind.Dns
            signal.isConnectionRefused() -> return ConnectionFailureKind.Refused
            signal.isUnreachable() -> return ConnectionFailureKind.Unreachable
        }
        current = current.cause
    }

    return ConnectionFailureKind.Unreachable
}

private fun Throwable.failureSignal(): String {
    return listOfNotNull(
        this::class.simpleName,
        message,
        toString(),
    ).joinToString(separator = " ")
        .lowercase()
}

private fun String.isDnsFailure(): Boolean =
    contains("unknownhostexception") ||
        contains("unresolvedaddressexception") ||
        contains("unresolved address") ||
        contains("unable to resolve host") ||
        contains("failed to resolve") ||
        contains("could not resolve") ||
        contains("getaddrinfo") ||
        contains("name or service not known") ||
        contains("temporary failure in name resolution") ||
        contains("nodename nor servname provided") ||
        contains("no address associated with hostname") ||
        contains("host not found") ||
        contains("eai_noname") ||
        contains("eai_again") ||
        contains("enotfound")

private fun String.isConnectionRefused(): Boolean =
    contains("connectionrefused") ||
        contains("connection refused") ||
        contains("connectexception: connection refused") ||
        contains("econnrefused")

private fun String.isTlsHandshakeFailure(): Boolean =
    contains("sslhandshakeexception") ||
        contains("sslexception") ||
        contains("sslprotocolexception") ||
        contains("sslpeerunverifiedexception") ||
        contains("certpathvalidatorexception") ||
        contains("certificateexception") ||
        contains("tlsexception") ||
        contains("tls alert") ||
        contains("tls handshake") ||
        contains("ssl handshake") ||
        contains("handshake failed") ||
        contains("secure connection failed") ||
        contains("certificate_verify_failed") ||
        contains("pkix path building failed") ||
        contains("trust anchor") ||
        contains("certificate pinning") ||
        contains("server certificate") ||
        contains("certificate is invalid") ||
        contains("certificate verify failed")

private fun String.isUnreachable(): Boolean =
    contains("noroutetohostexception") ||
        contains("no route to host") ||
        contains("network is unreachable") ||
        contains("host is unreachable") ||
        contains("enetunreach") ||
        contains("ehostunreach") ||
        contains("unreachable")
