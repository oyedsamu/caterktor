package io.github.oyedsamu.caterktor

/**
 * Resolves a request URL from an optional base URL and a path.
 *
 * Resolution rules (R1 + R2):
 * - If [path] has a scheme (`http://` or `https://`), return it unchanged.
 * - If [path] starts with `/` and [baseUrl] is provided, prepend the origin
 *   (scheme + authority) of [baseUrl].
 * - Otherwise combine: `baseUrl.trimEnd('/') + "/" + path.trimStart('/')`.
 * - If [baseUrl] is null and [path] is relative, throws [IllegalArgumentException].
 */
@PublishedApi
internal fun resolveUrl(baseUrl: String?, path: String): String {
    if (path.startsWith("http://") || path.startsWith("https://")) return path
    requireNotNull(baseUrl) {
        "CaterKtor: relative URL '$path' cannot be resolved without a baseUrl. " +
            "Configure one via CaterKtorBuilder.baseUrl."
    }
    return if (path.startsWith("/")) {
        val origin = extractOrigin(baseUrl)
        origin + path
    } else {
        baseUrl.trimEnd('/') + "/" + path.trimStart('/')
    }
}

/**
 * Expands a path template by replacing `{paramName}` placeholders with
 * URL-path-encoded values from [params] (R3).
 *
 * Example: `expandPathTemplate("users/{id}/posts/{postId}", mapOf("id" to "42", "postId" to "7"))`
 * returns `"users/42/posts/7"`.
 *
 * Values are percent-encoded for use in a URL path segment. Only characters
 * outside the unreserved set (letters, digits, `-`, `.`, `_`, `~`) are encoded.
 *
 * Placeholders with no matching key in [params] are left as-is.
 */
@PublishedApi
internal fun expandPathTemplate(template: String, params: Map<String, Any>): String {
    if (params.isEmpty()) return template
    return buildString(template.length) {
        var i = 0
        while (i < template.length) {
            if (template[i] == '{') {
                val end = template.indexOf('}', i)
                if (end == -1) {
                    // Unclosed brace — treat as literal
                    append(template[i])
                    i++
                } else {
                    val key = template.substring(i + 1, end)
                    val value = params[key]
                    if (value != null) {
                        append(percentEncodePathSegment(value.toString()))
                    } else {
                        append(template, i, end + 1)
                    }
                    i = end + 1
                }
            } else {
                append(template[i])
                i++
            }
        }
    }
}

/** Extracts the scheme + authority (origin) from a URL. */
private fun extractOrigin(url: String): String {
    val schemeEnd = url.indexOf("://")
    if (schemeEnd == -1) return url
    val pathStart = url.indexOf('/', schemeEnd + 3)
    return if (pathStart == -1) url else url.substring(0, pathStart)
}

/** Percent-encodes a single path segment value. Unreserved chars are kept as-is. */
private fun percentEncodePathSegment(value: String): String = buildString {
    for (char in value) {
        if (char.isUnreserved()) {
            append(char)
        } else {
            val bytes = char.toString().encodeToByteArray()
            for (byte in bytes) {
                append('%')
                append(byte.toUByte().toString(16).padStart(2, '0').uppercase())
            }
        }
    }
}

private fun Char.isUnreserved(): Boolean =
    this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' ||
        this == '-' || this == '.' || this == '_' || this == '~'
