package com.byoyedele.caterktor

import io.ktor.http.HttpStatusCode

data class NetworkException(
    val httpCode: Int,
    val errors: List<ApiError>,
) : RuntimeException() {

    override val message: String
        get() = errorDetail
            ?.takeIf { it.isNotBlank() }
            ?: buildDefaultMessage()

    val errorCode: String?
        get() = errors.firstOrNull()?.code

    val errorDetail: String?
        get() = errors.firstOrNull()?.detail

    fun hasHttpCode(httpStatusCode: HttpStatusCode): Boolean =
        HttpStatusCode.fromValue(httpCode) == httpStatusCode

    fun hasError(errorCode: String): Boolean =
        errors.any { it.code == errorCode }

    private fun buildDefaultMessage(): String {
        val errorSummary = errors.joinToString(separator = "; ") { error ->
            "Code: ${error.code}, Title: ${error.title}, Detail: ${error.detail}"
        }
        return "HTTP Code: $httpCode, Errors: [$errorSummary]"
    }
}
