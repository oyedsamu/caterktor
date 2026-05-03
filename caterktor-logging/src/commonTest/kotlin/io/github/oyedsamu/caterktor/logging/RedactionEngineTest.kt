@file:OptIn(ExperimentalCaterktor::class)

package io.github.oyedsamu.caterktor.logging

import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import io.github.oyedsamu.caterktor.RequestBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RedactionEngineTest {

    private val engine = RedactionEngine()

    // ── redactFormBody ────────────────────────────────────────────────────────

    @Test
    fun formBodyRedactsSensitiveFields() {
        val body = RequestBody.Form(
            listOf(
                RequestBody.Form.Field("grant_type", "client_credentials"),
                RequestBody.Form.Field("client_id", "myapp"),
                RequestBody.Form.Field("client_secret", "super-secret"),
                RequestBody.Form.Field("refresh_token", "tok-abc"),
            )
        )
        val result = engine.redactFormBody(body)
        assertTrue(result.contains("grant_type=client_credentials"))
        assertTrue(result.contains("client_id=myapp"))
        assertTrue(result.contains("client_secret=***"))
        assertTrue(result.contains("refresh_token=***"))
        assertFalse(result.contains("super-secret"))
        assertFalse(result.contains("tok-abc"))
    }

    @Test
    fun formBodyFieldNamesAreCaseInsensitive() {
        val body = RequestBody.Form(
            listOf(
                RequestBody.Form.Field("Password", "hunter2"),
                RequestBody.Form.Field("ACCESS_TOKEN", "letmein"),
                RequestBody.Form.Field("username", "ada"),
            )
        )
        val result = engine.redactFormBody(body)
        assertTrue(result.contains("Password=***"))
        assertTrue(result.contains("ACCESS_TOKEN=***"))
        assertTrue(result.contains("username=ada"))
        assertFalse(result.contains("hunter2"))
        assertFalse(result.contains("letmein"))
    }

    @Test
    fun formBodyWithNoSensitiveFieldsIsUnchanged() {
        val body = RequestBody.Form(
            listOf(
                RequestBody.Form.Field("grant_type", "authorization_code"),
                RequestBody.Form.Field("code", "xyz"),
                RequestBody.Form.Field("redirect_uri", "https://example.com/cb"),
            )
        )
        val result = engine.redactFormBody(body)
        assertEquals("grant_type=authorization_code&code=xyz&redirect_uri=https://example.com/cb", result)
    }

    @Test
    fun formBodyEmptyIsEmpty() {
        val result = engine.redactFormBody(RequestBody.Form(emptyList()))
        assertEquals("", result)
    }

    @Test
    fun formBodyCustomReplacementIsUsed() {
        val custom = RedactionEngine(replacement = "<hidden>")
        val body = RequestBody.Form(listOf(RequestBody.Form.Field("password", "s3cr3t")))
        val result = custom.redactFormBody(body)
        assertEquals("password=<hidden>", result)
    }

    @Test
    fun formBodyRegexRulesAreAppliedAfterFieldRedaction() {
        val engineWithRegex = RedactionEngine(
            regexRules = listOf(RegexRedactionRule(Regex("code=\\w+")))
        )
        val body = RequestBody.Form(
            listOf(
                RequestBody.Form.Field("code", "abc123"),
                RequestBody.Form.Field("client_secret", "shh"),
            )
        )
        val result = engineWithRegex.redactFormBody(body)
        assertFalse(result.contains("abc123"))
        assertFalse(result.contains("shh"))
        assertTrue(result.contains("client_secret=***"))
    }

    // ── redactUrl ─────────────────────────────────────────────────────────────

    @Test
    fun urlWithNoQueryParamsIsUnchanged() {
        val url = "https://api.example.com/v1/users"
        assertEquals(url, engine.redactUrl(url))
    }

    @Test
    fun urlSensitiveQueryParamsAreRedacted() {
        val url = "https://api.example.com/v1/login?client_id=app&client_secret=shh&redirect_uri=https://cb"
        val result = engine.redactUrl(url)
        assertTrue(result.contains("client_id=app"))
        assertTrue(result.contains("client_secret=***"))
        assertTrue(result.contains("redirect_uri=https://cb"))
        assertFalse(result.contains("shh"))
    }

    @Test
    fun urlParameterNamesAreCaseInsensitive() {
        val url = "https://api.example.com/?ACCESS_TOKEN=letmein&page=2"
        val result = engine.redactUrl(url)
        assertTrue(result.contains("ACCESS_TOKEN=***"))
        assertTrue(result.contains("page=2"))
    }

    // ── redactHeader ─────────────────────────────────────────────────────────

    @Test
    fun sensitiveHeaderIsRedacted() {
        assertEquals("***", engine.redactHeader("Authorization", "Bearer tok"))
        assertEquals("***", engine.redactHeader("authorization", "Bearer tok"))
    }

    @Test
    fun nonSensitiveHeaderIsPassedThrough() {
        val value = "application/json"
        assertEquals(value, engine.redactHeader("Content-Type", value))
    }

    // ── redactBody ────────────────────────────────────────────────────────────

    @Test
    fun jsonBodyRedactsSensitiveFields() {
        val json = """{"username":"ada","password":"hunter2","email":"ada@example.com"}"""
        val result = engine.redactBody("application/json", json.encodeToByteArray())
        assertTrue(result.contains("\"password\":\"***\""))
        assertTrue(result.contains("\"username\":\"ada\""))
        assertFalse(result.contains("hunter2"))
    }

    @Test
    fun bodyExceedingMaxBytesIsReplaced() {
        val small = RedactionEngine(maxBodyBytes = 10)
        val bytes = "this is more than ten bytes".encodeToByteArray()
        val result = small.redactBody("text/plain", bytes)
        assertTrue(result.startsWith("<body"))
        assertTrue(result.contains("exceeds max"))
    }

    @Test
    fun binaryBodyIsSuppressed() {
        val bytes = byteArrayOf(0x00, 0x01, 0x02, 0xFF.toByte())
        val result = engine.redactBody(null, bytes)
        assertTrue(result.startsWith("<binary"))
    }
}
