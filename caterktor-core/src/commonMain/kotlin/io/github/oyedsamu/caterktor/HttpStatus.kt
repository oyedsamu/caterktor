package io.github.oyedsamu.caterktor

/**
 * A typesafe wrapper for an HTTP status code. Does not enforce validity — any integer is accepted.
 *
 * Named constants for common status codes are available on the companion object, e.g.
 * [HttpStatus.OK], [HttpStatus.NotFound].
 *
 * `@kotlin.jvm.JvmInline` is required here even in `commonMain` because the Android Kotlin
 * compiler does not yet support bare `value class` without it. On non-JVM KMP targets the
 * annotation is silently ignored. Kotlin 2.x no longer emits the historical
 * `OPTIONAL_DECLARATION_USAGE_IN_NON_COMMON_SOURCE` warning for this pattern.
 */
@kotlin.jvm.JvmInline
public value class HttpStatus(
    /** The raw integer status code, e.g. `200`, `404`. */
    public val code: Int,
) {

    /** `true` if this is an informational response (100–199). */
    public val isInformational: Boolean
        get() = code in 100..199

    /** `true` if this is a successful response (200–299). */
    public val isSuccess: Boolean
        get() = code in 200..299

    /** `true` if this is a redirection response (300–399). */
    public val isRedirect: Boolean
        get() = code in 300..399

    /** `true` if this is a client error response (400–499). */
    public val isClientError: Boolean
        get() = code in 400..499

    /** `true` if this is a server error response (500–599). */
    public val isServerError: Boolean
        get() = code in 500..599

    /** Returns the status code as a string, e.g. `"200"`. */
    override fun toString(): String = code.toString()

    public companion object {
        /** 200 OK */
        public val OK: HttpStatus = HttpStatus(200)

        /** 201 Created */
        public val Created: HttpStatus = HttpStatus(201)

        /** 202 Accepted */
        public val Accepted: HttpStatus = HttpStatus(202)

        /** 204 No Content */
        public val NoContent: HttpStatus = HttpStatus(204)

        /** 301 Moved Permanently */
        public val MovedPermanently: HttpStatus = HttpStatus(301)

        /** 302 Found */
        public val Found: HttpStatus = HttpStatus(302)

        /** 304 Not Modified */
        public val NotModified: HttpStatus = HttpStatus(304)

        /** 400 Bad Request */
        public val BadRequest: HttpStatus = HttpStatus(400)

        /** 401 Unauthorized */
        public val Unauthorized: HttpStatus = HttpStatus(401)

        /** 403 Forbidden */
        public val Forbidden: HttpStatus = HttpStatus(403)

        /** 404 Not Found */
        public val NotFound: HttpStatus = HttpStatus(404)

        /** 405 Method Not Allowed */
        public val MethodNotAllowed: HttpStatus = HttpStatus(405)

        /** 409 Conflict */
        public val Conflict: HttpStatus = HttpStatus(409)

        /** 410 Gone */
        public val Gone: HttpStatus = HttpStatus(410)

        /** 422 Unprocessable Entity */
        public val UnprocessableEntity: HttpStatus = HttpStatus(422)

        /** 429 Too Many Requests */
        public val TooManyRequests: HttpStatus = HttpStatus(429)

        /** 500 Internal Server Error */
        public val InternalServerError: HttpStatus = HttpStatus(500)

        /** 502 Bad Gateway */
        public val BadGateway: HttpStatus = HttpStatus(502)

        /** 503 Service Unavailable */
        public val ServiceUnavailable: HttpStatus = HttpStatus(503)

        /** 504 Gateway Timeout */
        public val GatewayTimeout: HttpStatus = HttpStatus(504)
    }
}
