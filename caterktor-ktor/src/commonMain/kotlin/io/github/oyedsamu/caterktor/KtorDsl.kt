package io.github.oyedsamu.caterktor

import io.ktor.client.HttpClientConfig

/**
 * Apply additional configuration to the underlying Ktor [HttpClient].
 *
 * This is the K4 escape hatch: use it to install Ktor plugins (caching, logging,
 * content-encoding, etc.) that CaterKtor does not surface directly.
 *
 * The block is applied **after** the engine-specific factory configures the client.
 * Multiple calls accumulate — each block is applied in registration order.
 *
 * Only effective when [CaterKtorBuilder.transport] is a [KtorTransport].
 * Silently ignored otherwise.
 *
 * This function is defined in `caterktor-ktor` rather than `caterktor-core`
 * because it references [KtorTransport], which carries a Ktor client dependency.
 * Projects that do not use Ktor can depend on `caterktor-core` alone without
 * pulling in any Ktor artifacts.
 *
 * ```kotlin
 * val client = CaterKtor {
 *     transport = OkHttpTransport()
 *     ktor {
 *         install(HttpCache)
 *     }
 * }
 * ```
 */
@ExperimentalCaterktor
public fun CaterKtorBuilder.ktor(block: HttpClientConfig<*>.() -> Unit): CaterKtorBuilder =
    addTransportFinalizer { transport ->
        if (transport is KtorTransport) {
            KtorTransport(transport.httpClient.config(block), ownsHttpClient = transport.ownsHttpClient)
        } else {
            transport
        }
    }
