package io.github.oyedsamu.caterktor.testing

import io.github.oyedsamu.caterktor.CloseableTransport
import io.github.oyedsamu.caterktor.ErrorBody
import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import io.github.oyedsamu.caterktor.Headers
import io.github.oyedsamu.caterktor.HttpStatus
import io.github.oyedsamu.caterktor.NetworkError
import io.github.oyedsamu.caterktor.NetworkErrorException
import io.github.oyedsamu.caterktor.NetworkRequest
import io.github.oyedsamu.caterktor.NetworkResponse
import io.github.oyedsamu.caterktor.RawBody

/**
 * Queue-backed [io.github.oyedsamu.caterktor.Transport] for tests.
 *
 * Each call records the request and consumes one enqueued response or failure.
 * If the queue is empty, [defaultResponse] is returned.
 */
@ExperimentalCaterktor
public class FakeTransport(
    public val defaultResponse: NetworkResponse = testResponse(),
) : CloseableTransport {

    private val scriptedResults: ArrayDeque<ScriptedResult> = ArrayDeque()
    private val recordedRequests: MutableList<NetworkRequest> = mutableListOf()
    private var closed: Boolean = false

    public val requests: List<NetworkRequest>
        get() = recordedRequests.toList()

    public fun enqueue(response: NetworkResponse): FakeTransport = apply {
        scriptedResults.addLast(ScriptedResult.Response(response))
    }

    public fun enqueue(
        status: HttpStatus,
        headers: Headers = Headers.Empty,
        body: ByteArray = byteArrayOf(),
    ): FakeTransport = enqueue(testResponse(status = status, headers = headers, body = body))

    public fun enqueueFailure(error: NetworkError): FakeTransport = apply {
        scriptedResults.addLast(ScriptedResult.Failure(NetworkErrorException(error)))
    }

    public fun enqueueFailure(throwable: Throwable): FakeTransport = apply {
        scriptedResults.addLast(ScriptedResult.Failure(throwable))
    }

    public fun clearRequests(): FakeTransport = apply {
        recordedRequests.clear()
    }

    override suspend fun execute(request: NetworkRequest): NetworkResponse {
        if (closed) {
            throw IllegalStateException("FakeTransport is closed")
        }
        recordedRequests += request
        val next = if (scriptedResults.isEmpty()) {
            ScriptedResult.Response(defaultResponse)
        } else {
            scriptedResults.removeFirst()
        }
        return when (next) {
            is ScriptedResult.Response -> next.response
            is ScriptedResult.Failure -> throw next.throwable
        }
    }

    override fun close() {
        closed = true
    }

    private sealed interface ScriptedResult {
        data class Response(val response: NetworkResponse) : ScriptedResult
        data class Failure(val throwable: Throwable) : ScriptedResult
    }
}

@ExperimentalCaterktor
public fun testResponse(
    status: HttpStatus = HttpStatus.OK,
    headers: Headers = Headers.Empty,
    body: ByteArray = byteArrayOf(),
): NetworkResponse = NetworkResponse(status = status, headers = headers, body = body)

@ExperimentalCaterktor
public fun jsonResponse(
    json: String,
    status: HttpStatus = HttpStatus.OK,
    headers: Headers = Headers.Empty,
): NetworkResponse =
    testResponse(
        status = status,
        headers = headers + Headers { set("Content-Type", "application/json; charset=UTF-8") },
        body = json.encodeToByteArray(),
    )

@ExperimentalCaterktor
public fun httpFailure(
    status: HttpStatus,
    headers: Headers = Headers.Empty,
    body: ByteArray = byteArrayOf(),
    cause: Throwable? = null,
): NetworkError.Http =
    NetworkError.Http(
        status = status,
        headers = headers,
        body = ErrorBody(raw = RawBody(body, headers["Content-Type"]), parsed = null),
        cause = cause,
    )
