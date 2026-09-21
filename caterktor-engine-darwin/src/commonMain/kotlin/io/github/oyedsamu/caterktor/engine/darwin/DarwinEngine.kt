package io.github.oyedsamu.caterktor.engine.darwin

import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import io.github.oyedsamu.caterktor.KtorTransport
import io.github.oyedsamu.caterktor.Transport
import io.github.oyedsamu.caterktor.TransportCapability
import io.github.oyedsamu.caterktor.TransportContext
import io.github.oyedsamu.caterktor.TransportFactory
import io.github.oyedsamu.caterktor.toProxyConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin as KtorDarwin

/**
 * [TransportFactory] for the Darwin engine, backed by `NSURLSession` on Apple
 * platforms.
 *
 * ```kotlin
 * val client = CaterKtor {
 *     engine(Darwin)
 *     network { proxy = ProxySpec.Socks("localhost", 1080) }
 * }
 * ```
 *
 * For a transport that needs engine options CaterKtor does not surface, use
 * [DarwinTransport] and assign it to `transport` instead.
 */
@ExperimentalCaterktor
public data object Darwin : TransportFactory {

    override val capabilities: Set<TransportCapability> = setOf(TransportCapability.Proxy)

    private const val MILLIS_PER_SECOND: Double = 1000.0

    override fun create(context: TransportContext): Transport {
        val client = HttpClient(KtorDarwin) {
            engine {
                context.network.proxy.toProxyConfig()?.let { proxy = it }
                context.timeout.socketTimeoutMs?.let { millis ->
                    configureSession { timeoutIntervalForRequest = millis / MILLIS_PER_SECOND }
                }
            }
        }
        return KtorTransport(client, ownsHttpClient = true)
    }
}
