package io.github.oyedsamu.caterktor.serialization.protobuf

import io.github.oyedsamu.caterktor.BodyConverter
import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import io.github.oyedsamu.caterktor.RawBody
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.serializer
import kotlin.reflect.KType

/**
 * A [BodyConverter] that encodes and decodes request/response bodies using
 * [kotlinx.serialization.protobuf.ProtoBuf].
 *
 * Protocol Buffers is a compact binary serialization format — the produced bytes are
 * **not** human-readable, unlike JSON or XML.
 *
 * ### Default behaviour
 * The default [ProtoBuf] instance has `encodeDefaults = false`, meaning fields that hold
 * their default value are omitted from the encoded output (same behaviour as the JSON
 * converter).  Supply a custom [ProtoBuf] instance to the constructor if you need
 * different settings.
 *
 * ### Accepted content types
 * Both `"application/x-protobuf"` (the most widely used variant) and
 * `"application/protobuf"` are accepted by [supports].
 *
 * ### Thread safety
 * Instances are stateless beyond the (immutable) [protoBuf] configuration object and are
 * therefore safe to share across coroutines and threads.
 */
@ExperimentalCaterktor
@OptIn(ExperimentalSerializationApi::class)
public class KotlinxProtobufConverter(
    public val protoBuf: ProtoBuf = ProtoBuf,
) : BodyConverter {

    override fun supports(contentType: String): Boolean =
        contentType == "application/x-protobuf" || contentType == "application/protobuf"

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> encode(value: T, type: KType, contentType: String): ByteArray {
        val ser = serializer(type) as KSerializer<T>
        return protoBuf.encodeToByteArray(ser, value)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> decode(raw: RawBody, type: KType): T {
        val deser = serializer(type) as KSerializer<T>
        return protoBuf.decodeFromByteArray(deser, raw.bytes)
    }
}
