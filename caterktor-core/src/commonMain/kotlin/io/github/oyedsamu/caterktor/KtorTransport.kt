package io.github.oyedsamu.caterktor

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod as KtorHttpMethod

/**
 * A [Transport] implementation backed by a Ktor [HttpClient].
 *
 * [KtorTransport] is the default, opinionated terminal stage of the CaterKtor
 * pipeline. It accepts any Ktor [HttpClient] the caller supplies (typically
 * configured with a platform engine such as OkHttp, Darwin, or CIO) and
 * re-configures it with `expectSuccess = false` so that 4xx and 5xx responses
 * surface as ordinary [NetworkResponse] values rather than thrown exceptions.
 *
 * ## Error handling
 *
 * Ktor-originated exceptions are translated by [mapKtorErrors] into an
 * internal [NetworkErrorException] carrying the appropriate [NetworkError].
 * [kotlin.coroutines.cancellation.CancellationException] is never caught or
 * wrapped — it propagates to the caller to preserve structured concurrency.
 *
 * ## Body support
 *
 * [RequestBody.Bytes] is supported: the bytes are passed to the engine and
 * the `Content-Type` header is set from the body. Streaming body support
 * (sources, multipart, forms) arrives in Wave B1.
 *
 * @property httpClient The caller-supplied Ktor client. The exact instance
 *   passed in is retained so that callers can reference it for diagnostics;
 *   the transport internally uses a copy re-configured with
 *   `expectSuccess = false`.
 */
@ExperimentalCaterktor
public class KtorTransport(
    public val httpClient: HttpClient,
) : Transport {

    /**
     * Internal, defensively re-configured client. `expectSuccess = false`
     * ensures the [Transport] contract is upheld regardless of how the
     * caller-supplied [httpClient] was configured.
     */
    private val client: HttpClient = httpClient.config { expectSuccess = false }

    override suspend fun execute(request: NetworkRequest): NetworkResponse = mapKtorErrors {
        val ktorResponse = client.request {
            method = KtorHttpMethod(request.method.name)
            url(request.url)
            for (name in request.headers.names) {
                for (value in request.headers.getAll(name)) {
                    headers.append(name, value)
                }
            }
            when (val body = request.body) {
                null -> { /* no body, nothing to set */ }
                is RequestBody.Bytes -> {
                    setBody(body.bytes)
                    header(HttpHeaders.ContentType, body.contentType)
                }
            }
        }

        val bytes: ByteArray = ktorResponse.readRawBytes()
        val responseHeaders = Headers {
            for (entry in ktorResponse.headers.entries()) {
                for (value in entry.value) {
                    add(entry.key, value)
                }
            }
        }

        NetworkResponse(
            status = HttpStatus(ktorResponse.status.value),
            headers = responseHeaders,
            body = bytes,
        )
    }
}
