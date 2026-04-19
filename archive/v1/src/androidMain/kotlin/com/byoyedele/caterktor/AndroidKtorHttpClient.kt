package com.byoyedele.caterktor

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

fun androidKtorHttpClient(
    block: io.ktor.client.HttpClientConfig<*>.() -> Unit = {},
): HttpClient = HttpClient(OkHttp) {
    block()
}
