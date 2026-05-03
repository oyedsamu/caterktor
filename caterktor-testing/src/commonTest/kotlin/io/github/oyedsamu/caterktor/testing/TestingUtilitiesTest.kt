@file:OptIn(ExperimentalCaterktor::class)

package io.github.oyedsamu.caterktor.testing

import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import io.github.oyedsamu.caterktor.Headers
import io.github.oyedsamu.caterktor.HttpMethod
import io.github.oyedsamu.caterktor.HttpStatus
import io.github.oyedsamu.caterktor.NetworkRequest
import io.github.oyedsamu.caterktor.RequestBody
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TestingUtilitiesTest {

    @Test
    fun fakeNetworkClientReturnsQueuedResponsesAndRecordsRequests() = runTest {
        val fake = FakeNetworkClient()
            .enqueue(jsonResponse("""{"ok":true}"""))

        val response = fake.execute(
            NetworkRequest(
                method = HttpMethod.POST,
                url = "https://example.test/items",
                headers = Headers { set("X-Test", "yes") },
                body = RequestBody.Text("hello"),
            ),
        )

        response.assertThat {
            hasStatus(HttpStatus.OK)
            hasHeader("Content-Type", "application/json; charset=UTF-8")
            hasBodyText("""{"ok":true}""")
        }
        assertEquals(1, fake.requests.size)
        fake.requests.single().assertThat {
            hasMethod(HttpMethod.POST)
            hasUrl("https://example.test/items")
            hasHeader("X-Test", "yes")
            hasBodyText("hello")
        }
    }

    @Test
    fun fakeTransportDslMatchesPathTemplatesAndExposesParameters() = runTest {
        val transport = FakeTransport {
            get("/users/{id}") { match ->
                jsonResponse("""{"id":"${match.pathParameters["id"]}"}""")
            }
        }

        val response = transport.execute(
            NetworkRequest(
                method = HttpMethod.GET,
                url = "https://example.test/users/42?expand=true",
            ),
        )

        response.assertThat {
            hasStatus(HttpStatus.OK)
            hasBodyText("""{"id":"42"}""")
        }
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun fakeTransportDslReportsReadableMethodMismatchFailures() = runTest {
        val transport = FakeTransport {
            get("/users/{id}", jsonResponse("""{"ok":true}"""))
        }

        val error = assertFailsWith<AssertionError> {
            transport.execute(
                NetworkRequest(
                    method = HttpMethod.POST,
                    url = "https://example.test/users/42",
                ),
            )
        }

        val message = error.message.orEmpty()
        assertTrue(message.contains("No fake rule matched POST /users/42."), message)
        assertTrue(message.contains("Path matched, but method did not. Allowed methods: GET"), message)
        assertTrue(message.contains(" - GET /users/{id}"), message)
    }

    @Test
    fun testServerRoutesRequestsByMethodAndPath() = runTest {
        val server = CaterktorTestServer()
            .route(
                method = HttpMethod.GET,
                path = "/users",
                response = jsonResponse("""{"users":[]}"""),
            )
        val client = server.client()

        val response = client.execute(
            NetworkRequest(
                method = HttpMethod.GET,
                url = "https://caterktor.test/users?limit=10",
            ),
        )

        response.assertThat {
            hasStatus(HttpStatus.OK)
            hasBodyText("""{"users":[]}""")
        }
        assertEquals(1, server.requests.size)
        server.requests.single().assertThat {
            hasMethod(HttpMethod.GET)
            hasUrl("https://caterktor.test/users?limit=10")
        }
    }

    @Test
    fun testServerRoutesPathTemplatesInMemory() = runTest {
        val server = CaterktorTestServer()
            .route(
                method = HttpMethod.GET,
                path = "/users/{id}",
                response = jsonResponse("""{"user":true}"""),
            )
        val client = server.client()

        val response = client.execute(
            NetworkRequest(
                method = HttpMethod.GET,
                url = "https://caterktor.test/users/42?expand=true",
            ),
        )

        response.assertThat {
            hasStatus(HttpStatus.OK)
            hasBodyText("""{"user":true}""")
        }
    }

    @Test
    fun fakeTransportRecordsRequestsConcurrently() = runTest {
        val transport = FakeTransport {
            get("/ping", jsonResponse("""{"ok":true}"""))
        }

        val n = 20
        List(n) {
            async {
                transport.execute(NetworkRequest(method = HttpMethod.GET, url = "https://example.test/ping"))
            }
        }.awaitAll()

        assertEquals(n, transport.requests.size)
    }

    @Test
    fun assertionDslThrowsAssertionErrorWithExpectedAndActualValues() {
        val error = assertFailsWith<AssertionError> {
            testResponse(HttpStatus.NoContent).assertThat {
                hasStatus(HttpStatus.OK)
            }
        }

        assertEquals("Expected status <200>, but was <204>", error.message)
    }
}
