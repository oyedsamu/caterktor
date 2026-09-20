package io.github.oyedsamu.caterktor

/**
 * A connection-level feature that a transport engine may or may not support.
 *
 * Engines declare what they can honour via [TransportFactory.capabilities].
 * [CaterKtorBuilder.build] compares that set against what the configuration
 * actually asks for and fails if the engine cannot deliver it, rather than
 * letting the setting be silently dropped.
 */
@ExperimentalCaterktor
public enum class TransportCapability {

    /**
     * The engine can route requests through a [ProxySpec] other than
     * [ProxySpec.Default].
     */
    Proxy,

    /**
     * The engine can delegate hostname resolution to a [DnsResolver].
     *
     * The Darwin engine cannot: `NSURLSession` offers no hook for it.
     */
    CustomDns,
}

/**
 * The resolved configuration handed to a [TransportFactory] when the transport
 * is constructed.
 *
 * Wrapping the configuration in a context type — rather than passing
 * [NetworkConfig] directly — lets later releases carry additional settings to
 * engines without breaking the [TransportFactory] signature.
 *
 * @property network Connection-level settings collected from the
 *   `network { }` block.
 */
@ExperimentalCaterktor
public data class TransportContext(
    public val network: NetworkConfig = NetworkConfig.Default,
)

/**
 * Creates a [Transport] from a resolved [TransportContext].
 *
 * Engine modules ship a factory object per engine (`Cio`, `OkHttp`, `Darwin`).
 * Pass one to [CaterKtorBuilder.engine] instead of assigning
 * [CaterKtorBuilder.transport] directly:
 *
 * ```kotlin
 * val client = CaterKtor {
 *     engine(Cio)
 *     network { proxy = ProxySpec.Socks("localhost", 1080) }
 * }
 * ```
 *
 * Assigning `transport = CioTransport()` still works and remains the right
 * choice when supplying a pre-built client, but a transport built that way is
 * constructed before the builder has collected any configuration, so
 * `network { }` cannot reach it. [CaterKtorBuilder.build] rejects that
 * combination rather than ignoring the configuration.
 */
@ExperimentalCaterktor
public interface TransportFactory {

    /** The connection-level features this engine can honour. */
    public val capabilities: Set<TransportCapability>

    /** Construct a [Transport] configured according to [context]. */
    public fun create(context: TransportContext): Transport
}

/**
 * The capabilities this configuration actually requires of an engine.
 *
 * Settings left at their defaults require nothing, so a default
 * [NetworkConfig] is satisfied by every engine.
 */
@ExperimentalCaterktor
internal fun NetworkConfig.requiredCapabilities(): Set<TransportCapability> = buildSet {
    if (proxy != ProxySpec.Default) add(TransportCapability.Proxy)
    if (dns != null) add(TransportCapability.CustomDns)
}
