package io.github.oyedsamu.caterktor.testing

import io.github.oyedsamu.caterktor.CloseableTransport
import io.github.oyedsamu.caterktor.ErrorBody
import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import io.github.oyedsamu.caterktor.Headers
import io.github.oyedsamu.caterktor.HttpMethod
import io.github.oyedsamu.caterktor.HttpStatus
import io.github.oyedsamu.caterktor.NetworkError
import io.github.oyedsamu.caterktor.NetworkErrorException
import io.github.oyedsamu.caterktor.NetworkRequest
import io.github.oyedsamu.caterktor.NetworkResponse
import io.github.oyedsamu.caterktor.RawBody
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    private val mutex = Mutex()
    private val scriptedResults: ArrayDeque<ScriptedResult> = ArrayDeque()
    private val rules: MutableList<FakeRule> = mutableListOf()
    private val recordedRequests: MutableList<NetworkRequest> = mutableListOf()
    private var failOnUnmatchedRules: Boolean = false
    private var closed: Boolean = false

    public constructor(configure: FakeTransportDsl.() -> Unit) : this() {
        rules(configure)
        failOnUnmatchedRequests()
    }

    public val requests: List<NetworkRequest>
        get() = recordedRequests.toList()

    public fun rules(configure: FakeTransportDsl.() -> Unit): FakeTransport =
        rules(configure = configure, failOnUnmatchedRequests = true)

    public fun rule(
        method: HttpMethod,
        pathTemplate: String,
        response: NetworkResponse,
    ): FakeTransport = addRule(
        method = method,
        pathTemplate = pathTemplate,
        result = ScriptedResult.Response(response),
        failOnUnmatchedRequests = true,
    )

    public fun rule(
        method: HttpMethod,
        pathTemplate: String,
        handler: (FakeRequest) -> NetworkResponse,
    ): FakeTransport = addRule(
        method = method,
        pathTemplate = pathTemplate,
        result = ScriptedResult.Handler(handler),
        failOnUnmatchedRequests = true,
    )

    public fun ruleFailure(
        method: HttpMethod,
        pathTemplate: String,
        error: NetworkError,
    ): FakeTransport = ruleFailure(method, pathTemplate, NetworkErrorException(error))

    public fun ruleFailure(
        method: HttpMethod,
        pathTemplate: String,
        throwable: Throwable,
    ): FakeTransport = addRule(
        method = method,
        pathTemplate = pathTemplate,
        result = ScriptedResult.Failure(throwable),
        failOnUnmatchedRequests = true,
    )

    internal fun rules(
        configure: FakeTransportDsl.() -> Unit,
        failOnUnmatchedRequests: Boolean,
    ): FakeTransport = apply {
        FakeTransportDsl(this, failOnUnmatchedRequests).configure()
    }

    internal fun addRule(
        method: HttpMethod,
        pathTemplate: String,
        result: ScriptedResult,
        failOnUnmatchedRequests: Boolean,
    ): FakeTransport = apply {
        rules += FakeRule(
            method = method,
            pathTemplate = PathTemplate.parse(pathTemplate),
            result = result,
        )
        failOnUnmatchedRules = failOnUnmatchedRules || failOnUnmatchedRequests
    }

    public fun failOnUnmatchedRequests(): FakeTransport = apply {
        failOnUnmatchedRules = true
    }

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
        val (scripted, pathParams) = mutex.withLock {
            recordedRequests.add(request)
            rules.firstNotNullOfOrNull { it.match(request) }
                ?: if (scriptedResults.isEmpty()) {
                    if (failOnUnmatchedRules) {
                        throw AssertionError(unmatchedRequestMessage(request, rules))
                    }
                    ScriptedResult.Response(defaultResponse) to emptyMap()
                } else {
                    scriptedResults.removeFirst() to emptyMap()
                }
        }
        return when (scripted) {
            is ScriptedResult.Response -> scripted.response
            is ScriptedResult.Failure -> throw scripted.throwable
            is ScriptedResult.Handler -> scripted.handler(FakeRequest(request, pathParams))
        }
    }

    override fun close() {
        closed = true
    }

    internal sealed interface ScriptedResult {
        data class Response(val response: NetworkResponse) : ScriptedResult
        data class Failure(val throwable: Throwable) : ScriptedResult
        data class Handler(val handler: (FakeRequest) -> NetworkResponse) : ScriptedResult
    }
}

