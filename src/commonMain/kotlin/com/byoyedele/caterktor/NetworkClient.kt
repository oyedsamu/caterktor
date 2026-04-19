package com.byoyedele.caterktor

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

interface NetworkClient {
    suspend fun <T> execute(
        request: NetworkRequest,
        responseDeserializer: KSerializer<T>,
    ): Result<T>
}

data class NetworkRequest(
    val method: NetworkMethod,
    val path: String,
    val headers: Map<String, String> = emptyMap(),
    val queryParameters: Map<String, List<String>> = emptyMap(),
    val body: JsonNetworkRequestBody<*>? = null,
)

enum class NetworkMethod {
    Get,
    Post,
    Put,
    Patch,
    Delete,
}

data class JsonNetworkRequestBody<T>(
    val value: T,
    val serializer: KSerializer<T>,
    val wrapInApiRequest: Boolean = true,
)

class NetworkRequestBuilder {
    private val headers = linkedMapOf<String, String>()
    private val queryParameters = linkedMapOf<String, MutableList<String>>()

    fun header(name: String, value: String) {
        headers[name] = value
    }

    fun queryParameter(name: String, value: String) {
        queryParameters.getOrPut(name) { mutableListOf() }.add(value)
    }

    internal fun buildHeaders(): Map<String, String> = headers.toMap()

    internal fun buildQueryParameters(): Map<String, List<String>> =
        queryParameters.mapValues { (_, values) -> values.toList() }
}

suspend inline fun <reified T> NetworkClient.get(
    path: String,
    noinline block: NetworkRequestBuilder.() -> Unit = {},
): Result<T> = execute(
    request = networkRequest(NetworkMethod.Get, path, block),
    responseDeserializer = serializer(),
)

suspend inline fun <reified I, reified O> NetworkClient.post(
    path: String,
    body: I,
    noinline block: NetworkRequestBuilder.() -> Unit = {},
): Result<O> = execute(
    request = networkRequest(
        method = NetworkMethod.Post,
        path = path,
        block = block,
        body = JsonNetworkRequestBody(body, serializer<I>()),
    ),
    responseDeserializer = serializer(),
)

suspend inline fun <reified O> NetworkClient.post(
    path: String,
    noinline block: NetworkRequestBuilder.() -> Unit = {},
): Result<O> = execute(
    request = networkRequest(NetworkMethod.Post, path, block),
    responseDeserializer = serializer(),
)

suspend inline fun <reified I, reified O> NetworkClient.put(
    path: String,
    body: I,
    noinline block: NetworkRequestBuilder.() -> Unit = {},
): Result<O> = execute(
    request = networkRequest(
        method = NetworkMethod.Put,
        path = path,
        block = block,
        body = JsonNetworkRequestBody(body, serializer<I>()),
    ),
    responseDeserializer = serializer(),
)

suspend inline fun <reified I, reified O> NetworkClient.patch(
    path: String,
    body: I,
    noinline block: NetworkRequestBuilder.() -> Unit = {},
): Result<O> = execute(
    request = networkRequest(
        method = NetworkMethod.Patch,
        path = path,
        block = block,
        body = JsonNetworkRequestBody(body, serializer<I>()),
    ),
    responseDeserializer = serializer(),
)

suspend inline fun <reified O> NetworkClient.delete(
    path: String,
    noinline block: NetworkRequestBuilder.() -> Unit = {},
): Result<O> = execute(
    request = networkRequest(NetworkMethod.Delete, path, block),
    responseDeserializer = serializer(),
)

suspend inline fun <reified I, reified O> NetworkClient.plainJsonPost(
    path: String,
    body: I,
    noinline block: NetworkRequestBuilder.() -> Unit = {},
): Result<O> = execute(
    request = networkRequest(
        method = NetworkMethod.Post,
        path = path,
        block = block,
        body = JsonNetworkRequestBody(
            value = body,
            serializer = serializer<I>(),
            wrapInApiRequest = false,
        ),
    ),
    responseDeserializer = serializer(),
)

fun networkRequest(
    method: NetworkMethod,
    path: String,
    block: NetworkRequestBuilder.() -> Unit,
    body: JsonNetworkRequestBody<*>? = null,
): NetworkRequest {
    val builder = NetworkRequestBuilder().apply(block)
    return NetworkRequest(
        method = method,
        path = path,
        headers = builder.buildHeaders(),
        queryParameters = builder.buildQueryParameters(),
        body = body,
    )
}
