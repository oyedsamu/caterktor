package io.github.oyedsamu.caterktor

import kotlinx.io.Buffer
import kotlinx.io.Source as IoSource
import kotlinx.io.readByteArray

/**
 * Marker for request body types.
 *
 * Bodies are source-first: every implementation can provide a fresh
 * [kotlinx.io.Source] for transports that stream request payloads. Byte helpers
 * remain available for small payloads and legacy converter paths.
 */
public sealed interface RequestBody {

    /** The request body media type, e.g. `"application/json"`, when known. */
    public val contentType: String?

    /**
     * The body length in bytes when known. `null` means the transport should use
     * chunked/streaming transfer semantics when supported.
     */
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

    /**
     * A pre-encoded byte-array body.
     *
     * Created automatically by the typed call helpers when a [BodyConverter]
     * encodes the request value. Do not construct manually unless you have
     * already-encoded bytes and want to bypass converter selection.
     *
     * @property bytes The encoded content as a fresh defensive copy.
     * @property contentType The MIME type, e.g. `"application/json"`.
     */
    public class Bytes(
        bytes: ByteArray,
        override val contentType: String,
    ) : RequestBody {
        private val bytesStorage: ByteArray = bytes.copyOf()

        override val contentLength: Long = bytesStorage.size.toLong()

        public val bytes: ByteArray
            get() = bytesStorage.copyOf()

        override fun source(): IoSource = Buffer().also { it.write(bytesStorage) }
    }

    /**
     * A UTF-8 text body.
     *
     * This is a convenience variant over [Bytes] for hand-authored textual
     * request payloads. Structured typed calls still go through [BodyConverter].
     */
    public class Text(
        public val text: String,
        override val contentType: String = "text/plain; charset=UTF-8",
    ) : RequestBody {
        private val bytesStorage: ByteArray = text.encodeToByteArray()

        override val contentLength: Long = bytesStorage.size.toLong()

        override fun source(): IoSource = Buffer().also { it.write(bytesStorage) }
    }

    /**
     * A streaming body backed by caller-supplied [kotlinx.io.Source] instances.
     *
     * [sourceFactory] should return a fresh source for every call when the body
     * may be retried or logged. A one-shot source is valid, but callers must then
     * avoid retry policies that need to replay the request body.
     */
    public class Source(
        public val sourceFactory: () -> IoSource,
        override val contentType: String?,
        override val contentLength: Long? = null,
    ) : RequestBody {
        init {
            require(contentLength == null || contentLength >= 0L) {
                "contentLength must be null or >= 0, was $contentLength"
            }
        }

        override fun source(): IoSource = sourceFactory()
    }
}
