package io.github.oyedsamu.caterktor

/**
 * A raw HTTP body that has not been (or could not be) parsed into a structured form.
 *
 * Carried by [NetworkError.Serialization] when decoding fails, and by [ErrorBody]
 * when an HTTP error response includes a body.
 *
 * The contents are held as a defensive copy of [bytes]; subsequent mutations to the
 * array passed into the constructor do not affect this instance.
 *
 * @property bytes The raw body bytes as a fresh defensive copy.
 * @property contentType The value of the `Content-Type` response header, if any,
 *   without parameter parsing. `null` when the server sent no `Content-Type`.
 */
public class RawBody(
    bytes: ByteArray,
    public val contentType: String?,
) {
    private val bytesStorage: ByteArray = bytes.copyOf()

    /**
     * The raw body bytes as a fresh defensive copy.
     */
    public val bytes: ByteArray
        get() = bytesStorage.copyOf()

    /**
     * Decodes [bytes] as a string using the given [charset] name.
     *
     * Only `"UTF-8"` is guaranteed across all KMP targets; other charset names may throw
     * on platforms whose charset registry lacks them. Defaults to `UTF-8`.
     */
    public fun asString(charset: String = "UTF-8"): String =
        bytesStorage.decodeToStringWith(charset)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RawBody) return false
        if (contentType != other.contentType) return false
        return bytesStorage.contentEquals(other.bytesStorage)
    }

    override fun hashCode(): Int {
        var result = bytesStorage.contentHashCode()
        result = 31 * result + (contentType?.hashCode() ?: 0)
        return result
    }

    /**
     * Returns a compact, log-safe summary. Deliberately does not include the body
     * contents — the size and content-type are sufficient for debugging and avoid
     * accidentally leaking bodies into logs.
     */
    override fun toString(): String =
        "RawBody(contentType=$contentType, size=${bytesStorage.size})"
}

/**
 * Platform-portable charset decoding.
 *
 * `ByteArray.decodeToString()` always assumes UTF-8, so for non-UTF-8 charsets we
 * cannot portably decode in commonMain without pulling in a platform-specific
 * charset library. For now we support UTF-8 exactly; other charset names throw
 * [IllegalArgumentException]. A future workstream may route this through a
 * pluggable charset registry.
 */
private fun ByteArray.decodeToStringWith(charset: String): String {
    val normalized = charset.trim().uppercase().replace("_", "-")
    return when (normalized) {
        "UTF-8", "UTF8" -> this.decodeToString()
        else -> throw IllegalArgumentException(
            "Unsupported charset '$charset' in common code. Only UTF-8 is currently supported.",
        )
    }
}
