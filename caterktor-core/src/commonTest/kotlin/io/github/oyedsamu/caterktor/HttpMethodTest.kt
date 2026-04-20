package io.github.oyedsamu.caterktor

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HttpMethodTest {

    @Test
    fun isSafe_trueForGetHeadOptions() {
        assertTrue(HttpMethod.GET.isSafe)
        assertTrue(HttpMethod.HEAD.isSafe)
        assertTrue(HttpMethod.OPTIONS.isSafe)
    }

    @Test
    fun isSafe_falseForUnsafeMethods() {
        assertFalse(HttpMethod.POST.isSafe)
        assertFalse(HttpMethod.PUT.isSafe)
        assertFalse(HttpMethod.PATCH.isSafe)
        assertFalse(HttpMethod.DELETE.isSafe)
    }

    @Test
    fun isIdempotent_trueForSafeMethods() {
        assertTrue(HttpMethod.GET.isIdempotent)
        assertTrue(HttpMethod.HEAD.isIdempotent)
        assertTrue(HttpMethod.OPTIONS.isIdempotent)
    }

    @Test
    fun isIdempotent_trueForPutAndDelete() {
        assertTrue(HttpMethod.PUT.isIdempotent)
        assertTrue(HttpMethod.DELETE.isIdempotent)
    }

    @Test
    fun isIdempotent_falseForPostAndPatch() {
        assertFalse(HttpMethod.POST.isIdempotent)
        assertFalse(HttpMethod.PATCH.isIdempotent)
    }

    @Test
    fun allValues_coverExpectedMethods() {
        val values = HttpMethod.entries.toSet()
        assertTrue(HttpMethod.GET in values)
        assertTrue(HttpMethod.HEAD in values)
        assertTrue(HttpMethod.POST in values)
        assertTrue(HttpMethod.PUT in values)
        assertTrue(HttpMethod.PATCH in values)
        assertTrue(HttpMethod.DELETE in values)
        assertTrue(HttpMethod.OPTIONS in values)
    }
}
