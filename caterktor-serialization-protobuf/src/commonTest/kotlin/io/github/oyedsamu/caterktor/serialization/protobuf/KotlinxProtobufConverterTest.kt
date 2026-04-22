@file:OptIn(ExperimentalCaterktor::class, ExperimentalSerializationApi::class)

package io.github.oyedsamu.caterktor.serialization.protobuf

import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import io.github.oyedsamu.caterktor.RawBody
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Serializable
private data class ProtobufModel(
    @ProtoNumber(1) val name: String,
    @ProtoNumber(2) val count: Int,
)

class KotlinxProtobufConverterTest {

    private val converter = KotlinxProtobufConverter()

    @Test
    fun supportsCommonProtobufMediaTypes() {
        assertTrue(converter.supports("application/x-protobuf"))
        assertTrue(converter.supports("application/protobuf"))
        assertFalse(converter.supports("application/json"))
    }

    @Test
    fun encodeThenDecodeRoundTripsProtobuf() {
        val original = ProtobufModel(name = "Grace", count = 9)

        val bytes = converter.encode(original, typeOf<ProtobufModel>(), "application/x-protobuf")
        val decoded: ProtobufModel = converter.decode(
            raw = RawBody(bytes, "application/x-protobuf"),
            type = typeOf<ProtobufModel>(),
        )

        assertEquals(original, decoded)
    }
}
