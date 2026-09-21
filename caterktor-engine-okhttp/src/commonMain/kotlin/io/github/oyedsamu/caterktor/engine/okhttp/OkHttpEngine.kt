package io.github.oyedsamu.caterktor.engine.okhttp

import io.github.oyedsamu.caterktor.DnsResolver
import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import io.github.oyedsamu.caterktor.KtorTransport
import io.github.oyedsamu.caterktor.Transport
import io.github.oyedsamu.caterktor.TransportCapability
import io.github.oyedsamu.caterktor.TransportContext
import io.github.oyedsamu.caterktor.TransportFactory
import io.github.oyedsamu.caterktor.toProxyConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp as KtorOkHttp
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import java.net.InetAddress
import java.util.concurrent.TimeUnit

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

    override val capabilities: Set<TransportCapability> =
        setOf(TransportCapability.Proxy, TransportCapability.CustomDns)

    override fun create(context: TransportContext): Transport {
        val network = context.network
        val client = HttpClient(KtorOkHttp) {
            engine {
                network.proxy.toProxyConfig()?.let { proxy = it }
                network.dns?.let { dns = it.asOkHttpDns() }
                config {
                    context.timeout.connectTimeoutMs?.let {
                        connectTimeout(it, TimeUnit.MILLISECONDS)
                    }
                    context.timeout.socketTimeoutMs?.let {
                        readTimeout(it, TimeUnit.MILLISECONDS)
                        writeTimeout(it, TimeUnit.MILLISECONDS)
                    }
                }
            }
        }
        return KtorTransport(client, ownsHttpClient = true)
    }
}

/**
 * Adapt a [DnsResolver] to OkHttp's [Dns].
 *
 * `Dns.lookup` is a blocking call with no suspending overload, so the
 * resolver is bridged with [runBlocking]. OkHttp invokes it on its own
 * connection thread rather than on the calling coroutine, so this blocks a
 * thread OkHttp already dedicates to waiting on I/O.
 *
 * Addresses are converted with [InetAddress.getByName], which performs no
 * lookup of its own for a literal IP address — returning hostnames from a
 * [DnsResolver] would defer to system DNS and undo the override.
 */
@ExperimentalCaterktor
private fun DnsResolver.asOkHttpDns(): Dns = Dns { hostname ->
    runBlocking { resolve(hostname) }.map(InetAddress::getByName)
}
