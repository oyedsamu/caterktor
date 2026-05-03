@file:OptIn(io.github.oyedsamu.caterktor.ExperimentalCaterktor::class)

package io.github.oyedsamu.caterktor.connectivity

import io.github.oyedsamu.caterktor.Chain
import io.github.oyedsamu.caterktor.ConnectionFailureKind
import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import io.github.oyedsamu.caterktor.Headers
import io.github.oyedsamu.caterktor.HttpMethod
import io.github.oyedsamu.caterktor.HttpStatus
import io.github.oyedsamu.caterktor.NetworkError
import io.github.oyedsamu.caterktor.NetworkErrorException
import io.github.oyedsamu.caterktor.NetworkEvent
import io.github.oyedsamu.caterktor.NetworkRequest
import io.github.oyedsamu.caterktor.NetworkResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertEquals
import kotlin.time.Instant

class ConnectivityInterceptorTest {

    private class FakeProbe(online: Boolean) : ConnectivityProbe {
        override val isOnline: StateFlow<Boolean> = MutableStateFlow(online)
    }

    private class FakeChain(
        private val onProceed: suspend (NetworkRequest) -> NetworkResponse,
    ) : Chain {
        override val request: NetworkRequest = NetworkRequest(
            method = HttpMethod.GET,
            url = "https://example.test/",
        )
        override val attempt: Int = 1
        override val deadline: Instant? = null
        override val requestId: String = "test-request-id"

        override fun emitEvent(event: NetworkEvent) {}

        override suspend fun proceed(request: NetworkRequest): NetworkResponse =
            onProceed(request)
    }

    @Test
    fun offlineProbeThrowsOfflineBeforeRequest() = runTest {
        var chainCalled = false
        val interceptor = ConnectivityInterceptor(FakeProbe(online = false))
        val chain = FakeChain { chainCalled = true; error("should not be reached") }

        val ex = assertFailsWith<NetworkErrorException> {
            interceptor.intercept(chain)
        }

        assertIs<NetworkError.Offline>(ex.error)
        assertEquals(false, chainCalled)
    }

    @Test
    fun onlineProbeForwardsRequest() = runTest {
        val expectedResponse = NetworkResponse(
            status = HttpStatus.OK,
            headers = Headers.Empty,
            body = byteArrayOf(),
        )
        val interceptor = ConnectivityInterceptor(FakeProbe(online = true))
        val chain = FakeChain { expectedResponse }

        val response = interceptor.intercept(chain)

        assertEquals(expectedResponse, response)
    }

    @Test
    fun connectionFailedBecomesOfflineWhenProbeReportsOffline() = runTest {
        val probeFlow = MutableStateFlow(true)
        val probe = object : ConnectivityProbe {
            override val isOnline: StateFlow<Boolean> = probeFlow
        }

        val interceptor = ConnectivityInterceptor(probe)
        val chain = FakeChain {
            // Simulate going offline while the request was in-flight
            probeFlow.value = false
            throw NetworkErrorException(
                NetworkError.ConnectionFailed(kind = ConnectionFailureKind.Unreachable),
            )
        }

        val ex = assertFailsWith<NetworkErrorException> {
            interceptor.intercept(chain)
        }

        assertIs<NetworkError.Offline>(ex.error)
    }

    @Test
    fun connectionFailedIsPassedThroughWhenOnline() = runTest {
        val interceptor = ConnectivityInterceptor(FakeProbe(online = true))
        val connectionFailed = NetworkErrorException(
            NetworkError.ConnectionFailed(kind = ConnectionFailureKind.Unreachable),
        )
        val chain = FakeChain { throw connectionFailed }

        val ex = assertFailsWith<NetworkErrorException> {
            interceptor.intercept(chain)
        }

        assertIs<NetworkError.ConnectionFailed>(ex.error)
    }
}
