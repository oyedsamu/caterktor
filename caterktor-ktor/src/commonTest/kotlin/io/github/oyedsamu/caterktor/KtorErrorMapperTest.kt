package io.github.oyedsamu.caterktor

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class KtorErrorMapperTest {

    @Test
    fun dns_signal_from_exception_class_maps_to_dns() = runTest {
        val thrown = mappedFailure(UnknownHostException("api.example.test"))

        val failure = assertIs<NetworkError.ConnectionFailed>(thrown.error)
        assertEquals(ConnectionFailureKind.Dns, failure.kind)
    }

    @Test
    fun dns_signal_from_platform_message_maps_to_dns() = runTest {
        val thrown = mappedFailure(IOException("getaddrinfo failed: EAI_NONAME"))

        val failure = assertIs<NetworkError.ConnectionFailed>(thrown.error)
        assertEquals(ConnectionFailureKind.Dns, failure.kind)
    }

    @Test
    fun refused_signal_from_platform_message_maps_to_refused() = runTest {
        val thrown = mappedFailure(IOException("connect failed: ECONNREFUSED (Connection refused)"))

        val failure = assertIs<NetworkError.ConnectionFailed>(thrown.error)
        assertEquals(ConnectionFailureKind.Refused, failure.kind)
    }

    @Test
    fun tls_signal_from_exception_class_maps_to_tls_handshake() = runTest {
        val thrown = mappedFailure(SSLHandshakeException("certificate verify failed"))

        val failure = assertIs<NetworkError.ConnectionFailed>(thrown.error)
        assertEquals(ConnectionFailureKind.TlsHandshake, failure.kind)
    }

    @Test
    fun tls_signal_from_wrapped_cause_maps_to_tls_handshake() = runTest {
        val thrown = mappedFailure(
            IOException("request failed", CertificateException("PKIX path building failed")),
        )

        val failure = assertIs<NetworkError.ConnectionFailed>(thrown.error)
        assertEquals(ConnectionFailureKind.TlsHandshake, failure.kind)
    }

    @Test
    fun unreachable_signal_from_platform_message_maps_to_unreachable() = runTest {
        val thrown = mappedFailure(IOException("connect failed: ENETUNREACH (Network is unreachable)"))

        val failure = assertIs<NetworkError.ConnectionFailed>(thrown.error)
        assertEquals(ConnectionFailureKind.Unreachable, failure.kind)
    }

    @Test
    fun io_exception_without_specific_signal_defaults_to_unreachable() = runTest {
        val cause = IOException("socket closed unexpectedly")
        val thrown = mappedFailure(cause)

        val failure = assertIs<NetworkError.ConnectionFailed>(thrown.error)
        assertEquals(ConnectionFailureKind.Unreachable, failure.kind)
        assertSame(cause, failure.cause)
    }

    @Test
    fun cancellation_propagates_unchanged() = runTest {
        val sentinel = CancellationException("caller cancelled")

        val thrown = assertFailsWith<CancellationException> {
            mapKtorErrors { throw sentinel }
        }

        assertSame(sentinel, thrown)
    }

    @Test
    fun connect_timeout_maps_to_timeout_before_connection_signal_matching() = runTest {
        val thrown = mappedFailure(ConnectTimeoutException("Connection refused"))

        val failure = assertIs<NetworkError.Timeout>(thrown.error)
        assertEquals(TimeoutKind.Connect, failure.kind)
    }

    @Test
    fun socket_timeout_maps_to_timeout_before_connection_signal_matching() = runTest {
        val thrown = mappedFailure(SocketTimeoutException("Network is unreachable"))

        val failure = assertIs<NetworkError.Timeout>(thrown.error)
        assertEquals(TimeoutKind.Socket, failure.kind)
    }

    @Test
    fun request_timeout_maps_to_timeout_before_io_classifier() = runTest {
        val thrown = mappedFailure(HttpRequestTimeoutException("https://example.test", 1_000))

        val failure = assertIs<NetworkError.Timeout>(thrown.error)
        assertEquals(TimeoutKind.Request, failure.kind)
    }

    private suspend fun mappedFailure(throwable: Throwable): NetworkErrorException {
        return assertFailsWith {
            mapKtorErrors { throw throwable }
        }
    }

    private class UnknownHostException(message: String) : IOException(message)

    private class SSLHandshakeException(message: String) : IOException(message)

    private class CertificateException(message: String) : IOException(message)
}
