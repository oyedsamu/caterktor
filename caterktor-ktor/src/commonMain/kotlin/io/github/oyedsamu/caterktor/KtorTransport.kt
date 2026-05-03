package io.github.oyedsamu.caterktor

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.ChannelWriterContent
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.cancel
import io.ktor.utils.io.writeSource
import kotlinx.io.Buffer
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
 * [RequestBody.Bytes], [RequestBody.Text], and [RequestBody.Form] are sent as
 * replayable byte content. [RequestBody.Source] and [RequestBody.Multipart]
 * are sent through Ktor's write-channel content path so large request payloads
 * can stream without first materializing a byte array in CaterKtor.
 *
 * Regular [execute] responses are exposed as replayable [ResponseBody.Bytes]
 * for typed decode compatibility. Use [download] for block-scoped one-shot
 * [ResponseBody.Source] streaming so large response bodies are not materialized
 * by CaterKtor.
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
        val ktorResponse = client.request { applyNetworkRequest(request) }

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

    /**
     * Execute [request] and expose the response body as a one-shot streaming
     * [ResponseBody.Source] for the duration of [block].
     *
     * The returned source must be consumed inside [block]. Once [block]
     * returns or throws, Ktor releases the underlying response resources and the
     * source is no longer valid.
     *
     * **Threading note:** the underlying [ResponseBody.Source] bridges Ktor's
     * async [ByteReadChannel] to the synchronous [kotlinx.io.Source] API via
     * `runBlocking`. Do not invoke [ResponseBody.Source.source] from a
     * single-threaded dispatcher (e.g. the iOS main thread) — doing so will
     * deadlock. Use a background dispatcher when calling this function in such
     * environments.
     */
    public suspend fun <T> download(
        request: NetworkRequest,
        block: suspend (NetworkResponse) -> T,
    ): T = mapKtorErrors {
        client.prepareRequest { applyNetworkRequest(request) }.execute { ktorResponse ->
            val responseHeaders = Headers {
                for (entry in ktorResponse.headers.entries()) {
                    for (value in entry.value) {
                        add(entry.key, value)
                    }
                }
            }
            val channel = ktorResponse.bodyAsChannel()
            var sourceOpened = false
            val body = ResponseBody.Source(
                sourceFactory = {
                    check(!sourceOpened) { "Streaming response body can only be opened once." }
                    sourceOpened = true
                    createRawSource(channel)
                },
                contentType = responseHeaders[HttpHeaders.ContentType],
                contentLength = responseHeaders[HttpHeaders.ContentLength]?.toLongOrNull(),
            )
            block(
                NetworkResponse(
                    status = HttpStatus(ktorResponse.status.value),
                    headers = responseHeaders,
                    body = body,
                ),
            )
        }
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

@OptIn(ExperimentalCaterktor::class)
private fun HttpRequestBuilder.applyNetworkRequest(request: NetworkRequest) {
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

private fun RequestBody.toOutgoingContent(): OutgoingContent =
    when (this) {
        is RequestBody.Bytes -> ByteArrayContent(bytes, parseContentType(contentType))
        is RequestBody.Text -> ByteArrayContent(bytes(), parseContentType(contentType))
        is RequestBody.Form -> ByteArrayContent(bytes(), parseContentType(contentType))
        is RequestBody.Multipart -> toChannelWriterContent()
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

private fun RequestBody.Multipart.toChannelWriterContent(): ChannelWriterContent =
    ChannelWriterContent(
        body = {
            writeMultipartBody(this@toChannelWriterContent)
        },
        contentType = parseContentType(contentType),
        contentLength = contentLength,
    )

private suspend fun ByteWriteChannel.writeMultipartBody(body: RequestBody.Multipart) {
    for (part in body.parts) {
        writeAscii("--")
        writeAscii(body.boundary)
        writeAscii(CRLF)
        for ((name, values) in part.headers.toMap()) {
            for (value in values) {
                writeAscii(name)
                writeAscii(": ")
                writeAscii(value)
                writeAscii(CRLF)
            }
        }
        writeAscii(CRLF)
        val opened = part.body.source()
        try {
            writeSource(opened)
        } finally {
            opened.close()
        }
        writeAscii(CRLF)
    }
    writeAscii("--")
    writeAscii(body.boundary)
    writeAscii("--")
    writeAscii(CRLF)
}

private suspend fun ByteWriteChannel.writeAscii(value: String) {
    writeSource(Buffer().also { it.write(value.encodeToByteArray()) })
}

private const val CRLF: String = "\r\n"

private fun parseContentType(value: String?): ContentType? =
    value?.let(ContentType::parse)

/**
 * Create a [kotlinx.io.Source] that reads from [channel].
 *
 * Implemented in `nonJsMain` using [ByteReadChannelRawSource] + `runBlocking`.
 * Implemented in `jsMain` to throw [UnsupportedOperationException] because JS
 * coroutines do not support `runBlocking`.
 */
internal expect fun createRawSource(channel: ByteReadChannel): kotlinx.io.Source