@ExperimentalCaterktor
public class FakeTransportDsl internal constructor(
    private val transport: FakeTransport,
    private val failOnUnmatchedRequests: Boolean = true,
) {
    public fun respond(
        method: HttpMethod,
        pathTemplate: String,
        response: NetworkResponse,
    ): FakeTransportDsl = apply {
        transport.addRule(
            method = method,
            pathTemplate = pathTemplate,
            result = FakeTransport.ScriptedResult.Response(response),
            failOnUnmatchedRequests = failOnUnmatchedRequests,
        )
    }

    public fun respond(
        method: HttpMethod,
        pathTemplate: String,
        handler: (FakeRequest) -> NetworkResponse,
    ): FakeTransportDsl = apply {
        transport.addRule(
            method = method,
            pathTemplate = pathTemplate,
            result = FakeTransport.ScriptedResult.Handler(handler),
            failOnUnmatchedRequests = failOnUnmatchedRequests,
        )
    }

    public fun fail(
        method: HttpMethod,
        pathTemplate: String,
        error: NetworkError,
    ): FakeTransportDsl = apply {
        transport.addRule(
            method = method,
            pathTemplate = pathTemplate,
            result = FakeTransport.ScriptedResult.Failure(NetworkErrorException(error)),
            failOnUnmatchedRequests = failOnUnmatchedRequests,
        )
    }

    public fun fail(
        method: HttpMethod,
        pathTemplate: String,
        throwable: Throwable,
    ): FakeTransportDsl = apply {
        transport.addRule(
            method = method,
            pathTemplate = pathTemplate,
            result = FakeTransport.ScriptedResult.Failure(throwable),
            failOnUnmatchedRequests = failOnUnmatchedRequests,
        )
    }

    public fun get(pathTemplate: String, response: NetworkResponse): FakeTransportDsl =
        respond(HttpMethod.GET, pathTemplate, response)

    public fun get(pathTemplate: String, handler: (FakeRequest) -> NetworkResponse): FakeTransportDsl =
        respond(HttpMethod.GET, pathTemplate, handler)

    public fun head(pathTemplate: String, response: NetworkResponse): FakeTransportDsl =
        respond(HttpMethod.HEAD, pathTemplate, response)

    public fun head(pathTemplate: String, handler: (FakeRequest) -> NetworkResponse): FakeTransportDsl =
        respond(HttpMethod.HEAD, pathTemplate, handler)

    public fun post(pathTemplate: String, response: NetworkResponse): FakeTransportDsl =
        respond(HttpMethod.POST, pathTemplate, response)

    public fun post(pathTemplate: String, handler: (FakeRequest) -> NetworkResponse): FakeTransportDsl =
        respond(HttpMethod.POST, pathTemplate, handler)

    public fun put(pathTemplate: String, response: NetworkResponse): FakeTransportDsl =
        respond(HttpMethod.PUT, pathTemplate, response)

    public fun put(pathTemplate: String, handler: (FakeRequest) -> NetworkResponse): FakeTransportDsl =
        respond(HttpMethod.PUT, pathTemplate, handler)

    public fun patch(pathTemplate: String, response: NetworkResponse): FakeTransportDsl =
        respond(HttpMethod.PATCH, pathTemplate, response)

    public fun patch(pathTemplate: String, handler: (FakeRequest) -> NetworkResponse): FakeTransportDsl =
        respond(HttpMethod.PATCH, pathTemplate, handler)

    public fun delete(pathTemplate: String, response: NetworkResponse): FakeTransportDsl =
        respond(HttpMethod.DELETE, pathTemplate, response)

    public fun delete(pathTemplate: String, handler: (FakeRequest) -> NetworkResponse): FakeTransportDsl =
        respond(HttpMethod.DELETE, pathTemplate, handler)

    public fun options(pathTemplate: String, response: NetworkResponse): FakeTransportDsl =
        respond(HttpMethod.OPTIONS, pathTemplate, response)

    public fun options(pathTemplate: String, handler: (FakeRequest) -> NetworkResponse): FakeTransportDsl =
        respond(HttpMethod.OPTIONS, pathTemplate, handler)
}

