package io.github.oyedsamu.caterktor

/**
 * Ordered query parameters for typed request helpers.
 *
 * `null` values are omitted so optional filters can be passed directly without
 * forcing callers to build maps conditionally. Repeated names are preserved in
 * insertion order.
 *
 * ```kotlin
 * val params = QueryParameters {
 *     add("limit", 20)
 *     add("tag", "kmp")
 *     add("tag", "networking")
 * }
 * ```
 */
@ExperimentalCaterktor
public class QueryParameters private constructor(
    public val entries: List<Entry>,
) {

    /**
     * A single query name/value pair.
     */
    public data class Entry(
        public val name: String,
        public val value: String,
    )

    /**
     * Returns `true` when no parameters will be appended.
     */
    public fun isEmpty(): Boolean = entries.isEmpty()

    /**
     * Mutable builder for [QueryParameters].
     */
    @CaterKtorDsl
    public class Builder {
        private val entries: MutableList<Entry> = mutableListOf()

        /**
         * Add [name]=[value]. `null` values are ignored.
         */
        public fun add(name: String, value: Any?): Builder = apply {
            require(name.isNotBlank()) { "Query parameter name must not be blank." }
            if (value != null) {
                entries += Entry(name, value.toString())
            }
        }

        /**
         * Add one entry per non-null value for [name].
         */
        public fun addAll(name: String, values: Iterable<Any?>): Builder = apply {
            for (value in values) {
                add(name, value)
            }
        }

        internal fun build(): QueryParameters =
            if (entries.isEmpty()) Empty else QueryParameters(entries.toList())
    }

    public companion object {
        /**
         * Empty parameter set.
         */
        public val Empty: QueryParameters = QueryParameters(emptyList())

        /**
         * Build query parameters with the DSL.
         */
        public operator fun invoke(block: Builder.() -> Unit): QueryParameters =
            Builder().apply(block).build()
    }
}

/**
 * Build query parameters from ordered pairs.
 */
@ExperimentalCaterktor
public fun queryParameters(vararg pairs: Pair<String, Any?>): QueryParameters =
    QueryParameters {
        for ((name, value) in pairs) {
            add(name, value)
        }
    }

/**
 * Build query parameters from a map.
 *
 * Iterable values become repeated query parameters. `null` values are omitted.
 */
@ExperimentalCaterktor
public fun queryParameters(params: Map<String, Any?>): QueryParameters =
    QueryParameters {
        for ((name, value) in params) {
            if (value is Iterable<*>) {
                addAll(name, value)
            } else {
                add(name, value)
            }
        }
    }

