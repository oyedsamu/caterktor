package io.github.oyedsamu.caterktor

/**
 * Registry for media-type-aware body converters.
 *
 * The registry is responsible for three things that plain [BodyConverter]
 * lists cannot express safely:
 * - constructing a deterministic `Accept` header for typed calls;
 * - dispatching response `Content-Type` headers after stripping parameters
 *   such as `charset=UTF-8`;
 * - rejecting duplicate registrations for the same bare media type.
 */
@ExperimentalCaterktor
public class ContentNegotiationRegistry private constructor(
    public val entries: List<Entry>,
) {

    /**
     * A single converter registration.
     *
     * @property contentType Bare media type, e.g. `"application/json"`.
     * @property converter Converter used for matching request/response bodies.
     * @property quality Optional `Accept` quality weight in the inclusive range
     *   `0.0..1.0`.
     */
    public data class Entry(
        public val contentType: String,
        public val converter: BodyConverter,
        public val quality: Double,
    )

    /** Registered bare media types in `Accept` order. */
    public val acceptedContentTypes: List<String> =
        entries.map { it.contentType }

    /** Default `Accept` header value, or `null` when nothing is registered. */
    public val acceptHeader: String? =
        entries.takeIf { it.isNotEmpty() }?.joinToString(", ") { entry ->
            if (entry.quality >= 1.0) {
                entry.contentType
            } else {
                "${entry.contentType}; q=${entry.quality.toAcceptQuality()}"
            }
        }

    /**
     * Returns the converter registered for [contentTypeHeader].
     *
     * Parameters such as `charset=UTF-8` are ignored for matching, but the
     * original header is still preserved in [RawBody] for converters that care.
     */
    public fun converterFor(contentTypeHeader: String?): BodyConverter? {
        val bare = bareContentType(contentTypeHeader) ?: return null
        return entries.firstOrNull { it.contentType == bare }?.converter
    }

    /** Create a mutable builder initialized with this registry's entries. */
    public fun toBuilder(): Builder = Builder(entries)

    public class Builder internal constructor(
        entries: List<Entry> = emptyList(),
    ) {
        private val entries: MutableList<Entry> = entries.toMutableList()

        /**
         * Register [converter] for [contentType].
         *
         * [contentType] may include parameters, but only the bare media type is
         * stored and matched. Registering the same bare media type twice is an
         * error because dispatch would otherwise depend on hidden ordering.
         */
        public fun register(
            contentType: String,
            converter: BodyConverter,
            quality: Double = 1.0,
        ): Builder = apply {
            val bare = requireNotNull(bareContentType(contentType)) {
                "contentType must include a media type, was '$contentType'"
            }
            require(quality in 0.0..1.0) {
                "quality must be in 0.0..1.0, was $quality"
            }
            require(entries.none { it.contentType == bare }) {
                "A converter is already registered for '$bare'"
            }
            entries += Entry(bare, converter, quality)
        }

        public fun build(): ContentNegotiationRegistry =
            if (entries.isEmpty()) Empty else ContentNegotiationRegistry(entries.toList())
    }

    public companion object {
        public val Empty: ContentNegotiationRegistry = ContentNegotiationRegistry(emptyList())

        /**
         * Extract and normalize the bare media type from a `Content-Type` or
         * `Accept` value.
         */
        public fun bareContentType(contentTypeHeader: String?): String? =
            contentTypeHeader
                ?.substringBefore(';')
                ?.trim()
                ?.lowercase()
                ?.takeIf { it.isNotEmpty() }

        public operator fun invoke(block: Builder.() -> Unit): ContentNegotiationRegistry =
            Builder().apply(block).build()
    }
}

private fun Double.toAcceptQuality(): String {
    val clamped = coerceIn(0.0, 1.0)
    val scaled = (clamped * 1000).toInt()
    val whole = scaled / 1000
    val fraction = (scaled % 1000).toString().padStart(3, '0').trimEnd('0')
    return if (fraction.isEmpty()) whole.toString() else "$whole.$fraction"
}
