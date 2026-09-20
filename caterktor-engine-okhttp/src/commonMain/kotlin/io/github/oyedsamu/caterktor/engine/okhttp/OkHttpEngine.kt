package io.github.oyedsamu.caterktor.engine.okhttp

import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import io.github.oyedsamu.caterktor.KtorTransport
import io.github.oyedsamu.caterktor.Transport
import io.github.oyedsamu.caterktor.TransportCapability
import io.github.oyedsamu.caterktor.TransportContext
import io.github.oyedsamu.caterktor.TransportFactory
import io.github.oyedsamu.caterktor.toProxyConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp as KtorOkHttp

/**
 * [TransportFactory] for the OkHttp engine — the recommended engine on
 * Android and the JVM.
 *
 * ```kotlin
 * val client = CaterKtor {
 *     engine(OkHttp)
 *     network { proxy = ProxySpec.Socks("localhost", 1080) }
 * }
 * ```
 *
 * For a transport that needs engine options CaterKtor does not surface, use
 * [OkHttpTransport] and assign it to `transport` instead.
 */
@ExperimentalCaterktor
public data object OkHttp : TransportFactory {

    override val capabilities: Set<TransportCapability> = setOf(TransportCapability.Proxy)

    override fun create(context: TransportContext): Transport {
        val client = HttpClient(KtorOkHttp) {
            engine { context.network.proxy.toProxyConfig()?.let { proxy = it } }
        }
        return KtorTransport(client, ownsHttpClient = true)
    }
}
