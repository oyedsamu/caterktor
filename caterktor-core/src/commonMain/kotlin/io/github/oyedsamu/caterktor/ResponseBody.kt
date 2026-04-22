package io.github.oyedsamu.caterktor

import kotlinx.io.Buffer
import kotlinx.io.Source as IoSource
import kotlinx.io.readByteArray

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
