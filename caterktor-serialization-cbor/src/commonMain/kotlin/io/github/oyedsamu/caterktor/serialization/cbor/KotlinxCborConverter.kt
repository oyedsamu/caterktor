package io.github.oyedsamu.caterktor.serialization.cbor

import io.github.oyedsamu.caterktor.BodyConverter
import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import io.github.oyedsamu.caterktor.RawBody
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.serializer
import kotlin.reflect.KType

/**
 * A [BodyConverter] that serializes and deserializes CBOR
 * (Concise Binary Object Representation, RFC 7049) using
 * [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization).
 *
 * CBOR is a compact binary format whose data model is a superset of JSON's,
 * making it well-suited for bandwidth-sensitive or embedded environments that
 * still require structured, schema-driven payloads.
 *
 * ## Binary codec
 * Unlike the JSON converter, no string intermediary is involved. Encoding
 * writes binary CBOR bytes via [Cbor.encodeToByteArray] and decoding reads
 * those bytes directly via [Cbor.decodeFromByteArray].
 *
 * ## Accepted content-type
 * [supports] returns `true` for `"application/cbor"` only. The bare
 * media-type must be provided — parameters such as `; charset=UTF-8` must be
 * stripped by the caller before invoking [supports].
 *
 * ## Thread safety
 * Instances are stateless beyond the immutable [cbor] configuration and are
 * safe for concurrent use across coroutines and threads.
 *
 * @property cbor The [Cbor] instance used for all encode/decode operations.
 */
@ExperimentalCaterktor
@OptIn(ExperimentalSerializationApi::class)
public class KotlinxCborConverter(
    public val cbor: Cbor = Cbor,
) : BodyConverter {

    /**
     * Returns `true` when [contentType] is exactly `"application/cbor"`.
     *
     * Only the bare media-type is matched — parameters such as `; charset=UTF-8`
     * must be stripped by the caller before invoking this method.
     *
     * @param contentType The bare content-type string to test.
     */
    public override fun supports(contentType: String): Boolean =
        contentType == "application/cbor"

    /**
     * Serializes [value] to a CBOR-encoded byte array.
     *
     * @param value The object to serialize. Must be an instance of a
     *   `@Serializable`-annotated type reachable from [type].
     * @param type The [KType] describing the exact type of [value], including
     *   generic type arguments.
     * @param contentType The target content-type (informational; not used by this
     *   implementation).
     * @return The CBOR binary representation of [value].
     * @throws kotlinx.serialization.SerializationException if [value] cannot be
     *   serialized — e.g. a missing serializer or an unregistered polymorphic type.
     */
    public override fun <T : Any> encode(value: T, type: KType, contentType: String): ByteArray {
        val ser = unsafeCastSerializer<T>(serializer(type))
        return cbor.encodeToByteArray(ser, value)
    }

    /**
     * Deserializes [raw] bytes from CBOR to a value of type [T].
     *
     * [raw]'s bytes are passed directly to the CBOR decoder — no string
     * conversion step is performed.
     *
     * @param raw The raw response body. [RawBody.bytes] must contain valid
     *   CBOR-encoded data.
     * @param type The [KType] of the target type, including generic type arguments.
     * @return The deserialized value.
     * @throws kotlinx.serialization.SerializationException if the bytes cannot be
     *   parsed as valid CBOR, or if the CBOR structure does not match [type].
     */
    public override fun <T : Any> decode(raw: RawBody, type: KType): T {
        val deser = unsafeCastSerializer<T>(serializer(type))
        return cbor.decodeFromByteArray(deser, raw.bytes)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> unsafeCastSerializer(ser: Any): KSerializer<T> = ser as KSerializer<T>
}
