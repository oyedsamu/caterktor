package io.github.oyedsamu.caterktor.engine.cio

import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import io.github.oyedsamu.caterktor.KtorTransport
import io.github.oyedsamu.caterktor.Transport
import io.github.oyedsamu.caterktor.TransportCapability
import io.github.oyedsamu.caterktor.TransportContext
import io.github.oyedsamu.caterktor.TransportFactory
import io.github.oyedsamu.caterktor.toProxyConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

/**
 * [TransportFactory] for the CIO engine.
 *
 * ```kotlin
 * val client = CaterKtor {
 *     engine(Cio)
 *     network { proxy = ProxySpec.Http("http://proxy.corp:8080") }
 * }
 * ```
 *
 * For a transport that needs engine options CaterKtor does not surface, use
 * [CioTransport] and assign it to `transport` instead.
 */
@ExperimentalCaterktor
public data object Cio : TransportFactory {

    override val capabilities: Set<TransportCapability> = setOf(TransportCapability.Proxy)

    override fun create(context: TransportContext): Transport {
        val client = HttpClient(CIO) {
            engine { context.network.proxy.toProxyConfig()?.let { proxy = it } }
        }
        return KtorTransport(client, ownsHttpClient = true)
    }
}
