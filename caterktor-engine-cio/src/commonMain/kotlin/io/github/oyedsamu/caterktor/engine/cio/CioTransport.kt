package io.github.oyedsamu.caterktor.engine.cio

import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import io.github.oyedsamu.caterktor.KtorTransport
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.cio.CIOEngineConfig

/**
 * Creates a [KtorTransport] backed by the CIO (Coroutine-based I/O) engine —
 * the cross-platform engine for JVM and Linux targets.
 *
 * CIO is a pure Kotlin engine with no native dependencies, making it a good
 * default for server-side JVM and Linux use cases.
 *
 * ## Usage
 * ```kotlin
 * val client = CaterKtor {
 *     transport = CioTransport()
 * }
 * ```
 *
 * @param block Optional configuration for the underlying Ktor CIO engine.
 * @return A [KtorTransport] ready to be assigned to [CaterKtorBuilder.transport].
 */
@ExperimentalCaterktor
public fun CioTransport(
    block: io.ktor.client.HttpClientConfig<CIOEngineConfig>.() -> Unit = {},
): KtorTransport = KtorTransport(HttpClient(CIO, block))
