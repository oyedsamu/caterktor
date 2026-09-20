package io.github.oyedsamu.caterktor

/**
 * Engine-agnostic description of an HTTP proxy.
 *
 * [ProxySpec] is declared in `caterktor-core` and therefore carries no Ktor
 * types. Engine modules translate it into the engine's own proxy
 * representation when the transport is constructed — see [TransportFactory].
 *
 * ## Why there is no "disabled" variant
 *
 * Ktor's proxy type is platform-dependent: `java.net.Proxy` on JVM, but a
 * URL-carrying class on Native. Only the JVM side has a "no proxy" sentinel,
 * so an explicit *disable* cannot be expressed in common code that also
 * compiles for Linux and Apple targets. [Default] covers the common case of
 * "whatever the engine and platform already do".
 */
@ExperimentalCaterktor
public sealed interface ProxySpec {

    /**
     * Leave the engine's proxy setting untouched.
     *
     * Each engine then applies its own default, which on CIO and OkHttp means
     * honouring the platform's system proxy settings. This is the default and
     * preserves the behaviour of a transport constructed without any
     * [NetworkConfig].
     */
    public data object Default : ProxySpec

    /**
     * Route requests through the HTTP proxy at [url].
     *
     * ## Why only `http://`
     *
     * Engines disagree about every other scheme. Ktor's JVM `ProxyBuilder.http`
     * discards the scheme and treats the host and port as a plain HTTP proxy,
     * so `https://` there never meant a TLS connection to the proxy; the
     * native builder rejects anything but `http`, and the Darwin engine raises
     * an error while building its `NSURLSession`. Accepting only `http://`
     * makes every engine behave alike and turns a device-only runtime failure
     * into one raised at construction.
     *
     * @property url Absolute URL of the proxy, e.g. `http://proxy.corp:8080`.
     *   Must carry an `http` scheme; use [Socks] for SOCKS proxies.
     */
    public data class Http(public val url: String) : ProxySpec {
        init {
            require(url.isNotBlank()) { "ProxySpec.Http url must not be blank" }
            require(url.startsWith("http://", ignoreCase = true)) {
                "ProxySpec.Http url must start with http://, was \"$url\". " +
                    "Use ProxySpec.Socks for a SOCKS proxy. An https:// proxy URL is not " +
                    "portable: Ktor's native engines reject it and the JVM engines ignore " +
                    "the scheme rather than connecting to the proxy over TLS."
            }
        }
    }

    /**
     * Route requests through the SOCKS proxy at [host]:[port].
     *
     * Supported by the CIO and OkHttp engines, and by Darwin as of Ktor 3.5.0.
     *
     * @property host Proxy hostname or IP address.
     * @property port Proxy port, in `1..65535`.
     */
    public data class Socks(public val host: String, public val port: Int) : ProxySpec {
        init {
            require(host.isNotBlank()) { "ProxySpec.Socks host must not be blank" }
            require(port in 1..MAX_PORT) { "ProxySpec.Socks port must be in 1..$MAX_PORT, was $port" }
        }

        private companion object {
            const val MAX_PORT = 65535
        }
    }
}
