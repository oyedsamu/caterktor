package io.github.oyedsamu.caterktor

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.io.Buffer
import kotlinx.io.Source as IoSource
import kotlinx.io.readByteArray

internal const val DEFAULT_MAX_BODY_DECODE_BYTES: Int = 10 * 1024 * 1024
private const val RESPONSE_BODY_BUFFER_CHUNK_SIZE: Int = 8 * 1024

/**
 * Raw response body model.
 *
 * The public response surface is source-first so transports can expose
 * streaming bodies without changing ABI later. [Bytes] remains the replayable
 * convenience body used by small payloads, typed decoding, and the current
 * in-core Ktor transport.
 */
public sealed interface ResponseBody {

    /** The response body media type, usually copied from `Content-Type`. */
    public val contentType: String?

    /** The response body length in bytes when known. */
    public val contentLength: Long?

    /**
     * Open a source for this body. Callers own the returned source and must close
     * it after reading.
     */
    public fun source(): IoSource

    /**
     * Convenience helper for small bodies. This consumes and closes a fresh
     * [source], so callers should prefer [source] for large payloads.
     */
    public fun bytes(): ByteArray {
        val opened = source()
        return try {
            opened.readByteArray()
        } finally {
            opened.close()
        }
    }

    /** Convert this body to the legacy raw byte wrapper used by converters. */
    public fun rawBody(contentTypeOverride: String? = null): RawBody =
        RawBody(bytes(), contentTypeOverride ?: contentType)

    /**
     * A replayable in-memory response body.
     */
    public class Bytes(
        bytes: ByteArray,
        override val contentType: String? = null,
    ) : ResponseBody {
        private val bytesStorage: ByteArray = bytes.copyOf()

        override val contentLength: Long = bytesStorage.size.toLong()

        public val bytes: ByteArray
            get() = bytesStorage.copyOf()

        override fun source(): IoSource = Buffer().also { it.write(bytesStorage) }
    }

    /**
     * A response body backed by caller-supplied [kotlinx.io.Source] instances.
     *
     * [sourceFactory] should return a fresh source for every call when replay is
     * needed. One-shot sources are valid for streaming consumers, but byte helper
     * calls and typed decoding will consume them.
     */
    public class Source(
        public val sourceFactory: () -> IoSource,
        override val contentType: String?,
        override val contentLength: Long? = null,
    ) : ResponseBody {
        init {
            require(contentLength == null || contentLength >= 0L) {
                "contentLength must be null or >= 0, was $contentLength"
            }
        }

        override fun source(): IoSource = sourceFactory()
    }
}

/**
 * Return a replayable in-memory body after reading at most [maxBytes].
 *
 * This helper exists for typed decoding, whose current [BodyConverter] contract
 * still consumes a fully buffered [RawBody]. It bounds unknown-length streaming
 * bodies by reading one byte past the limit and failing before allocating beyond
 * the configured budget.
 *
 * @throws IllegalStateException if [maxBytes] is negative or this body exceeds [maxBytes].
 */
public fun ResponseBody.buffered(maxBytes: Int): ResponseBody.Bytes {
    require(maxBytes >= 0) { "maxBytes must be >= 0, was $maxBytes" }
    val maxBytesLong = maxBytes.toLong()
    val knownContentLength = contentLength
    if (knownContentLength != null && knownContentLength > maxBytesLong) {
        throw ResponseBodyTooLargeException(knownContentLength, maxBytes)
    }

    val opened = source()
    return try {
        val sink = Buffer()
        val chunk = ByteArray(RESPONSE_BODY_BUFFER_CHUNK_SIZE)
        var totalBytes = 0L

        while (true) {
            val bytesUntilOverflow = maxBytesLong - totalBytes + 1L
            if (bytesUntilOverflow <= 0L) {
                throw ResponseBodyTooLargeException(totalBytes, maxBytes)
            }
            val readLimit = minOf(chunk.size.toLong(), bytesUntilOverflow).toInt()
            val read = opened.readAtMostTo(chunk, 0, readLimit)
            if (read == -1) break
            totalBytes += read
            if (totalBytes > maxBytesLong) {
                throw ResponseBodyTooLargeException(totalBytes, maxBytes)
            }
            sink.write(chunk, 0, read)
        }

        ResponseBody.Bytes(sink.readByteArray(), contentType)
    } finally {
        opened.close()
    }
}

internal fun ResponseBody.rawBodyOrNull(
    maxBytes: Int = DEFAULT_MAX_BODY_DECODE_BYTES,
    contentTypeOverride: String? = null,
): RawBody? =
    try {
        buffered(maxBytes).rawBody(contentTypeOverride)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

internal class ResponseBodyTooLargeException(
    actualBytes: Long?,
    maxBytes: Int,
) : IllegalStateException(
    if (actualBytes == null) {
        "Response body exceeds maxBodyDecodeBytes ($maxBytes)."
    } else {
        "Response body ($actualBytes bytes) exceeds maxBodyDecodeBytes ($maxBytes)."
    },
)
