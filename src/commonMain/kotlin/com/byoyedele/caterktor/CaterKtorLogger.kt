package com.byoyedele.caterktor

import io.ktor.client.statement.HttpResponse

interface CaterKtorLogger {
    val isEnabled: Boolean

    fun log(message: String)

    object None : CaterKtorLogger {
        override val isEnabled: Boolean = false

        override fun log(message: String) = Unit
    }
}

fun interface CaterKtorLogWriter {
    fun log(message: String)
}

fun caterKtorLogger(writer: CaterKtorLogWriter): CaterKtorLogger =
    object : CaterKtorLogger {
        override val isEnabled: Boolean = true

        override fun log(message: String) {
            writer.log(message)
        }
    }

internal fun NetworkRequest.toLogMessage(): String = buildString {
    append("CaterKtor -> ")
    append(method.name.uppercase())
    append(' ')
    append(path.withQueryParameters(queryParameters))
    if (headers.isNotEmpty()) {
        append(" headers=")
        append(headers.sanitized())
    }
}

internal fun HttpResponse.toLogMessage(request: NetworkRequest): String = buildString {
    append("CaterKtor <- ")
    append(status.value)
    append(' ')
    append(status.description)
    append(" for ")
    append(request.method.name.uppercase())
    append(' ')
    append(request.path)
}

internal fun NetworkRequest.toFailureLogMessage(throwable: Throwable): String = buildString {
    append("CaterKtor !! ")
    append(method.name.uppercase())
    append(' ')
    append(path)
    append(" failed with ")
    append(throwable::class.simpleName ?: "Throwable")
    throwable.message
        ?.takeIf { it.isNotBlank() }
        ?.let {
            append(": ")
            append(it)
        }
}

private fun String.withQueryParameters(queryParameters: Map<String, List<String>>): String {
    if (queryParameters.isEmpty()) return this
    val queryString = queryParameters
        .flatMap { (name, values) -> values.map { value -> "$name=$value" } }
        .joinToString(separator = "&")
    return "$this?$queryString"
}

private fun Map<String, String>.sanitized(): Map<String, String> =
    mapValues { (name, value) ->
        if (name.isSensitiveHeader()) RedactedHeaderValue else value
    }

private fun String.isSensitiveHeader(): Boolean =
    equals("Authorization", ignoreCase = true) ||
        equals("Cookie", ignoreCase = true) ||
        equals("Set-Cookie", ignoreCase = true) ||
        contains("Token", ignoreCase = true) ||
        contains("Secret", ignoreCase = true) ||
        contains("Api-Key", ignoreCase = true)

private const val RedactedHeaderValue = "<redacted>"
