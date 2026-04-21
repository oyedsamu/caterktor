package io.github.oyedsamu.caterktor.serialization.json

import io.github.oyedsamu.caterktor.BodyConverter
import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import io.github.oyedsamu.caterktor.RawBody
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.reflect.KType

/**
 * A [BodyConverter] that serializes and deserializes JSON using
 * [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization).
 *
 * By default, a strict [Json] instance is used:
 * - `encodeDefaults = false` — default values are omitted from the encoded output.
 * - `ignoreUnknownKeys = false` — unknown JSON keys cause a [SerializationException]
 *   at decode time, surfacing contract mismatches early.
 *
 * To opt into leniency (e.g. `ignoreUnknownKeys = true`), supply a custom [Json]
 * instance via the primary constructor.
 *
 * ## Thread safety
 * Instances are stateless beyond the immutable [json] configuration and are safe
 * for concurrent use across coroutines and threads.
 *
 * ## Error handling
 * On encode or decode failure, a [SerializationException] is propagated to the
 * caller. The CaterKtor framework layer catches it and maps it to the appropriate
 * [io.github.oyedsamu.caterktor.NetworkError.Serialization] variant.
 *
 * @property json The [Json] instance used for all encode/decode operations.
 */
@ExperimentalCaterktor
public class KotlinxJsonConverter(
    public val json: Json = DefaultJson,
) : BodyConverter {

    /**
     * Returns `true` when [contentType] is exactly `"application/json"`.
     *
     * Only the bare media-type is matched — parameters such as `; charset=UTF-8`
     * must be stripped by the caller before invoking this method.
     *
     * @param contentType The bare content-type string to test.
     */
    public override fun supports(contentType: String): Boolean =
        contentType == "application/json"

    /**
     * Serializes [value] to a UTF-8-encoded JSON byte array.
     *
     * @param value The object to serialize. Must be an instance of a
     *   `@Serializable`-annotated type reachable from [type].
     * @param type The [KType] describing the exact type of [value], including
     *   generic type arguments.
     * @param contentType The target content-type (informational; not used by this
     *   implementation).
     * @return The JSON representation of [value] as UTF-8 bytes.
     * @throws SerializationException if [value] cannot be serialized — e.g. a
     *   missing serializer or a polymorphic type that is not registered.
     */
    @Suppress("UNCHECKED_CAST")
    public override fun <T : Any> encode(value: T, type: KType, contentType: String): ByteArray {
        val ser = serializer(type) as KSerializer<T>
        return json.encodeToString(ser, value).encodeToByteArray()
    }

    /**
     * Deserializes [raw] bytes from JSON to a value of type [T].
     *
     * [raw]'s bytes are decoded as a UTF-8 string before parsing.
     *
     * @param raw The raw response body. [RawBody.bytes] must contain valid
     *   UTF-8-encoded JSON.
     * @param type The [KType] of the target type, including generic type arguments.
     * @return The deserialized value.
     * @throws SerializationException if the bytes cannot be parsed as valid JSON, or
     *   if the JSON structure does not match [type] (e.g. a missing required field or
     *   an unknown key when [Json.ignoreUnknownKeys] is `false`).
     */
    @Suppress("UNCHECKED_CAST")
    public override fun <T : Any> decode(raw: RawBody, type: KType): T {
        val deser = serializer(type) as KSerializer<T>
        return json.decodeFromString(deser, raw.bytes.decodeToString())
    }

    private companion object {
        private val DefaultJson: Json = Json {
            encodeDefaults = false
            ignoreUnknownKeys = false
        }
    }
}
