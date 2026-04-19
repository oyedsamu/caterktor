package io.github.oyedsamu.caterktor

/**
 * The body of an HTTP error response.
 *
 * [raw] is always populated when the server sent a body. [parsed] is populated
 * only when a configured error parser successfully parsed the body into a
 * structured form. The error-parser workstream arrives in a later milestone;
 * this type is intentionally open for extension.
 *
 * ## Future shape
 * When the error-parser workstream lands it will introduce a typed
 * `ParsedError` sealed interface, and the [parsed] property will be
 * narrowed from [Any]`?` to `ParsedError?`. The rename is a source-breaking
 * change scoped behind [ExperimentalCaterktor] until then — consumers SHOULD
 * treat [parsed] as a debug aid, not a typed contract.
 *
 * @property raw The raw bytes the server returned, if any. `null` means the
 *   server sent no body (e.g. an empty 500).
 * @property parsed The result of a configured error parser, if one ran
 *   successfully. `null` means no parser was configured, none matched, or
 *   parsing failed. Currently typed as [Any]`?`; see "Future shape" above.
 */
public class ErrorBody(
    public val raw: RawBody?,
    public val parsed: Any?,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ErrorBody) return false
        if (raw != other.raw) return false
        if (parsed != other.parsed) return false
        return true
    }

    override fun hashCode(): Int {
        var result = raw?.hashCode() ?: 0
        result = 31 * result + (parsed?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String = "ErrorBody(raw=$raw, parsed=$parsed)"

    public companion object {
        /** An [ErrorBody] with no raw bytes and no parsed value. */
        public val Empty: ErrorBody = ErrorBody(raw = null, parsed = null)
    }
}
