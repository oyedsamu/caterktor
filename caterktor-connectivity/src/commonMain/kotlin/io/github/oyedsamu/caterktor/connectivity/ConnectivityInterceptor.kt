package io.github.oyedsamu.caterktor.connectivity

import io.github.oyedsamu.caterktor.Chain
import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import io.github.oyedsamu.caterktor.Interceptor
import io.github.oyedsamu.caterktor.NetworkError
import io.github.oyedsamu.caterktor.NetworkErrorException
import io.github.oyedsamu.caterktor.NetworkResponse

/**
 * Intercepts requests before the transport and maps [NetworkError.ConnectionFailed]
 * to [NetworkError.Offline] when the [ConnectivityProbe] reports the device is offline.
 *
 * Install this interceptor early in the pipeline (before retry and auth) so
 * that offline failures surface immediately without consuming retry budget.
 *
 * ```kotlin
 * CaterKtor {
 *     transport = KtorTransport(...)
 *     addInterceptor(ConnectivityInterceptor(AndroidConnectivityProbe(context)))
 *     addInterceptor(RetryInterceptor(...))
 * }
 * ```
 */
@ExperimentalCaterktor
public class ConnectivityInterceptor(
    private val probe: ConnectivityProbe,
) : Interceptor {

    override suspend fun intercept(chain: Chain): NetworkResponse {
        if (!probe.isOnline.value) {
            throw NetworkErrorException(NetworkError.Offline())
        }
        return try {
            chain.proceed(chain.request)
        } catch (e: NetworkErrorException) {
            if (e.error is NetworkError.ConnectionFailed && !probe.isOnline.value) {
                throw NetworkErrorException(NetworkError.Offline(cause = e))
            }
            throw e
        }
    }
}
