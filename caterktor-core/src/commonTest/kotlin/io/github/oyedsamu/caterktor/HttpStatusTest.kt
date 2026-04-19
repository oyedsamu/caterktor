package io.github.oyedsamu.caterktor

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HttpStatusTest {

    @Test
    fun isInformational_returnsTrueFor1xx() {
        assertTrue(HttpStatus(100).isInformational)
        assertTrue(HttpStatus(199).isInformational)
        assertFalse(HttpStatus(200).isInformational)
        assertFalse(HttpStatus(99).isInformational)
    }

    @Test
    fun isSuccess_returnsTrueFor2xx() {
        assertTrue(HttpStatus(200).isSuccess)
        assertTrue(HttpStatus(299).isSuccess)
        assertFalse(HttpStatus(300).isSuccess)
        assertFalse(HttpStatus(199).isSuccess)
    }

    @Test
    fun isRedirect_returnsTrueFor3xx() {
        assertTrue(HttpStatus(300).isRedirect)
        assertTrue(HttpStatus(399).isRedirect)
        assertFalse(HttpStatus(400).isRedirect)
        assertFalse(HttpStatus(299).isRedirect)
    }

    @Test
    fun isClientError_returnsTrueFor4xx() {
        assertTrue(HttpStatus(400).isClientError)
        assertTrue(HttpStatus(499).isClientError)
        assertFalse(HttpStatus(500).isClientError)
        assertFalse(HttpStatus(399).isClientError)
    }

    @Test
    fun isServerError_returnsTrueFor5xx() {
        assertTrue(HttpStatus(500).isServerError)
        assertTrue(HttpStatus(599).isServerError)
        assertFalse(HttpStatus(600).isServerError)
        assertFalse(HttpStatus(499).isServerError)
    }

    @Test
    fun toString_returnsCodeAsString() {
        assertEquals("200", HttpStatus(200).toString())
        assertEquals("404", HttpStatus(404).toString())
        assertEquals("500", HttpStatus(500).toString())
        assertEquals("0", HttpStatus(0).toString())
    }

    @Test
    fun namedConstants_haveCorrectCodes() {
        assertEquals(200, HttpStatus.OK.code)
        assertEquals(201, HttpStatus.Created.code)
        assertEquals(202, HttpStatus.Accepted.code)
        assertEquals(204, HttpStatus.NoContent.code)
        assertEquals(301, HttpStatus.MovedPermanently.code)
        assertEquals(302, HttpStatus.Found.code)
        assertEquals(304, HttpStatus.NotModified.code)
        assertEquals(400, HttpStatus.BadRequest.code)
        assertEquals(401, HttpStatus.Unauthorized.code)
        assertEquals(403, HttpStatus.Forbidden.code)
        assertEquals(404, HttpStatus.NotFound.code)
        assertEquals(405, HttpStatus.MethodNotAllowed.code)
        assertEquals(409, HttpStatus.Conflict.code)
        assertEquals(410, HttpStatus.Gone.code)
        assertEquals(422, HttpStatus.UnprocessableEntity.code)
        assertEquals(429, HttpStatus.TooManyRequests.code)
        assertEquals(500, HttpStatus.InternalServerError.code)
        assertEquals(502, HttpStatus.BadGateway.code)
        assertEquals(503, HttpStatus.ServiceUnavailable.code)
        assertEquals(504, HttpStatus.GatewayTimeout.code)
    }

    @Test
    fun namedConstants_classifyCorrectly() {
        assertTrue(HttpStatus.OK.isSuccess)
        assertTrue(HttpStatus.NotFound.isClientError)
        assertTrue(HttpStatus.InternalServerError.isServerError)
        assertTrue(HttpStatus.MovedPermanently.isRedirect)
    }
}
