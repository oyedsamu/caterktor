package io.github.oyedsamu.caterktor

/**
 * The HTTP request method (verb), as defined by RFC 9110.
 */
public enum class HttpMethod {
    GET,
    HEAD,
    POST,
    PUT,
    PATCH,
    DELETE,
    OPTIONS;

    /**
     * `true` if the method is *safe* — it has no observable side effects on the server,
     * per RFC 9110 §9.2.1. Safe methods: [GET], [HEAD], [OPTIONS].
     */
    public val isSafe: Boolean
        get() = this == GET || this == HEAD || this == OPTIONS

    /**
     * `true` if the method is *idempotent* — repeated identical requests produce the
     * same server state as a single request, per RFC 9110 §9.2.2.
     * Idempotent methods: all [isSafe] methods plus [PUT] and [DELETE].
     */
    public val isIdempotent: Boolean
        get() = isSafe || this == PUT || this == DELETE
}
