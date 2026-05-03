package io.github.oyedsamu.caterktor.logging

import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import io.github.oyedsamu.caterktor.RequestBody
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * A regex replacement applied by [RedactionEngine].
 */
@ExperimentalCaterktor
public data class RegexRedactionRule(
    public val pattern: Regex,
    public val replacement: String = RedactionEngine.DefaultReplacement,
)

/**
 * Redacts sensitive values before they are written by [LoggerInterceptor].
 *
 * The engine covers the common leak paths for HTTP wire logs: headers, query
 * parameters, JSON body fields, caller-supplied regex rules, binary body
 * suppression, and maximum body size.
 *
 * JSON selectors support a small JSONPath-lite subset:
 *
 * - `password` or `$..password`: redact any field with that name.
 * - `$.token`: redact a root field.
 * - `$.user.password`: redact a nested field.
 * - `*` can be used as a path-segment wildcard.
 */
@ExperimentalCaterktor
public class RedactionEngine(
    public val headerNames: Set<String> = DefaultHeaderNames,
    public val queryParameterNames: Set<String> = DefaultQueryParameterNames,
    public val jsonBodyFields: Set<String> = DefaultJsonBodyFields,
    public val regexRules: List<RegexRedactionRule> = emptyList(),
    public val replacement: String = DefaultReplacement,
    public val maxBodyBytes: Int = 16 * 1024,
    public val suppressBinaryBodies: Boolean = true,
) {

    private val headerNamesLower: Set<String> = headerNames.mapTo(mutableSetOf()) { it.lowercase() }
    private val queryParameterNamesLower: Set<String> = queryParameterNames.mapTo(mutableSetOf()) { it.lowercase() }
    private val jsonSelectors: List<JsonSelector> = jsonBodyFields.mapNotNull { JsonSelector.parse(it) }

    init {
        require(maxBodyBytes >= 0) { "maxBodyBytes must be >= 0, was $maxBodyBytes" }
    }

    /**
     * Redact [value] when [name] is configured as sensitive.
     */
    public fun redactHeader(name: String, value: String): String =
        if (name.lowercase() in headerNamesLower) replacement else applyRegexRules(value)

    /**
     * Redact configured query parameter values in [url].
     */
    public fun redactUrl(url: String): String {
        val question = url.indexOf('?')
        if (question < 0) return applyRegexRules(url)

        val fragment = url.indexOf('#', startIndex = question + 1)
        val queryEnd = if (fragment >= 0) fragment else url.length
        val prefix = url.substring(0, question + 1)
        val query = url.substring(question + 1, queryEnd)
        val suffix = if (fragment >= 0) url.substring(fragment) else ""
        if (query.isEmpty()) return applyRegexRules(url)

        val redactedQuery = query.split('&').joinToString("&") { parameter ->
            val equals = parameter.indexOf('=')
            val name = if (equals >= 0) parameter.substring(0, equals) else parameter
            if (name.lowercase() in queryParameterNamesLower) {
                "$name=$replacement"
            } else {
                parameter
            }
        }

        return applyRegexRules(prefix + redactedQuery + suffix)
    }

    /**
     * Redact sensitive field values in a form body for logging.
     *
     * Field names matching [queryParameterNames] (case-insensitive) have their
     * values replaced with [replacement]. The result is human-readable plain
     * text, not URL-encoded wire format.
     */
    @OptIn(ExperimentalCaterktor::class)
    public fun redactFormBody(body: RequestBody.Form): String {
        val redacted = body.fields.joinToString("&") { field ->
            if (field.name.lowercase() in queryParameterNamesLower) {
                "${field.name}=$replacement"
            } else {
                "${field.name}=${field.value}"
            }
        }
        return applyRegexRules(redacted)
    }

    /**
     * Render and redact a byte body for logging.
     */
    public fun redactBody(contentType: String?, bytes: ByteArray): String {
        if (bytes.size > maxBodyBytes) {
            return "<body ${bytes.size} bytes exceeds max $maxBodyBytes bytes>"
        }
        if (suppressBinaryBodies && !bytes.isPrintableText()) {
            return "<binary ${bytes.size} bytes>"
        }
        return redactTextBody(contentType, bytes.decodeToString())
    }

    /**
     * Redact a text body for logging.
     */
    public fun redactTextBody(contentType: String?, text: String): String {
        val byteSize = text.encodeToByteArray().size
        if (byteSize > maxBodyBytes) {
            return "<body $byteSize bytes exceeds max $maxBodyBytes bytes>"
        }

        val jsonRedacted = if (looksLikeJson(contentType, text)) {
            redactJson(text)
        } else {
            text
        }
        return applyRegexRules(jsonRedacted)
    }

    private fun redactJson(text: String): String =
        try {
            Json.parseToJsonElement(text)
                .redactJsonElement(emptyList())
                .toString()
        } catch (_: IllegalArgumentException) {
            text
        }

    private fun JsonElement.redactJsonElement(path: List<String>): JsonElement =
        when (this) {
            is JsonObject -> buildJsonObject {
                for ((key, value) in this@redactJsonElement) {
                    val childPath = path + key
                    val redactedValue = if (jsonSelectors.any { it.matches(childPath) }) {
                        JsonPrimitive(replacement)
                    } else {
                        value.redactJsonElement(childPath)
                    }
                    put(key, redactedValue)
                }
            }
            is JsonArray -> JsonArray(map { it.redactJsonElement(path) })
            else -> this
        }

    private fun applyRegexRules(value: String): String =
        regexRules.fold(value) { redacted, rule ->
            rule.pattern.replace(redacted, rule.replacement)
        }

    public companion object {
        public const val DefaultReplacement: String = "***"

        public val DefaultHeaderNames: Set<String> = setOf(
            "Authorization",
            "Cookie",
            "Set-Cookie",
            "Proxy-Authorization",
            "X-Auth-Token",
            "X-Api-Key",
        )

        public val DefaultQueryParameterNames: Set<String> = setOf(
            "access_token",
            "api_key",
            "auth",
            "authorization",
            "client_secret",
            "key",
            "password",
            "refresh_token",
            "secret",
            "signature",
            "sig",
            "token",
        )

        public val DefaultJsonBodyFields: Set<String> = setOf(
            "access_token",
            "apiKey",
            "api_key",
            "authorization",
            "client_secret",
            "cookie",
            "id_token",
            "password",
            "refresh_token",
            "secret",
            "session",
            "token",
        )
    }
}

