package com.byoyedele.caterktor

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable

class KtorNetworkClientTest {

    @Test
    fun parsesWrappedResponseBody() = runTest {
        val client = networkClient(
            //language=JSON
            """
                {
                  "data": {
                    "name": "CaterKtor"
                  }
                }
            """.trimIndent()
        )

        val result = client.get<TestResponse>("/profile")

        assertEquals(Result.success(TestResponse("CaterKtor")), result)
    }

    @Test
    fun createsWrappedJsonRequestBody() = runTest {
        var request: HttpRequestData? = null
        val client = networkClient("""{}""") { request = it }

        val result = client.post<TestRequest, Unit>("/profile", TestRequest("CaterKtor"))

        assertEquals(Result.success(Unit), result)
        val body = request?.body as TextContent
        assertEquals("""{"data":{"name":"CaterKtor"}}""", body.text)
    }

    @Test
    fun createsPlainJsonRequestBody() = runTest {
        var request: HttpRequestData? = null
        val client = networkClient("""{}""") { request = it }

        val result = client.plainJsonPost<TestRequest, Unit>("/profile", TestRequest("CaterKtor"))

        assertEquals(Result.success(Unit), result)
        val body = request?.body as TextContent
        assertEquals("""{"name":"CaterKtor"}""", body.text)
    }

    @Test
    fun passesHeadersAndQueryParameters() = runTest {
        var request: HttpRequestData? = null
        val client = networkClient("""{"data":{"name":"CaterKtor"}}""") { request = it }

        val result = client.get<TestResponse>("/profile") {
            header("X-Test", "true")
            queryParameter("country", "NG")
            queryParameter("country", "UG")
        }

        assertEquals(Result.success(TestResponse("CaterKtor")), result)
        assertEquals("true", request?.headers?.get("X-Test"))
        assertEquals(listOf("NG", "UG"), request?.url?.parameters?.getAll("country"))
    }

    @Test
    fun logsRequestAndResponseWithRedactedSensitiveHeaders() = runTest {
        val logs = mutableListOf<String>()
        val client = networkClient(
            responseBody = """{"data":{"name":"CaterKtor"}}""",
            logger = caterKtorLogger { logs.add(it) },
        )

        val result = client.get<TestResponse>("/profile") {
            header("Authorization", "Bearer secret")
            header("X-Request-Id", "request-id")
            queryParameter("country", "NG")
        }

        assertEquals(Result.success(TestResponse("CaterKtor")), result)
        assertEquals(
            listOf(
                "CaterKtor -> GET /profile?country=NG headers={Authorization=<redacted>, X-Request-Id=request-id}",
                "CaterKtor <- 200 OK for GET /profile",
            ),
            logs,
        )
    }

    @Test
    fun mapsErrorResponseToNetworkException() = runTest {
        val client = networkClient(
            responseBody = //language=JSON
            """
                {
                  "errors": [
                    {
                      "code": "INTERNAL_SERVER_ERROR",
                      "detail": "There was a problem processing your request",
                      "status": 500,
                      "title": "Internal Server Error"
                    }
                  ]
                }
            """.trimIndent(),
            responseCode = HttpStatusCode.InternalServerError,
        )

        val result = client.get<TestResponse>("/profile")

        val exception = result.exceptionOrNull()
        assertIs<NetworkException>(exception)
        assertEquals(500, exception.httpCode)
        assertEquals("INTERNAL_SERVER_ERROR", exception.errorCode)
        assertEquals("There was a problem processing your request", exception.errorDetail)
    }

    @Test
    fun failsWhenNonNullableDataIsMissing() = runTest {
        val client = networkClient("{}")

        val result = client.get<TestResponse>("/profile")

        assertTrue(result.exceptionOrNull() is NullPointerException)
    }

    private fun networkClient(
        responseBody: String,
        responseCode: HttpStatusCode = HttpStatusCode.OK,
        logger: CaterKtorLogger = CaterKtorLogger.None,
        onRequest: (HttpRequestData) -> Unit = {},
    ): NetworkClient {
        val engine = MockEngine {
            onRequest(it)
            respond(
                content = ByteReadChannel(responseBody),
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                status = responseCode,
            )
        }
        val httpClient = HttpClient(engine) {
            defaultRequest {
                contentType(ContentType.Application.Json)
            }
        }
        return KtorNetworkClient(
            httpClient = httpClient,
            logger = logger,
        )
    }
}

@Serializable
private data class TestRequest(
    val name: String,
)

@Serializable
private data class TestResponse(
    val name: String,
)
