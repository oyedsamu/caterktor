package io.github.oyedsamu.caterktor

/**
 * A type-safe key for a value stored in [Attributes].
 *
 * Each key instance is distinct regardless of name — two keys with the same name
 * are different keys. Use object declarations or top-level vals to share keys
 * across modules.
 *
 * @param T The type of the value stored under this key.
 * @property name A human-readable name, used in [toString] and diagnostics.
 */
@ExperimentalCaterktor
public class AttributeKey<T : Any>(public val name: String) {
    override fun toString(): String = "AttributeKey($name)"
}

/**
 * An immutable, type-safe collection of key-value pairs.
 *
 * Attributes are the replacement for `Map<String, Any>` tags. Every key is a
 * typed [AttributeKey]; values are retrieved with [getOrNull] or [require].
 *
 * ## Construction
 *
 * Use [Attributes] companion function or [AttributesBuilder]:
 * ```kotlin
 * val attrs = Attributes {
 *     put(MyKey, myValue)
 * }
 * ```
 *
 * ## Merging
 *
 * Use [plus] to create a new [Attributes] with additional entries (later entries
 * win on key collision):
 * ```kotlin
 * val merged = base + Attributes { put(ExtraKey, value) }
 * ```
 */
@ExperimentalCaterktor
public class Attributes internal constructor(
    private val map: Map<AttributeKey<*>, Any>,
) {

    /** Returns `true` if this collection has no entries. */
    public val isEmpty: Boolean get() = map.isEmpty()

    /** Returns `true` if [key] has a value in this collection. */
    public operator fun <T : Any> contains(key: AttributeKey<T>): Boolean = map.containsKey(key)

    /** Returns the value for [key], or `null` if not present. */
    @Suppress("UNCHECKED_CAST")
    public fun <T : Any> getOrNull(key: AttributeKey<T>): T? = map[key] as T?

    /**
     * Returns the value for [key].
     * @throws IllegalStateException if [key] is not present.
     */
    public fun <T : Any> require(key: AttributeKey<T>): T =
        getOrNull(key) ?: error("No value for attribute key '$key'")

    /** Return a new [Attributes] containing all entries from both, with [other] winning on collision. */
    public operator fun plus(other: Attributes): Attributes =
        if (other.map.isEmpty()) this
        else if (map.isEmpty()) other
        else Attributes(map + other.map)

    override fun equals(other: Any?): Boolean =
        other is Attributes && map == other.map

    override fun hashCode(): Int = map.hashCode()

    override fun toString(): String = "Attributes(${map.entries.joinToString { "${it.key}=${it.value}" }})"

    public companion object {
        /** An empty [Attributes] instance. */
        public val Empty: Attributes = Attributes(emptyMap())

        /** Build an [Attributes] instance. */
        public operator fun invoke(block: AttributesBuilder.() -> Unit = {}): Attributes =
            AttributesBuilder().apply(block).build()
    }
}

/**
 * Mutable builder for [Attributes].
 */
@ExperimentalCaterktor
public class AttributesBuilder internal constructor() {
    private val map: MutableMap<AttributeKey<*>, Any> = mutableMapOf()

    /** Store [value] under [key]. Overwrites any existing value for the same key. */
    public fun <T : Any> put(key: AttributeKey<T>, value: T): AttributesBuilder = apply {
        map[key] = value
    }

    /** Remove [key] from the builder. */
    public fun <T : Any> remove(key: AttributeKey<T>): AttributesBuilder = apply {
        map.remove(key)
    }

    internal fun build(): Attributes = if (map.isEmpty()) Attributes.Empty else Attributes(map.toMap())
}
