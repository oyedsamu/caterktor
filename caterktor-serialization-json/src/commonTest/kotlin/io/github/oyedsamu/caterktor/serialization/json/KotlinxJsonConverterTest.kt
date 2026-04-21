package io.github.oyedsamu.caterktor.serialization.json

import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import io.github.oyedsamu.caterktor.RawBody
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Serializable
private data class TestModel(val name: String, val count: Int)

@OptIn(ExperimentalCaterktor::class)
class KotlinxJsonConverterTest {

    private val converter = KotlinxJsonConverter()

    // ── supports ──────────────────────────────────────────────────────────────

    @Test
    fun supports_returns_true_for_application_json() {
        assertTrue(converter.supports("application/json"))
    }

    @Test
    fun supports_returns_false_for_application_xml() {
        assertFalse(converter.supports("application/xml"))
    }

    @Test
    fun supports_returns_false_for_text_plain() {
        assertFalse(converter.supports("text/plain"))
    }

    @Test
    fun supports_returns_false_for_empty_string() {
        assertFalse(converter.supports(""))
    }

    // ── encode ────────────────────────────────────────────────────────────────

    @Test
    fun encode_produces_correct_json_bytes() {
        val model = TestModel(name = "Alice", count = 42)
        val bytes = converter.encode(model, typeOf<TestModel>(), "application/json")
        val json = bytes.decodeToString()
        assertEquals("""{"name":"Alice","count":42}""", json)
    }

    @Test
    fun encode_omits_default_values_when_using_default_json() {
        // Confirm encodeDefaults = false is in effect: all fields present anyway,
        // but verify no extra keys are emitted beyond what the model declares.
        val model = TestModel(name = "Bob", count = 0)
        val json = converter.encode(model, typeOf<TestModel>(), "application/json").decodeToString()
        assertEquals("""{"name":"Bob","count":0}""", json)
    }

    // ── decode ────────────────────────────────────────────────────────────────

    @Test
    fun decode_produces_correct_model_from_json_bytes() {
        val raw = RawBody(
            bytes = """{"name":"Carol","count":7}""".encodeToByteArray(),
            contentType = "application/json",
        )
        val model: TestModel = converter.decode(raw, typeOf<TestModel>())
        assertEquals(TestModel(name = "Carol", count = 7), model)
    }

    @Test
    fun decode_throws_serialization_exception_for_invalid_json() {
        val raw = RawBody(
            bytes = "not valid json".encodeToByteArray(),
            contentType = "application/json",
        )
        assertFailsWith<SerializationException> {
            converter.decode<TestModel>(raw, typeOf<TestModel>())
        }
    }

    @Test
    fun decode_throws_serialization_exception_for_missing_required_field() {
        val raw = RawBody(
            bytes = """{"name":"Dave"}""".encodeToByteArray(),
            contentType = "application/json",
        )
        assertFailsWith<SerializationException> {
            converter.decode<TestModel>(raw, typeOf<TestModel>())
        }
    }

    @Test
    fun decode_throws_serialization_exception_for_unknown_key_in_strict_mode() {
        val raw = RawBody(
            bytes = """{"name":"Eve","count":1,"extra":"field"}""".encodeToByteArray(),
            contentType = "application/json",
        )
        // Default Json has ignoreUnknownKeys = false, so unknown keys must throw.
        assertFailsWith<SerializationException> {
            converter.decode<TestModel>(raw, typeOf<TestModel>())
        }
    }

    // ── round-trip ────────────────────────────────────────────────────────────

    @Test
    fun encode_then_decode_round_trips_successfully() {
        val original = TestModel(name = "Frank", count = 99)
        val bytes = converter.encode(original, typeOf<TestModel>(), "application/json")
        val raw = RawBody(bytes = bytes, contentType = "application/json")
        val decoded: TestModel = converter.decode(raw, typeOf<TestModel>())
        assertEquals(original, decoded)
    }
}
