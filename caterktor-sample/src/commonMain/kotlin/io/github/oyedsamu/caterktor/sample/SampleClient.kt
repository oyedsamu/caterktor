package io.github.oyedsamu.caterktor.sample

import io.github.oyedsamu.caterktor.CaterKtor
import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import io.github.oyedsamu.caterktor.NetworkClient
import io.github.oyedsamu.caterktor.RetryInterceptor
import io.github.oyedsamu.caterktor.Transport
import io.github.oyedsamu.caterktor.auth.auth
import io.github.oyedsamu.caterktor.logging.LogLevel
import io.github.oyedsamu.caterktor.logging.LoggerInterceptor
import io.github.oyedsamu.caterktor.serialization.json.KotlinxJsonConverter

@OptIn(ExperimentalCaterktor::class)
public fun sampleClient(
    transport: Transport,
    tokenStore: SampleTokenStore,
    baseUrl: String,
    logger: (String) -> Unit = {},
): NetworkClient = CaterKtor {
    this.transport = transport
    this.baseUrl = baseUrl

    addConverter(KotlinxJsonConverter())

    auth {
        bearer {
            tokenProvider { tokenStore.accessToken() }
        }
        refresh {
            refreshToken { tokenStore.refreshAccessToken() }
        }
    }

    addInterceptor(RetryInterceptor(maxAttempts = 3))
    addInterceptor(LoggerInterceptor(level = LogLevel.Basic, logger = logger))
}
