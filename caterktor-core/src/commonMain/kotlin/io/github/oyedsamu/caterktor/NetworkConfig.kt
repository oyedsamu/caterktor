package io.github.oyedsamu.caterktor

/**
 * Connection-level settings that must be applied to the transport engine at
 * construction time.
 *
 * ## Why this is not part of [TimeoutConfig] or the `ktor { }` block
 *
 * A Ktor engine is instantiated when its [io.ktor.client.HttpClient] is built,
 * and `HttpClient.config { }` reuses that existing engine. Settings such as a
 * proxy therefore cannot be retrofitted onto a client after the fact — an
 * `engine { }` block applied later is silently ignored. [NetworkConfig] is
 * collected by [CaterKtorBuilder] *before* the transport exists and handed to
 * a [TransportFactory], which is the only point where engine configuration can
 * still take effect.
 *
 * ## Usage
 *
 * ```kotlin
 * val client = CaterKtor {
 *     engine(Cio)
 *     network {
 *         proxy = ProxySpec.Http("http://proxy.corp:8080")
 *     }
 * }
 * ```
 *
 * @property proxy The proxy to route requests through. Defaults to
 *   [ProxySpec.Default], which leaves the engine's own behaviour unchanged.
 * @property dns Replaces the engine's hostname resolution. `null` leaves the
 *   engine's own resolver in place. Not supported by every engine — see
 *   [DnsResolver].
 */
@ExperimentalCaterktor
public data class NetworkConfig(
    public val proxy: ProxySpec = ProxySpec.Default,
    public val dns: DnsResolver? = null,
) {

    /**
     * Mutable builder for [NetworkConfig], used by the `network { }` DSL on
     * [CaterKtorBuilder].
     */
    @CaterKtorDsl
    public class Builder {
        /** @see NetworkConfig.proxy */
        public var proxy: ProxySpec = ProxySpec.Default

        /** @see NetworkConfig.dns */
        public var dns: DnsResolver? = null

        /** Build an immutable [NetworkConfig] from the current state. */
        public fun build(): NetworkConfig = NetworkConfig(proxy = proxy, dns = dns)
    }

    public companion object {
        /** A [NetworkConfig] that changes nothing about the engine's defaults. */
        public val Default: NetworkConfig = NetworkConfig()
    }
}
