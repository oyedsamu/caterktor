package io.github.oyedsamu.caterktor.logging

import io.github.oyedsamu.caterktor.Chain
import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import io.github.oyedsamu.caterktor.Interceptor
import io.github.oyedsamu.caterktor.NetworkRequest
import io.github.oyedsamu.caterktor.NetworkResponse
import io.github.oyedsamu.caterktor.RequestBody
import io.github.oyedsamu.caterktor.ResponseBody

/**
 * Logging detail level for [LoggerInterceptor].
 *
 * Levels are ordered by verbosity; each level includes everything from the
 * levels above it.
 */
@ExperimentalCaterktor
public enum class LogLevel {
    /** No logging. */
    None,

    /**
     * One line per request: method, URL, status code, and duration.
     *
     * Example: `GET https://api.example.com/users | 200 OK (124 ms)`
     */
    Basic,

    /**
     * [Basic] plus all request and response headers, with values redacted for
     * `Authorization` and `Cookie` by default. Sensitive header names follow
     * the set defined on [LoggerInterceptor.sensitiveHeaders].
     */
    Headers,

    /**
     * [Headers] plus the full request and response body decoded as UTF-8.
     * Binary bodies are rendered as `<binary N bytes>`. Use with care in
     * production; bodies may contain PII.
     */
    Body,
}

/**
 * A [Chain]-level interceptor that logs outgoing requests and incoming
 * responses using a caller-supplied [logger] function.
 *
 * [LoggerInterceptor] operates on [NetworkRequest] and [NetworkResponse]. It
 * runs inside the interceptor pipeline, before and after the transport. It does
 * not see the decoded body or the [io.github.oyedsamu.caterktor.NetworkResult];
 * for result-level observation use
 * [io.github.oyedsamu.caterktor.NetworkClient.events] instead.
 *
 * ## Ordering
 *
 * Register [LoggerInterceptor] last in the pipeline, after auth and retry
 * interceptors, so the logged request reflects all rewrites and the logged
 * response is the final one after any retry.
 *
 * ## Sensitive headers
 *
 * At [LogLevel.Headers] and above, header values whose name matches
 * [sensitiveHeaders] (case-insensitive) are replaced with `***`. Extend the
 * default set if your API uses non-standard auth headers.
 *
 * ## Example
 *
 * ```kotlin
 * val client = CaterKtor {
 *     transport = OkHttpTransport()
 *     addInterceptor(LoggerInterceptor(level = LogLevel.Headers) { line ->
 *         println(line)
 *     })
 * }
 * ```
 */
@ExperimentalCaterktor
public class LoggerInterceptor private constructor(
    /** The level of detail to log. */
    public val level: LogLevel = LogLevel.Basic,
    /**
     * Header names whose values are redacted at [LogLevel.Headers] and above.
     * Comparison is case-insensitive.
     */
    public val sensitiveHeaders: Set<String> = DefaultSensitiveHeaders,
    /** Redaction engine applied to URLs, headers, and bodies before logging. */
    public val redaction: RedactionEngine = RedactionEngine(headerNames = sensitiveHeaders),
    /** Receives each log line. Supply `println`, Napier, Timber, etc. */
    public val logger: (String) -> Unit,
) : Interceptor {

    /**
     * Create a logger with header-name redaction.
     */
    public constructor(
        level: LogLevel = LogLevel.Basic,
        sensitiveHeaders: Set<String> = DefaultSensitiveHeaders,
        logger: (String) -> Unit,
    ) : this(
        level = level,
        sensitiveHeaders = sensitiveHeaders,
        redaction = RedactionEngine(headerNames = sensitiveHeaders),
        logger = logger,
    )

    /**
     * Create a logger with a custom [RedactionEngine].
     */
    public constructor(
        level: LogLevel = LogLevel.Basic,
        redaction: RedactionEngine,
        logger: (String) -> Unit,
    ) : this(
        level = level,
        sensitiveHeaders = redaction.headerNames,
        redaction = redaction,
        logger = logger,
    )

    override suspend fun intercept(chain: Chain): NetworkResponse {
        if (level == LogLevel.None) return chain.proceed(chain.request)

        val request = chain.request
        logRequest(request)

        val startMark = kotlin.time.TimeSource.Monotonic.markNow()
        val response = chain.proceed(request)
        val durationMs = startMark.elapsedNow().inWholeMilliseconds

        logResponse(response, durationMs)
        return response
    }

    private fun logRequest(request: NetworkRequest) {
        logger("-> ${request.method.name} ${redaction.redactUrl(request.url)}")
        if (level >= LogLevel.Headers) {
            for (name in request.headers.names) {
                val value = redaction.redactHeader(name, request.headers.getAll(name).joinToString(", "))
                logger("  $name: $value")
            }
        }
        if (level >= LogLevel.Body) {
            request.body?.let { body ->
                when (body) {
                    is RequestBody.Bytes -> logger("  Body (${body.contentType}): ${redaction.redactBody(body.contentType, body.bytes)}")
                    is RequestBody.Text -> logger("  Body (${body.contentType}): ${redaction.redactTextBody(body.contentType, body.text)}")
                    is RequestBody.Source -> logger("  Body (${body.contentType}): ${body.describeStreamingBody()}")
                }
            }
        }
    }

    private fun logResponse(response: NetworkResponse, durationMs: Long) {
        logger("<- ${response.status.code} (${durationMs} ms)")
        if (level >= LogLevel.Headers) {
            for (name in response.headers.names) {
                val value = redaction.redactHeader(name, response.headers.getAll(name).joinToString(", "))
                logger("  $name: $value")
            }
        }
        if (level >= LogLevel.Body) {
            when (val body = response.body) {
                is ResponseBody.Bytes -> {
                    val bytes = body.bytes
                    if (bytes.isNotEmpty()) {
                        logger("  Body: ${redaction.redactBody(response.headers["Content-Type"], bytes)}")
                    }
                }
                is ResponseBody.Source -> {
                    if (body.contentLength != 0L) {
                        logger("  Body: ${body.describeStreamingBody()}")
                    }
                }
            }
        }
    }

    public companion object {
        /**
         * Default set of header names whose values are redacted at
         * [LogLevel.Headers] and above.
         */
        public val DefaultSensitiveHeaders: Set<String> = RedactionEngine.DefaultHeaderNames
    }
}

private fun RequestBody.Source.describeStreamingBody(): String =
    "<streaming ${contentLength?.toString() ?: "unknown"} bytes>"

private fun ResponseBody.Source.describeStreamingBody(): String =
    "<streaming ${contentLength?.toString() ?: "unknown"} bytes>"
