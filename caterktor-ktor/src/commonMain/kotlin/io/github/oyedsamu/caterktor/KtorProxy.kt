package io.github.oyedsamu.caterktor

import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.ProxyConfig
import io.ktor.client.engine.http
import io.ktor.http.Url

/**
 * Translate an engine-agnostic [ProxySpec] into Ktor's [ProxyConfig].
 *
 * Returns `null` for [ProxySpec.Default], meaning the engine's own proxy
 * setting should be left untouched:
 *
 * ```kotlin
 * HttpClient(CIO) {
 *     engine { spec.toProxyConfig()?.let { proxy = it } }
 * }
 * ```
 *
 * Exposed so that custom [Transport] implementations outside the
 * `caterktor-engine-*` modules can honour [NetworkConfig] the same way the
 * bundled engines do.
 */
@ExperimentalCaterktor
public fun ProxySpec.toProxyConfig(): ProxyConfig? = when (this) {
    is ProxySpec.Default -> null
    is ProxySpec.Http -> ProxyBuilder.http(Url(url))
    is ProxySpec.Socks -> ProxyBuilder.socks(host, port)
}
