package io.github.oyedsamu.caterktor.sample

import io.github.oyedsamu.caterktor.CaterKtor
import io.github.oyedsamu.caterktor.Headers
import io.github.oyedsamu.caterktor.HttpMethod
import io.github.oyedsamu.caterktor.HttpStatus
import io.github.oyedsamu.caterktor.NetworkRequest
import io.github.oyedsamu.caterktor.NetworkResponse
import io.github.oyedsamu.caterktor.NetworkResult
import io.github.oyedsamu.caterktor.get
import io.github.oyedsamu.caterktor.Transport
import io.github.oyedsamu.caterktor.serialization.json.KotlinxJsonConverter
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The realistic stable surface, across module boundaries: a client, a JSON converter and a
 * typed call, with no `@OptIn(ExperimentalCaterktor::class)` anywhere in this file.
 *
 * [io.github.oyedsamu.caterktor.StableApiTest] covers the same ground inside `caterktor-core`.
 * This one exists because a user assembles a client from several artifacts, and the opt-in
 * requirement only truly disappears when every one of them is stable.
 */
class StableSurfaceTest {

    @Serializable
    private data class User(val id: String, val name: String)

    private class StubTransport(private val json: String) : Transport {
        override suspend fun execute(request: NetworkRequest): NetworkResponse =
            NetworkResponse(
                HttpStatus.OK,
                Headers.Builder().set("Content-Type", "application/json").build(),
                json.encodeToByteArray(),
            )
    }

    @Test
    fun a_json_client_needs_no_opt_in() = runTest {
        val client = CaterKtor {
            transport = StubTransport("""{"id":"u1","name":"Ada"}""")
            baseUrl = "https://api.example.test"
            addConverter(KotlinxJsonConverter())
        }

        val response = client.execute(
            NetworkRequest(method = HttpMethod.GET, url = "https://api.example.test/users/me"),
        )
        assertEquals(HttpStatus.OK, response.status)

        val typed: NetworkResult<User> = client.get("/users/me")
        assertEquals(User("u1", "Ada"), (typed as NetworkResult.Success).body)
    }
}
