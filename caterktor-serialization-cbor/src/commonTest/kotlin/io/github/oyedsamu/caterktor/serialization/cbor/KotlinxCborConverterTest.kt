@file:OptIn(ExperimentalCaterktor::class, ExperimentalSerializationApi::class)

package io.github.oyedsamu.caterktor.serialization.cbor

import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import io.github.oyedsamu.caterktor.RawBody
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Serializable
private data class CborModel(val name: String, val count: Int)

class KotlinxCborConverterTest {

    private val converter = KotlinxCborConverter()

    @Test
    fun supportsApplicationCborOnly() {
        assertTrue(converter.supports("application/cbor"))
        assertFalse(converter.supports("application/json"))
    }

    @Test
    fun encodeThenDecodeRoundTripsCbor() {
        val original = CborModel(name = "Ada", count = 7)

        val bytes = converter.encode(original, typeOf<CborModel>(), "application/cbor")
        val decoded: CborModel = converter.decode(
            raw = RawBody(bytes, "application/cbor"),
            type = typeOf<CborModel>(),
        )

        assertEquals(original, decoded)
    }
}
