package io.github.oyedsamu.caterktor

/**
 * Resolves a hostname to a list of IP addresses, replacing the engine's own
 * DNS lookup.
 *
 * The signature mirrors the CIO engine's resolver, so a CIO-backed transport
 * passes it through unchanged; other engines adapt it to their own type.
 *
 * ## Engine support
 *
 * Not every engine can accept a custom resolver:
 *
 * | Engine | Support |
 * |---|---|
 * | CIO | yes, since Ktor 3.6.0 |
 * | OkHttp | yes, adapted to `okhttp3.Dns` |
 * | Darwin | **no** — `NSURLSession` exposes no DNS hook |
 *
 * Configuring a resolver on an engine that cannot honour it fails at
 * [CaterKtor] build time rather than silently falling back to system DNS —
 * see [TransportCapability.CustomDns].
 *
 * ## Contract
 *
 * - Return at least one address, in the order they should be tried.
 * - Returning an empty list is treated by engines as an unresolvable host.
 * - Implementations may suspend, and must honour cancellation.
 * - Called on the engine's dispatcher, potentially concurrently for
 *   different hostnames.
 *
 * ```kotlin
 * val client = CaterKtor {
 *     engine(Cio)
 *     network {
 *         dns = DnsResolver { hostname -> dohClient.lookup(hostname) }
 *     }
 * }
 * ```
 */
@ExperimentalCaterktor
public fun interface DnsResolver {
    public suspend fun resolve(hostname: String): List<String>
}
