package io.github.oyedsamu.caterktor

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class BodyImmutabilityTest {

    @Test
    fun rawBodyCopiesInputAndReadBytes() {
        val source = byteArrayOf(1, 2, 3)
        val raw = RawBody(source, "application/octet-stream")

        source[0] = 9
        assertContentEquals(byteArrayOf(1, 2, 3), raw.bytes)

        val read = raw.bytes
        read[1] = 8
        assertContentEquals(byteArrayOf(1, 2, 3), raw.bytes)
        assertEquals("RawBody(contentType=application/octet-stream, size=3)", raw.toString())
    }

    @Test
    fun requestBodyBytesCopiesInputAndReadBytes() {
        val source = byteArrayOf(1, 2, 3)
        val body = RequestBody.Bytes(source, "application/octet-stream")

        source[0] = 9
        assertContentEquals(byteArrayOf(1, 2, 3), body.bytes)

        val read = body.bytes
        read[1] = 8
        assertContentEquals(byteArrayOf(1, 2, 3), body.bytes)
    }
}
