package io.github.oyedsamu.caterktor

import kotlinx.io.Buffer
import kotlinx.io.Source as IoSource
import kotlinx.io.readByteArray
import kotlin.random.Random

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
     * An `application/x-www-form-urlencoded` body.
     *
     * Field order and repeated names are preserved. Names and values are encoded
     * with UTF-8 form encoding, where spaces become `+` and reserved bytes are
     * percent-escaped.
     */
    public class Form(
        fields: List<Field>,
    ) : RequestBody {
        public constructor(vararg fields: Field) : this(fields.toList())

        public val fields: List<Field> = fields.toList()

        private val bytesStorage: ByteArray = encodeFormFields(this.fields)

        override val contentType: String = FORM_CONTENT_TYPE

        override val contentLength: Long = bytesStorage.size.toLong()

        override fun source(): IoSource = Buffer().also { it.write(bytesStorage) }

        /**
         * A single ordered form field.
         */
        public data class Field(
            public val name: String,
            public val value: String,
        ) {
            init {
                require(name.isNotBlank()) { "Form field name must not be blank." }
            }
        }
    }

    /**
     * A `multipart/form-data` body.
     *
     * This core model is transport-neutral. It exposes the structured [parts]
     * for transports that can stream multipart data directly, while also
     * providing [source] and [bytes] compatibility for existing byte/source
     * paths.
     */
    public class Multipart(
        parts: List<Part>,
        public val boundary: String = generateMultipartBoundary(),
    ) : RequestBody {
        public constructor(vararg parts: Part) : this(parts.toList())

        public val parts: List<Part> = parts.toList()

        override val contentType: String = "$MULTIPART_CONTENT_TYPE; boundary=$boundary"

        override val contentLength: Long? = calculateMultipartContentLength(this.parts, boundary)

        init {
            require(this.parts.isNotEmpty()) { "Multipart body must contain at least one part." }
            validateMultipartBoundary(boundary)
        }

        override fun source(): IoSource = Buffer().also { sink ->
            writeMultipartTo(sink, parts, boundary)
        }

        /**
         * A single multipart section with form-data headers and a source-first body.
         */
        public class Part(
            public val headers: Headers,
            public val body: RequestBody,
        ) {
            init {
                require("Content-Disposition" in headers) {
                    "Multipart part must include a Content-Disposition header."
                }
                require("Content-Length" !in headers) {
                    "Multipart part headers must not include Content-Length."
                }
            }

            public companion object {
                /**
                 * Build a form-data part around [body].
                 */
                public fun formData(
                    name: String,
                    body: RequestBody,
                    filename: String? = null,
                    headers: Headers = Headers.Empty,
                ): Part {
                    validateMultipartName(name)
                    if (filename != null) {
                        validateMultipartFilename(filename)
                    }
                    require("Content-Disposition" !in headers) {
                        "Extra multipart headers must not include Content-Disposition."
                    }
                    require("Content-Length" !in headers) {
                        "Extra multipart headers must not include Content-Length."
                    }
                    val bodyContentType = body.contentType
                    require("Content-Type" !in headers || bodyContentType == null) {
                        "Extra multipart headers must not include Content-Type when body.contentType is set."
                    }

                    val formHeaders = Headers {
                        set("Content-Disposition", buildContentDisposition(name, filename))
                        if (bodyContentType != null) {
                            set("Content-Type", bodyContentType)
                        }
                        addHeaders(headers)
                    }
                    return Part(formHeaders, body)
                }

                /**
                 * Build a UTF-8 text field part.
                 */
                public fun field(
                    name: String,
                    value: String,
                    headers: Headers = Headers.Empty,
                ): Part = formData(name, Text(value), headers = headers)
            }
        }
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

private const val FORM_CONTENT_TYPE: String = "application/x-www-form-urlencoded; charset=UTF-8"
private const val MULTIPART_CONTENT_TYPE: String = "multipart/form-data"
private const val CRLF: String = "\r\n"

private val HEX_DIGITS: CharArray = "0123456789ABCDEF".toCharArray()

private fun encodeFormFields(fields: List<RequestBody.Form.Field>): ByteArray =
    buildString {
        fields.forEachIndexed { index, field ->
            if (index > 0) append('&')
            appendFormEncoded(field.name)
            append('=')
            appendFormEncoded(field.value)
        }
    }.encodeToByteArray()

private fun StringBuilder.appendFormEncoded(value: String) {
    for (byte in value.encodeToByteArray()) {
        val unsigned = byte.toInt() and 0xff
        when {
            unsigned == ' '.code -> append('+')
            isFormSafeByte(unsigned) -> append(unsigned.toChar())
            else -> {
                append('%')
                append(HEX_DIGITS[unsigned shr 4])
                append(HEX_DIGITS[unsigned and 0x0f])
            }
        }
    }
}

private fun isFormSafeByte(value: Int): Boolean =
    value in 'A'.code..'Z'.code ||
        value in 'a'.code..'z'.code ||
        value in '0'.code..'9'.code ||
        value == '*'.code ||
        value == '-'.code ||
        value == '.'.code ||
        value == '_'.code

private fun generateMultipartBoundary(): String {
    val bytes = ByteArray(16)
    Random.nextBytes(bytes)
    return buildString("caterktor-".length + bytes.size * 2) {
        append("caterktor-")
        for (byte in bytes) {
            val unsigned = byte.toInt() and 0xff
            append(HEX_DIGITS[unsigned shr 4])
            append(HEX_DIGITS[unsigned and 0x0f])
        }
    }
}

private fun validateMultipartBoundary(boundary: String) {
    require(boundary.isNotEmpty()) { "Multipart boundary must not be empty." }
    require(boundary.length <= 70) { "Multipart boundary must be at most 70 characters." }
    require(boundary.all(::isBoundaryChar)) {
        "Multipart boundary contains characters that are not allowed."
    }
}

private fun isBoundaryChar(char: Char): Boolean =
    char in 'A'..'Z' ||
        char in 'a'..'z' ||
        char in '0'..'9' ||
        char == '\'' ||
        char == '(' ||
        char == ')' ||
        char == '+' ||
        char == '_' ||
        char == ',' ||
        char == '-' ||
        char == '.' ||
        char == '/' ||
        char == ':' ||
        char == '=' ||
        char == '?'

private fun validateMultipartName(name: String) {
    require(name.isNotBlank()) { "Multipart part name must not be blank." }
    require(!name.hasHeaderUnsafeChar()) { "Multipart part name must not contain control characters." }
}

private fun validateMultipartFilename(filename: String) {
    require(filename.isNotBlank()) { "Multipart filename must not be blank." }
    require(!filename.hasHeaderUnsafeChar()) { "Multipart filename must not contain control characters." }
}

private fun String.hasHeaderUnsafeChar(): Boolean =
    any { it == '\r' || it == '\n' || it.code == 0 }

private fun buildContentDisposition(name: String, filename: String?): String =
    buildString {
        append("form-data; name=\"")
        append(name.escapeHeaderParameter())
        append('"')
        if (filename != null) {
            append("; filename=\"")
            append(filename.escapeHeaderParameter())
            append('"')
        }
    }

private fun String.escapeHeaderParameter(): String =
    buildString(length) {
        for (char in this@escapeHeaderParameter) {
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                else -> append(char)
            }
        }
    }

