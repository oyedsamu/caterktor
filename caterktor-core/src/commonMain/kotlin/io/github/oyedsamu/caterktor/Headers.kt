package io.github.oyedsamu.caterktor

/**
 * An immutable, case-insensitive HTTP header map.
 *
 * Header names are normalized to lowercase internally (per the HTTP/2 spec and common practice),
 * but the public API accepts header names in any casing. [names] returns lowercase-normalized keys.
 *
 * Multiple values per header name are supported, as required by RFC 9110 §5.2.
 *
 * ### Example
 * ```kotlin
 * val headers = Headers {
 *     set("Content-Type", "application/json")
 *     add("Accept", "application/json")
 *     add("Accept", "text/plain")
 * }
 * headers["content-type"]       // "application/json"
 * headers.getAll("accept")      // ["application/json", "text/plain"]
 * ```
 */
public class Headers private constructor(
    /** Internal storage: lowercase-normalized header name → ordered list of values. */
    private val map: Map<String, List<String>>,
) {

    /**
     * Returns the first value for [name], or `null` if the header is absent.
     * The lookup is case-insensitive.
     */
    public operator fun get(name: String): String? = map[name.lowercase()]?.firstOrNull()

    /**
     * Returns all values for [name] in insertion order, or an empty list if absent.
     * The lookup is case-insensitive.
     */
    public fun getAll(name: String): List<String> = map[name.lowercase()] ?: emptyList()

    /**
     * Returns `true` if a header with [name] is present. The lookup is case-insensitive.
     */
    public operator fun contains(name: String): Boolean = map.containsKey(name.lowercase())

    /**
     * All header names present in this map, in lowercase-normalized form.
     *
     * Note: names are always returned in lowercase, regardless of the casing used when
     * the headers were constructed.
     */
    public val names: Set<String>
        get() = map.keys

    /**
     * Returns a new [Headers] that merges entries from both this and [other].
     * If a header name exists in both, the values are concatenated (not replaced).
     */
    public operator fun plus(other: Headers): Headers {
        if (other.map.isEmpty()) return this
        if (map.isEmpty()) return other
        val merged = LinkedHashMap<String, List<String>>(map)
        for ((key, values) in other.map) {
            val existing = merged[key]
            merged[key] = if (existing == null) values else existing + values
        }
        return Headers(merged)
    }

    /**
     * Returns the raw internal map with lowercase-normalized keys.
     * Intended for serialization and logging use; do not rely on the specific map implementation.
     */
    public fun toMap(): Map<String, List<String>> = map.toMap()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Headers) return false
        return map == other.map
    }

    override fun hashCode(): Int = map.hashCode()

    /**
     * Returns a human-readable representation of all headers.
     * Values are **not** redacted here — use a dedicated redaction layer if logging to production.
     */
    override fun toString(): String = buildString {
        append("Headers{")
        map.entries.forEachIndexed { index, (name, values) ->
            if (index > 0) append(", ")
            append(name)
            append(": ")
            if (values.size == 1) {
                append(values[0])
            } else {
                append(values)
            }
        }
        append("}")
    }

    public companion object {
        /** An empty [Headers] instance with no entries. */
        public val Empty: Headers = Headers(emptyMap())

        /**
         * Creates a [Headers] instance using a [Builder] DSL.
         *
         * ### Example
         * ```kotlin
         * val headers = Headers {
         *     set("Content-Type", "application/json")
         *     add("Accept", "application/json")
         * }
         * ```
         */
        public operator fun invoke(build: Builder.() -> Unit): Headers =
            Builder().apply(build).build()

        /**
         * Creates a [Headers] instance from a list of name–value pairs.
         * If a name appears more than once, all values are preserved.
         *
         * ### Example
         * ```kotlin
         * val headers = Headers.of(
         *     "Content-Type" to "application/json",
         *     "Accept" to "application/json",
         * )
         * ```
         */
        public fun of(vararg pairs: Pair<String, String>): Headers {
            if (pairs.isEmpty()) return Empty
            val map = LinkedHashMap<String, MutableList<String>>(pairs.size)
            for ((name, value) in pairs) {
                map.getOrPut(name.lowercase()) { mutableListOf() }.add(value)
            }
            return Headers(map.mapValues { it.value.toList() })
        }
    }

    /**
     * Mutable builder for constructing a [Headers] instance.
     *
     * Use [set] to replace all values for a header name, and [add] to append an additional value.
     */
    public class Builder {
        private val map: LinkedHashMap<String, MutableList<String>> = LinkedHashMap()

        /**
         * Sets [name] to [value], replacing any previously set values for that name.
         * The name is stored in lowercase-normalized form.
         */
        public fun set(name: String, value: String): Builder {
            map[name.lowercase()] = mutableListOf(value)
            return this
        }

        /**
         * Appends [value] to the list of values for [name].
         * If [name] has no existing values it is equivalent to [set].
         * The name is stored in lowercase-normalized form.
         */
        public fun add(name: String, value: String): Builder {
            map.getOrPut(name.lowercase()) { mutableListOf() }.add(value)
            return this
        }

        /** Builds and returns the immutable [Headers] instance. */
        public fun build(): Headers =
            Headers(map.mapValues { it.value.toList() })
    }
}
