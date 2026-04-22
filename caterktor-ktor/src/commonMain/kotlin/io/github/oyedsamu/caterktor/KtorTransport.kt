package io.github.oyedsamu.caterktor

import io.ktor.client.HttpClient
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.ChannelWriterContent
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.writeSource
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
 * [RequestBody.Bytes] and [RequestBody.Text] are sent as replayable byte
 * content. [RequestBody.Source] is sent through Ktor's write-channel content
 * path so large request payloads can stream without first materializing a
 * byte array in CaterKtor.
 *
	 * Responses are currently exposed as replayable [ResponseBody.Bytes]. This
	 * means the transport reads the HTTP response into memory before core's typed
	 * decode limit can run. Large streaming downloads are intentionally deferred
	 * to a future streaming transport/API.
 *
 * @property httpClient The caller-supplied Ktor client. The exact instance
 *   passed in is retained so that callers can reference it for diagnostics;
 *   the transport internally uses a copy re-configured with
 *   `expectSuccess = false`.
 * @property ownsHttpClient If `true`, [close] also closes [httpClient]. Engine
 *   factory modules pass `true`; caller-supplied clients should usually keep
 *   the default `false`.
 */
@ExperimentalCaterktor
public class KtorTransport(
    public val httpClient: HttpClient,
    public val ownsHttpClient: Boolean = false,
) : CloseableTransport {

    /**
     * Internal, defensively re-configured client. `expectSuccess = false`
     * ensures the [Transport] contract is upheld regardless of how the
     * caller-supplied [httpClient] was configured.
     */
    private val client: HttpClient = httpClient.config { expectSuccess = false }
    private var closed: Boolean = false

    override suspend fun execute(request: NetworkRequest): NetworkResponse = mapKtorErrors {
        val ktorResponse = client.request {
            method = KtorHttpMethod(request.method.name)
            url(request.url)
            val bodyContentType = request.body?.contentType
            for (name in request.headers.names) {
                if (bodyContentType != null && name.equals(HttpHeaders.ContentType, ignoreCase = true)) {
                    continue
                }
                for (value in request.headers.getAll(name)) {
                    headers.append(name, value)
                }
            }
            when (val body = request.body) {
                null -> { /* no body, nothing to set */ }
                else -> setBody(body.toOutgoingContent())
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
            body = ResponseBody.Bytes(
                bytes = bytes,
                contentType = responseHeaders[HttpHeaders.ContentType],
            ),
        )
    }

    override fun close() {
        if (closed) return
        closed = true
        client.close()
        if (ownsHttpClient) {
            httpClient.close()
        }
    }
}

private fun RequestBody.toOutgoingContent(): OutgoingContent =
    when (this) {
        is RequestBody.Bytes -> ByteArrayContent(bytes, parseContentType(contentType))
        is RequestBody.Text -> ByteArrayContent(bytes(), parseContentType(contentType))
        is RequestBody.Source -> ChannelWriterContent(
            body = {
                val opened = source()
                try {
                    writeSource(opened)
                } finally {
                    opened.close()
                }
            },
            contentType = parseContentType(contentType),
            contentLength = contentLength,
        )
    }

private fun parseContentType(value: String?): ContentType? =
    value?.let(ContentType::parse)
