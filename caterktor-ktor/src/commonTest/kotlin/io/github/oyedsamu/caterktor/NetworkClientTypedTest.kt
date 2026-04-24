@file:OptIn(ExperimentalCaterktor::class)

package io.github.oyedsamu.caterktor

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KType
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for the typed call helpers on [NetworkClient] — `get`, `post`, and the
 * body-encoding/decoding pipeline sitting on top of [BodyConverter].
 */
class NetworkClientTypedTest {

    private data class TestModel(val name: String, val count: Int)
    private data class CreateRequest(val name: String)

    /**
     * Minimal hand-rolled JSON converter for two specific types used in these
     * tests. Keeps the test module free of the kotlinx.serialization dependency.
     */
    private class TestJsonConverter : BodyConverter {
        override fun supports(contentType: String): Boolean =
            contentType == "application/json"

        override fun <T : Any> encode(value: T, type: KType, contentType: String): ByteArray {
            val text = when (value) {
                is CreateRequest -> """{"name":"${value.name}"}"""
                is TestModel -> """{"name":"${value.name}","count":${value.count}}"""
                else -> error("Unsupported value type in test converter: ${value::class}")
            }
            return text.encodeToByteArray()
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> decode(raw: RawBody, type: KType): T {
            val text = raw.bytes.decodeToString()
            return when (type.classifier) {
                TestModel::class -> {
                    val name = parseStringField(text, "name") ?: error("missing name: $text")
                    val count = parseIntField(text, "count") ?: error("missing count: $text")
                    TestModel(name, count) as T
                }
                CreateRequest::class -> {
                    val name = parseStringField(text, "name") ?: error("missing name: $text")
                    CreateRequest(name) as T
                }
                else -> error("Unsupported type in test converter: ${type.classifier}")
            }
        }

        private fun parseStringField(json: String, key: String): String? {
            val marker = "\"$key\":\""
            val start = json.indexOf(marker).takeIf { it >= 0 } ?: return null
            val from = start + marker.length
            val end = json.indexOf('"', from)
            if (end < 0) return null
            return json.substring(from, end)
        }

        private fun parseIntField(json: String, key: String): Int? {
            val marker = "\"$key\":"
            val start = json.indexOf(marker).takeIf { it >= 0 } ?: return null
            var i = start + marker.length
            while (i < json.length && json[i] == ' ') i++
            val from = i
            while (i < json.length && (json[i].isDigit() || json[i] == '-')) i++
            if (from == i) return null
            return json.substring(from, i).toInt()
        }
    }

    private fun clientWith(
        converter: BodyConverter? = TestJsonConverter(),
        baseUrl: String? = null,
        engine: MockEngine,
    ): NetworkClient = CaterKtor {
        transport = KtorTransport(HttpClient(engine))
        if (converter != null) addConverter(converter)
        this.baseUrl = baseUrl
    }

    @Test
    fun get_decodes_success_with_matching_converter() = runTest {
        val engine = MockEngine { _ ->
            respond(
                content = """{"name":"alice","count":3}""".encodeToByteArray(),
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val client = clientWith(engine = engine)

        val result = client.get<TestModel>("https://example.test/item")

        val success = assertIs<NetworkResult.Success<TestModel>>(result)
        assertEquals(TestModel("alice", 3), success.body)
        assertEquals(HttpStatus.OK, success.status)
        assertEquals(1, success.attempts)
        assertTrue(success.requestId.isNotBlank())
    }

    @Test
    fun get_returns_http_failure_on_404() = runTest {
        val engine = MockEngine { _ ->
            respond(
                content = """{"error":"not found"}""".encodeToByteArray(),
                status = HttpStatusCode.NotFound,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val client = clientWith(engine = engine)

        val result = client.get<TestModel>("https://example.test/item")

        val failure = assertIs<NetworkResult.Failure>(result)
        val httpErr = assertIs<NetworkError.Http>(failure.error)
        assertEquals(HttpStatus.NotFound, httpErr.status)
        assertEquals("application/json", httpErr.body.raw?.contentType)
    }

    @Test
    fun get_returns_serialization_failure_when_no_converter_registered() = runTest {
        val engine = MockEngine { _ ->
            respond(
                content = """{"name":"alice","count":3}""".encodeToByteArray(),
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val client = clientWith(converter = null, engine = engine)

        val result = client.get<TestModel>("https://example.test/item")

        val failure = assertIs<NetworkResult.Failure>(result)
        val err = assertIs<NetworkError.Serialization>(failure.error)
        assertEquals(SerializationPhase.Decoding, err.phase)
    }

    @Test
    fun post_encodes_body_and_decodes_response() = runTest {
        var seenMethod: String? = null
        var seenBody: ByteArray? = null
        var seenContentType: String? = null
        val engine = MockEngine { request ->
            seenMethod = request.method.value
            seenBody = request.body.toByteArray()
            seenContentType = request.body.contentType?.toString()
                ?: request.headers["Content-Type"]
            respond(
                content = """{"name":"alice","count":1}""".encodeToByteArray(),
                status = HttpStatusCode.Created,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val client = clientWith(engine = engine)

        val result = client.post<TestModel, CreateRequest>(
            url = "https://example.test/items",
            body = CreateRequest(name = "alice"),
        )

        val success = assertIs<NetworkResult.Success<TestModel>>(result)
        assertEquals(TestModel("alice", 1), success.body)
        assertEquals("POST", seenMethod)
        assertContentEquals("""{"name":"alice"}""".encodeToByteArray(), seenBody)
        assertTrue(
            seenContentType.orEmpty().contains("application/json"),
            "expected Content-Type to contain application/json, got: $seenContentType",
        )
    }

    @Test
    fun get_resolves_relative_url_against_base_url() = runTest {
        var seenUrl: String? = null
        val engine = MockEngine { request ->
            seenUrl = request.url.toString()
            respond(
                content = """{"name":"alice","count":0}""".encodeToByteArray(),
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val client = clientWith(baseUrl = "https://api.test/v1", engine = engine)

        val result = client.get<TestModel>("items/42")

        assertIs<NetworkResult.Success<TestModel>>(result)
        assertEquals("https://api.test/v1/items/42", seenUrl)
    }

    @Test
    fun get_expands_path_template_before_resolving() = runTest {
        var seenUrl: String? = null
        val engine = MockEngine { request ->
            seenUrl = request.url.toString()
            respond(
                content = """{"name":"bob","count":7}""".encodeToByteArray(),
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val client = clientWith(baseUrl = "https://api.test", engine = engine)

        val result = client.get<TestModel>(
            url = "users/{id}",
            pathParams = mapOf("id" to "42"),
        )

        assertIs<NetworkResult.Success<TestModel>>(result)
        assertEquals("https://api.test/users/42", seenUrl)
    }

    @Test
    fun get_appends_query_params_after_path_template_and_base_url_resolution() = runTest {
        var seenUrl: String? = null
        val engine = MockEngine { request ->
            seenUrl = request.url.toString()
            respond(
                content = """{"name":"search","count":2}""".encodeToByteArray(),
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val client = clientWith(baseUrl = "https://api.test/v1", engine = engine)

        val result = client.get<TestModel>(
            url = "users/{id}/items",
            pathParams = mapOf("id" to "a/b"),
            queryParams = QueryParameters {
                add("tag", "kmp")
                add("tag", "networking")
                add("empty", null)
            },
        )

        assertIs<NetworkResult.Success<TestModel>>(result)
        assertEquals("https://api.test/v1/users/a%2Fb/items?tag=kmp&tag=networking", seenUrl)
    }
}
