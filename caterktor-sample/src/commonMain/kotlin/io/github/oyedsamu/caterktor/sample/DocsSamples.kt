package io.github.oyedsamu.caterktor.sample

import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import io.github.oyedsamu.caterktor.Headers
import io.github.oyedsamu.caterktor.CaterKtor
import io.github.oyedsamu.caterktor.HttpStatus
import io.github.oyedsamu.caterktor.NetworkRequest
import io.github.oyedsamu.caterktor.HttpMethod
import io.github.oyedsamu.caterktor.NetworkEvent
import io.github.oyedsamu.caterktor.NetworkResponse
import io.github.oyedsamu.caterktor.RequestBody
import io.github.oyedsamu.caterktor.ResponseBody
import io.github.oyedsamu.caterktor.Transport
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.io.Buffer
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

    @OptIn(ExperimentalCaterktor::class)
    public suspend fun progressEventsSnippet(): ProgressSampleRun = coroutineScope {
        val uploadPayload = "sample upload".encodeToByteArray()
        val downloadPayload = "sample download".encodeToByteArray()
        val progressEvents = Channel<NetworkEvent>(Channel.UNLIMITED)
        val client = CaterKtor {
            transport = Transport { request ->
                request.body?.bytes()
                NetworkResponse(
                    status = HttpStatus.OK,
                    headers = Headers.Empty,
                    body = ResponseBody.Source(
                        sourceFactory = { Buffer().also { it.write(downloadPayload) } },
                        contentType = "application/octet-stream",
                        contentLength = downloadPayload.size.toLong(),
                    ),
                )
            }
        }
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            client.events.collect { event ->
                when (event) {
                    is NetworkEvent.UploadProgress,
                    is NetworkEvent.DownloadProgress,
                    -> progressEvents.send(event)
                    else -> Unit
                }
            }
        }

        try {
            val response = client.execute(
                NetworkRequest(
                    method = HttpMethod.POST,
                    url = "https://caterktor.test/upload",
                    body = RequestBody.Source(
                        sourceFactory = { Buffer().also { it.write(uploadPayload) } },
                        contentType = "application/octet-stream",
                        contentLength = uploadPayload.size.toLong(),
                    ),
                ),
            )
            val responseBytes = response.body.bytes()
            val upload = withTimeout(1_000L) {
                progressEvents.receiveMatching<NetworkEvent.UploadProgress>()
            }
            val download = withTimeout(1_000L) {
                progressEvents.receiveMatching<NetworkEvent.DownloadProgress>()
            }

            ProgressSampleRun(
                uploadedBytes = upload.bytesSent,
                uploadTotalBytes = upload.totalBytes,
                downloadedBytes = download.bytesRead,
                downloadTotalBytes = download.totalBytes,
                responseBytes = responseBytes.size,
            )
        } finally {
            collector.cancel()
            progressEvents.close()
            client.close()
        }
    }
}

@OptIn(ExperimentalCaterktor::class)
private suspend inline fun <reified T : NetworkEvent> Channel<NetworkEvent>.receiveMatching(): T {
    while (true) {
        val event = receive()
        if (event is T) return event
    }
}