private fun ByteArray.isPrintableText(): Boolean =
    all { it in 0x20..0x7E || it == 0x0A.toByte() || it == 0x0D.toByte() || it == 0x09.toByte() }

private fun looksLikeJson(contentType: String?, text: String): Boolean {
    val mediaType = contentType?.substringBefore(';')?.trim()?.lowercase()
    if (mediaType != null && (mediaType == "application/json" || mediaType.endsWith("+json"))) return true
    val trimmed = text.trimStart()
    return trimmed.startsWith('{') || trimmed.startsWith('[')
}

private data class JsonSelector(
    val mode: Mode,
    val segments: List<String>,
) {
    fun matches(path: List<String>): Boolean =
        when (mode) {
            Mode.Anywhere -> path.lastOrNull()?.equals(segments.single(), ignoreCase = true) == true
            Mode.Absolute -> path.matchesSegments(segments)
        }

    enum class Mode {
        Anywhere,
        Absolute,
    }

    companion object {
        fun parse(selector: String): JsonSelector? {
            val trimmed = selector.trim()
            if (trimmed.isEmpty()) return null
            if (!trimmed.startsWith("$")) {
                return JsonSelector(Mode.Anywhere, listOf(trimmed))
            }
            if (trimmed.startsWith("$..")) {
                val name = trimmed.removePrefix("$..").takeIf { it.isNotBlank() } ?: return null
                return JsonSelector(Mode.Anywhere, listOf(name))
            }
            if (trimmed.startsWith("$.")) {
                val segments = trimmed.removePrefix("$.")
                    .split('.')
                    .filter { it.isNotBlank() }
                return if (segments.isEmpty()) null else JsonSelector(Mode.Absolute, segments)
            }
            return null
        }
    }
}

private fun List<String>.matchesSegments(segments: List<String>): Boolean {
    if (size != segments.size) return false
    return zip(segments).all { (actual, expected) ->
        expected == "*" || actual.equals(expected, ignoreCase = true)
    }
}