@ExperimentalCaterktor
public class FakeRequest(
    public val request: NetworkRequest,
    public val pathParameters: Map<String, String>,
)

@OptIn(ExperimentalCaterktor::class)
private data class FakeRule(
    val method: HttpMethod,
    val pathTemplate: PathTemplate,
    val result: FakeTransport.ScriptedResult,
) {
    fun match(request: NetworkRequest): Pair<FakeTransport.ScriptedResult, Map<String, String>>? {
        if (request.method != method) return null
        val pathParameters = pathTemplate.match(request.url.pathOnly()) ?: return null
        return result to pathParameters
    }

    fun describes(): String = "${method.name} ${pathTemplate.normalized}"
}

private class PathTemplate private constructor(
    val normalized: String,
    private val segments: List<Segment>,
) {
    fun match(path: String): Map<String, String>? {
        val pathSegments = path.toPathSegments()
        if (segments.size != pathSegments.size) return null

        val parameters = mutableMapOf<String, String>()
        segments.zip(pathSegments).forEach { (templateSegment, pathSegment) ->
            when (templateSegment) {
                is Segment.Exact -> if (templateSegment.value != pathSegment) return null
                is Segment.Parameter -> {
                    if (pathSegment.isEmpty()) return null
                    parameters[templateSegment.name] = pathSegment
                }
            }
        }
        return parameters
    }

    companion object {
        fun parse(pathTemplate: String): PathTemplate {
            val normalized = pathTemplate.pathOnly()
            return PathTemplate(
                normalized = normalized,
                segments = normalized.toPathSegments().map { segment ->
                    if (segment.length > 2 && segment.startsWith('{') && segment.endsWith('}')) {
                        Segment.Parameter(segment.substring(1, segment.length - 1))
                    } else {
                        Segment.Exact(segment)
                    }
                },
            )
        }
    }

    private sealed interface Segment {
        data class Exact(val value: String) : Segment
        data class Parameter(val name: String) : Segment
    }
}

@OptIn(ExperimentalCaterktor::class)
private fun unmatchedRequestMessage(request: NetworkRequest, rules: List<FakeRule>): String {
    val path = request.url.pathOnly()
    val pathMatches = rules.filter { it.pathTemplate.match(path) != null }
    val methodMatches = rules.filter { it.method == request.method }
    return buildString {
        append("No fake rule matched ${request.method.name} $path.")
        append("\nRequest URL: ${request.url}")
        if (pathMatches.isNotEmpty()) {
            append("\nPath matched, but method did not. Allowed methods: ")
            append(pathMatches.joinToString { it.method.name })
        }
        if (methodMatches.isNotEmpty()) {
            append("\nMethod matched, but path did not. Candidate paths: ")
            append(methodMatches.joinToString { it.pathTemplate.normalized })
        }
        append("\nRegistered rules:")
        rules.forEach { rule ->
            append("\n - ${rule.describes()}")
        }
        append("\nUse enqueue(...) for queue-based fallback or add a matching fake rule.")
    }
}

private fun String.pathOnly(): String {
    val withoutQueryOrFragment = substringBefore('#').substringBefore('?')
    val schemeIndex = withoutQueryOrFragment.indexOf("://")
    val rawPath = if (schemeIndex >= 0) {
        val pathStart = withoutQueryOrFragment.indexOf('/', startIndex = schemeIndex + 3)
        if (pathStart >= 0) withoutQueryOrFragment.substring(pathStart) else "/"
    } else {
        withoutQueryOrFragment
    }
    val path = if (rawPath.startsWith('/')) rawPath else "/$rawPath"
    return path.ifEmpty { "/" }
}

private fun String.toPathSegments(): List<String> =
    if (this == "/") emptyList() else removePrefix("/").split('/')

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
