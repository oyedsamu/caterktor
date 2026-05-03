package io.github.oyedsamu.caterktor.testing

import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import io.github.oyedsamu.caterktor.HttpMethod
import io.github.oyedsamu.caterktor.HttpStatus
import io.github.oyedsamu.caterktor.NetworkRequest
import io.github.oyedsamu.caterktor.NetworkResponse
import io.github.oyedsamu.caterktor.RequestBody
import io.github.oyedsamu.caterktor.ResponseBody

@ExperimentalCaterktor
public fun NetworkRequest.assertThat(block: NetworkRequestAssertions.() -> Unit): NetworkRequest =
    apply { NetworkRequestAssertions(this).block() }

@ExperimentalCaterktor
public fun NetworkResponse.assertThat(block: NetworkResponseAssertions.() -> Unit): NetworkResponse =
    apply { NetworkResponseAssertions(this).block() }

@ExperimentalCaterktor
public class NetworkRequestAssertions(
    public val request: NetworkRequest,
) {
    public fun hasMethod(method: HttpMethod): NetworkRequestAssertions = apply {
        assertEqual("method", method, request.method)
    }

    public fun hasUrl(url: String): NetworkRequestAssertions = apply {
        assertEqual("url", url, request.url)
    }

    public fun hasHeader(name: String, value: String): NetworkRequestAssertions = apply {
        assertEqual("header $name", value, request.headers[name])
    }

    public fun hasNoHeader(name: String): NetworkRequestAssertions = apply {
        if (name in request.headers) {
            throw AssertionError("Expected header <$name> to be absent, but was <${request.headers[name]}>")
        }
    }

    public fun hasBodyText(text: String): NetworkRequestAssertions = apply {
        val body = request.body ?: throw AssertionError("Expected request body <$text>, but body was null")
        assertEqual("request body", text, body.asText())
    }
}

@ExperimentalCaterktor
public class NetworkResponseAssertions(
    public val response: NetworkResponse,
) {
    public fun hasStatus(status: HttpStatus): NetworkResponseAssertions = apply {
        assertEqual("status", status, response.status)
    }

    public fun hasHeader(name: String, value: String): NetworkResponseAssertions = apply {
        assertEqual("header $name", value, response.headers[name])
    }

    public fun hasNoHeader(name: String): NetworkResponseAssertions = apply {
        if (name in response.headers) {
            throw AssertionError("Expected header <$name> to be absent, but was <${response.headers[name]}>")
        }
    }

    public fun hasBodyText(text: String): NetworkResponseAssertions = apply {
        assertEqual("response body", text, response.body.asText())
    }
}

private fun assertEqual(label: String, expected: Any?, actual: Any?) {
    if (expected != actual) {
        throw AssertionError("Expected $label <$expected>, but was <$actual>")
    }
}

private fun RequestBody.asText(): String =
    when (this) {
        is RequestBody.Bytes -> bytes.decodeToString()
        is RequestBody.Text -> text
        is RequestBody.Form -> bytes().decodeToString()
        is RequestBody.Multipart -> bytes().decodeToString()
        is RequestBody.Source -> bytes().decodeToString()
    }

private fun ResponseBody.asText(): String =
    when (this) {
        is ResponseBody.Bytes -> bytes.decodeToString()
        is ResponseBody.Source -> bytes().decodeToString()
    }
