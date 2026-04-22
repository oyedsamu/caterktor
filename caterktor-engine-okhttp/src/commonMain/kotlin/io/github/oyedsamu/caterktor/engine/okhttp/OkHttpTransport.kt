package io.github.oyedsamu.caterktor.engine.okhttp

import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import io.github.oyedsamu.caterktor.KtorTransport
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.engine.okhttp.OkHttpConfig

/**
 * Creates a [KtorTransport] backed by the OkHttp engine — the recommended
 * transport for JVM and Android targets.
 *
 * The optional [block] allows full access to [OkHttpConfig] for fine-tuning
 * connection pools, timeouts, interceptors, and SSL configuration. All
 * OkHttp-specific knobs live there; CaterKtor does not surface them directly.
 *
 * ## Usage
 * ```kotlin
 * val client = CaterKtor {
 *     transport = OkHttpTransport {
 *         config {
 *             retryOnConnectionFailure(false)
 *         }
 *     }
 * }
 * ```
 *
 * @param block Configuration for the underlying Ktor OkHttp engine.
 * @return A [KtorTransport] ready to be assigned to [CaterKtorBuilder.transport].
 */
@ExperimentalCaterktor
public fun OkHttpTransport(
    block: io.ktor.client.HttpClientConfig<OkHttpConfig>.() -> Unit = {},
): KtorTransport = KtorTransport(HttpClient(OkHttp, block), ownsHttpClient = true)
