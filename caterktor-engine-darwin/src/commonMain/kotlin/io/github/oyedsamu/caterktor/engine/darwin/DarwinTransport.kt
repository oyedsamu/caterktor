package io.github.oyedsamu.caterktor.engine.darwin

import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import io.github.oyedsamu.caterktor.KtorTransport
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.engine.darwin.DarwinClientEngineConfig

/**
 * Creates a [KtorTransport] backed by the Darwin (NSURLSession) engine — the
 * correct transport for iOS and macOS targets.
 *
 * ## Usage
 * ```kotlin
 * val client = CaterKtor {
 *     transport = DarwinTransport()
 * }
 * ```
 *
 * @param block Optional configuration for the underlying Ktor Darwin engine.
 * @return A [KtorTransport] ready to be assigned to [CaterKtorBuilder.transport].
 */
@ExperimentalCaterktor
public fun DarwinTransport(
    block: io.ktor.client.HttpClientConfig<DarwinClientEngineConfig>.() -> Unit = {},
): KtorTransport = KtorTransport(HttpClient(Darwin, block))
