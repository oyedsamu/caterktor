package io.github.oyedsamu.caterktor.sample

import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import io.github.oyedsamu.caterktor.Headers
import io.github.oyedsamu.caterktor.HttpStatus
import io.github.oyedsamu.caterktor.NetworkRequest
import io.github.oyedsamu.caterktor.HttpMethod
import io.github.oyedsamu.caterktor.testing.CaterktorTestServer
import io.github.oyedsamu.caterktor.testing.jsonResponse
import io.github.oyedsamu.caterktor.testing.testResponse

/**
 * README and KDoc sample snippets live here as ordinary Kotlin code.
 *
 * `DocsSamplesCompileTest` invokes these functions, so drift in the public
 * examples fails CI instead of silently rotting in Markdown.
 */
public object DocsSamples {
    @OptIn(ExperimentalCaterktor::class)
    public suspend fun quickStartWithAuthRefresh(): SampleRun {
        val server = CaterktorTestServer()
            .enqueue(
                testResponse(
                    status = HttpStatus.Unauthorized,
                    headers = Headers { set("Content-Type", "application/json") },
                    body = """{"error":"expired token"}""".encodeToByteArray(),
                ),
            )
            .enqueue(jsonResponse("""{"id":"42","name":"Ada"}"""))
        val tokenStore = SampleTokenStore()
        val logs = mutableListOf<String>()
        val client = sampleClient(
            transport = server,
            tokenStore = tokenStore,
            baseUrl = server.baseUrl,
            logger = { line -> logs += line },
        )

        try {
            val user = UserRepository(client).me()
            return SampleRun(
                user = user,
                refreshCalls = tokenStore.refreshCalls,
                requestCount = server.requests.size,
                authorizationHeaders = server.requests.map { it.headers["Authorization"] },
                logs = logs.toList(),
            )
        } finally {
            client.close()
        }
    }

    @OptIn(ExperimentalCaterktor::class)
    public suspend fun repositoryTestSnippet(): User {
        val server = CaterktorTestServer()
        server.route(
            method = HttpMethod.GET,
            path = "/users/me",
            response = jsonResponse("""{"id":"7","name":"Grace"}"""),
        )
        val tokenStore = SampleTokenStore(initialAccessToken = "test-token")
        val client = sampleClient(
            transport = server,
            tokenStore = tokenStore,
            baseUrl = server.baseUrl,
        )

        try {
            return UserRepository(client).me()
        } finally {
            client.close()
        }
    }

    @OptIn(ExperimentalCaterktor::class)
    public fun rawRequestSnippet(): NetworkRequest =
        NetworkRequest(
            method = HttpMethod.GET,
            url = "https://caterktor.test/users/me",
            headers = Headers { set("Accept", "application/json") },
        )
}
