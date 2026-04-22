package io.github.oyedsamu.caterktor.testing

import io.github.oyedsamu.caterktor.CaterKtor
import io.github.oyedsamu.caterktor.CaterKtorBuilder
import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import io.github.oyedsamu.caterktor.NetworkClient
import io.github.oyedsamu.caterktor.NetworkRequest
import io.github.oyedsamu.caterktor.NetworkResponse
import kotlin.time.Instant

/**
 * Test harness that exposes a real [NetworkClient] backed by [FakeTransport].
 */
@ExperimentalCaterktor
public class FakeNetworkClient(
    public val transport: FakeTransport = FakeTransport(),
    configure: CaterKtorBuilder.() -> Unit = {},
) {
    public val client: NetworkClient = CaterKtor {
        transport = this@FakeNetworkClient.transport
        configure()
    }

    public val requests: List<NetworkRequest>
        get() = transport.requests

    public fun enqueue(response: NetworkResponse): FakeNetworkClient = apply {
        transport.enqueue(response)
    }

    public suspend fun execute(
        request: NetworkRequest,
        deadline: Instant? = null,
    ): NetworkResponse = client.execute(request, deadline)

    public fun close() {
        client.close()
    }
}
