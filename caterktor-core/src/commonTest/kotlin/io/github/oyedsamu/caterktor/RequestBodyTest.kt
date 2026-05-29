package io.github.oyedsamu.caterktor

import kotlinx.io.Buffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RequestBodyTest {

    @Test
    fun formUrlEncodesFieldsInOrder() {
        val body = RequestBody.Form(
            RequestBody.Form.Field("space name", "a+b & c"),
            RequestBody.Form.Field("symbol", "\u00A3"),
            RequestBody.Form.Field("empty", ""),
        )

        val expected = "space+name=a%2Bb+%26+c&symbol=%C2%A3&empty="

        assertEquals("application/x-www-form-urlencoded; charset=UTF-8", body.contentType)
        assertEquals(expected.encodeToByteArray().size.toLong(), body.contentLength)
        assertEquals(expected, body.bytes().decodeToString())
        assertEquals(expected, body.bytes().decodeToString())
    }

    @Test
    fun formCopiesFieldList() {
        val fields = mutableListOf(RequestBody.Form.Field("one", "1"))
        val body = RequestBody.Form(fields)

        fields += RequestBody.Form.Field("two", "2")

        assertEquals(listOf(RequestBody.Form.Field("one", "1")), body.fields)
        assertEquals("one=1", body.bytes().decodeToString())
    }

    @Test
    fun formRejectsBlankFieldName() {
        assertFailsWith<IllegalArgumentException> {
            RequestBody.Form.Field(" ", "value")
        }
    }

    @Test
    fun multipartBuildsFormDataBodyWithKnownLength() {
        val body = RequestBody.Multipart(
            parts = listOf(
                RequestBody.Multipart.Part.field("title", "hello"),
                RequestBody.Multipart.Part.formData(
                    name = "file",
                    filename = "note.txt",
                    body = RequestBody.Bytes("abc".encodeToByteArray(), "text/plain"),
                    headers = Headers.of("X-Check" to "yes"),
                ),
            ),
            boundary = "test-boundary",
        )

        val expected = buildString {
            append("--test-boundary\r\n")
            append("content-disposition: form-data; name=\"title\"\r\n")
            append("content-type: text/plain; charset=UTF-8\r\n")
            append("\r\n")
            append("hello\r\n")
            append("--test-boundary\r\n")
            append("content-disposition: form-data; name=\"file\"; filename=\"note.txt\"\r\n")
            append("content-type: text/plain\r\n")
            append("x-check: yes\r\n")
            append("\r\n")
            append("abc\r\n")
            append("--test-boundary--\r\n")
        }

        assertEquals("multipart/form-data; boundary=test-boundary", body.contentType)
        assertEquals(expected.encodeToByteArray().size.toLong(), body.contentLength)
        assertEquals(expected, body.bytes().decodeToString())
        assertEquals(expected, body.bytes().decodeToString())
    }

    @Test
    fun multipartUsesFreshPartSource() {
        var opens = 0
        val body = RequestBody.Multipart(
            parts = listOf(
                RequestBody.Multipart.Part.formData(
                    name = "stream",
                    body = RequestBody.Source(
                        sourceFactory = {
                            opens += 1
                            Buffer().also { it.write("fresh".encodeToByteArray()) }
                        },
                        contentType = "text/plain",
                    ),
                ),
            ),
            boundary = "source-boundary",
        )

        assertNull(body.contentLength)
        assertEquals(body.bytes().decodeToString(), body.bytes().decodeToString())
        assertEquals(2, opens)
    }

    @Test
    fun multipartRejectsInvalidConstruction() {
        assertFailsWith<IllegalArgumentException> {
            RequestBody.Multipart(emptyList(), boundary = "test-boundary")
        }
        assertFailsWith<IllegalArgumentException> {
            RequestBody.Multipart(
                listOf(RequestBody.Multipart.Part.field("name", "value")),
                boundary = "bad boundary",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            RequestBody.Multipart.Part(Headers.Empty, RequestBody.Text("value"))
        }
        assertFailsWith<IllegalArgumentException> {
            RequestBody.Multipart.Part.formData(
                name = "file",
                body = RequestBody.Text("value"),
                headers = Headers.of("Content-Length" to "5"),
            )
        }
    }

    @Test
    fun bytesBodyStillCopiesInputAndSourceReplayWorks() {
        val payload = byteArrayOf(1, 2, 3)
        val body = RequestBody.Bytes(payload, "application/octet-stream")

        payload[0] = 9

        assertContentEquals(byteArrayOf(1, 2, 3), body.bytes)
        assertContentEquals(byteArrayOf(1, 2, 3), body.bytes())
        assertContentEquals(byteArrayOf(1, 2, 3), body.bytes())
    }
}