private fun Headers.Builder.addHeaders(headers: Headers) {
    for ((name, values) in headers.toMap()) {
        for (value in values) {
            add(name, value)
        }
    }
}

private fun calculateMultipartContentLength(
    parts: List<RequestBody.Multipart.Part>,
    boundary: String,
): Long? {
    var length = 0L
    for (part in parts) {
        val bodyLength = part.body.contentLength ?: return null
        length += delimiterLength(boundary)
        length += headersLength(part.headers)
        length += CRLF.length
        length += bodyLength
        length += CRLF.length
    }
    length += closingDelimiterLength(boundary)
    return length
}

private fun delimiterLength(boundary: String): Long =
    "--".length.toLong() + boundary.length + CRLF.length

private fun closingDelimiterLength(boundary: String): Long =
    "--".length.toLong() + boundary.length + "--".length + CRLF.length

private fun headersLength(headers: Headers): Long {
    var length = 0L
    for ((name, values) in headers.toMap()) {
        for (value in values) {
            length += name.encodedByteLength() + ": ".length + value.encodedByteLength() + CRLF.length
        }
    }
    return length
}

private fun writeMultipartTo(
    sink: Buffer,
    parts: List<RequestBody.Multipart.Part>,
    boundary: String,
) {
    for (part in parts) {
        sink.writeAscii("--")
        sink.writeAscii(boundary)
        sink.writeAscii(CRLF)
        for ((name, values) in part.headers.toMap()) {
            for (value in values) {
                sink.writeAscii(name)
                sink.writeAscii(": ")
                sink.writeAscii(value)
                sink.writeAscii(CRLF)
            }
        }
        sink.writeAscii(CRLF)
        val opened = part.body.source()
        try {
            sink.write(opened.readByteArray())
        } finally {
            opened.close()
        }
        sink.writeAscii(CRLF)
    }
    sink.writeAscii("--")
    sink.writeAscii(boundary)
    sink.writeAscii("--")
    sink.writeAscii(CRLF)
}

private fun Buffer.writeAscii(value: String) {
    write(value.encodeToByteArray())
}

private fun String.encodedByteLength(): Long = encodeToByteArray().size.toLong()
