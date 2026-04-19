package com.byoyedele.caterktor

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class KtorNetworkClient(
    private val httpClient: HttpClient,
    private val json: Json = defaultNetworkJson,
    private val successStatusCodes: Set<HttpStatusCode> = defaultSuccessStatusCodes,
    private val logger: CaterKtorLogger = CaterKtorLogger.None,
) : NetworkClient {

    override suspend fun <T> execute(
        request: NetworkRequest,
        responseDeserializer: KSerializer<T>,
    ): Result<T> = runCatching {
        logger.logIfEnabled { request.toLogMessage() }
        val response = executeRequest(request)
        logger.logIfEnabled { response.toLogMessage(request) }
        if (response.status !in successStatusCodes) {
            throw parseErrorResponse(response)
        }
        parseResponse(response, responseDeserializer)
    }.onFailure { throwable ->
        logger.logIfEnabled { request.toFailureLogMessage(throwable) }
    }

    private suspend fun executeRequest(request: NetworkRequest): HttpResponse {
        return when (request.method) {
            NetworkMethod.Get -> httpClient.get(request.path) { apply(request) }
            NetworkMethod.Post -> httpClient.post(request.path) { apply(request) }
            NetworkMethod.Put -> httpClient.put(request.path) { apply(request) }
            NetworkMethod.Patch -> httpClient.patch(request.path) { apply(request) }
            NetworkMethod.Delete -> httpClient.delete(request.path) { apply(request) }
        }
    }

    private fun HttpRequestBuilder.apply(request: NetworkRequest) {
        request.headers.forEach { (name, value) -> header(name, value) }
        url {
            request.queryParameters.forEach { (name, values) ->
                values.forEach { parameters.append(name, it) }
            }
        }
        request.body?.let { setBody(encodeBody(it)) }
    }

    private fun encodeBody(body: JsonNetworkRequestBody<*>): TextContent =
        TextContent(
            text = if (body.wrapInApiRequest) encodeWrappedBody(body) else encodeRawBody(body),
            contentType = ContentType.Application.Json,
        )

    private fun <T> encodeWrappedBody(body: JsonNetworkRequestBody<T>): String =
        json.encodeToString(
            serializer = ApiRequest.serializer(body.serializer),
            value = ApiRequest(body.value),
        )

    private fun <T> encodeRawBody(body: JsonNetworkRequestBody<T>): String =
        json.encodeToString(
            serializer = body.serializer,
            value = body.value,
        )

    private suspend fun <T> parseResponse(
        response: HttpResponse,
        responseSerializer: KSerializer<T>,
    ): T {
        if (responseSerializer.descriptor.serialName == Unit.serializer().descriptor.serialName) {
            @Suppress("UNCHECKED_CAST")
            return Unit as T
        }

        val body = json.decodeFromString(
            deserializer = ApiResponse.serializer(responseSerializer),
            string = response.bodyAsText(),
        )
        @Suppress("UNCHECKED_CAST")
        return when {
            responseSerializer.descriptor.isNullable -> body.data as T
            body.data == null -> throw NullPointerException("Unexpected null `data` in the response.")
            else -> body.data as T
        }
    }

    private suspend fun parseErrorResponse(response: HttpResponse): NetworkException {
        val responseBody = response.bodyAsText()
        val apiErrorResponse = json.decodeFromStringOrNull(ApiErrorResponse.serializer(), responseBody)
        return NetworkException(
            httpCode = response.status.value,
            errors = apiErrorResponse?.errors
                ?: parseMonolithErrorResponse(response.status.value, responseBody),
        )
    }

    private fun parseMonolithErrorResponse(
        httpCode: Int,
        responseBody: String,
    ): List<ApiError> {
        val apiErrorResponse = json.decodeFromStringOrNull(ApiMonolithErrorResponse.serializer(), responseBody)
        return apiErrorResponse?.let {
            listOf(
                ApiError(
                    code = httpCode.toString(),
                    detail = apiErrorResponse.message,
                )
            )
        } ?: emptyList()
    }

    private fun <T> Json.decodeFromStringOrNull(
        deserializer: DeserializationStrategy<T>,
        string: String,
    ): T? = runCatching {
        decodeFromString(deserializer, string)
    }.getOrNull()
}

fun defaultKtorHttpClient(
    block: io.ktor.client.HttpClientConfig<*>.() -> Unit = {},
): HttpClient = HttpClient(block)

val defaultNetworkJson: Json = Json {
    isLenient = true
    encodeDefaults = true
    ignoreUnknownKeys = true
    explicitNulls = false
}

val defaultSuccessStatusCodes: Set<HttpStatusCode> = setOf(
    HttpStatusCode.OK,
    HttpStatusCode.Created,
    HttpStatusCode.Accepted,
    HttpStatusCode.NonAuthoritativeInformation,
    HttpStatusCode.NoContent,
    HttpStatusCode.ResetContent,
    HttpStatusCode.PartialContent,
)

private inline fun CaterKtorLogger.logIfEnabled(message: () -> String) {
    if (isEnabled) log(message())
}
