package io.github.oyedsamu.caterktor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NetworkResultTest {

    private fun success(body: String = "ok"): NetworkResult.Success<String> =
        NetworkResult.Success(
            body = body,
            status = HttpStatus.OK,
            headers = Headers.Empty,
            durationMs = 42L,
            attempts = 1,
            requestId = "req-1",
        )

    private fun failure(
        error: NetworkError = NetworkError.Protocol("bad framing"),
    ): NetworkResult.Failure =
        NetworkResult.Failure(
            error = error,
            durationMs = 10L,
            attempts = 2,
            requestId = "req-2",
        )

    @Test
    fun getOrNull_returns_body_on_success() {
        val r: NetworkResult<String> = success("hello")
        assertEquals("hello", r.getOrNull())
    }

    @Test
    fun getOrNull_returns_null_on_failure() {
        val r: NetworkResult<String> = failure()
        assertNull(r.getOrNull())
    }

    @Test
    fun errorOrNull_returns_null_on_success() {
        val r: NetworkResult<String> = success()
        assertNull(r.errorOrNull())
    }

    @Test
    fun errorOrNull_returns_error_on_failure() {
        val err = NetworkError.Protocol("boom")
        val r: NetworkResult<String> = failure(err)
        assertEquals(err, r.errorOrNull())
    }

    @Test
    fun getOrThrow_returns_body_on_success() {
        val r: NetworkResult<String> = success("hi")
        assertEquals("hi", r.getOrThrow())
    }

    @Test
    fun getOrThrow_throws_on_failure() {
        val err = NetworkError.Protocol("nope")
        val r: NetworkResult<String> = failure(err)
        val ex = assertFailsWith<NetworkResultException> { r.getOrThrow() }
        assertEquals(err, ex.error)
    }

    @Test
    fun map_transforms_body_on_success_and_preserves_metadata() {
        val s = success("42")
        val mapped = s.map { it.toInt() }
        assertTrue(mapped is NetworkResult.Success)
        assertEquals(42, mapped.body)
        assertEquals(s.status, mapped.status)
        assertEquals(s.headers, mapped.headers)
        assertEquals(s.durationMs, mapped.durationMs)
        assertEquals(s.attempts, mapped.attempts)
        assertEquals(s.requestId, mapped.requestId)
    }

    @Test
    fun map_passes_failure_unchanged() {
        val f = failure()
        val r: NetworkResult<String> = f
        val mapped: NetworkResult<Int> = r.map { it.length }
        // Failure is NetworkResult<Nothing>; the same instance is returned.
        assertSame(f, mapped)
    }

    @Test
    fun fold_invokes_success_branch() {
        val r: NetworkResult<String> = success("x")
        val out = r.fold(onSuccess = { "S:${it.body}" }, onFailure = { "F" })
        assertEquals("S:x", out)
    }

    @Test
    fun fold_invokes_failure_branch() {
        val r: NetworkResult<String> = failure()
        val out = r.fold(onSuccess = { "S" }, onFailure = { "F:${it.attempts}" })
        assertEquals("F:2", out)
    }

    @Test
    fun onSuccess_invokes_action_and_returns_same_instance() {
        val s: NetworkResult<String> = success("y")
        var captured: String? = null
        val ret = s.onSuccess { captured = it.body }
        assertEquals("y", captured)
        assertSame(s, ret)
    }

    @Test
    fun onSuccess_does_not_invoke_on_failure_and_returns_same_instance() {
        val f: NetworkResult<String> = failure()
        var invoked = false
        val ret = f.onSuccess { invoked = true }
        assertFalse(invoked)
        assertSame(f, ret)
    }

    @Test
    fun onFailure_invokes_action_and_returns_same_instance() {
        val f: NetworkResult<String> = failure()
        var captured: NetworkError? = null
        val ret = f.onFailure { captured = it.error }
        assertEquals((f as NetworkResult.Failure).error, captured)
        assertSame(f, ret)
    }

    @Test
    fun onFailure_does_not_invoke_on_success_and_returns_same_instance() {
        val s: NetworkResult<String> = success()
        var invoked = false
        val ret = s.onFailure { invoked = true }
        assertFalse(invoked)
        assertSame(s, ret)
    }

    @Test
    fun toKotlinResult_success_maps_to_Result_success() {
        val r: NetworkResult<String> = success("k")
        val k = r.toKotlinResult()
        assertTrue(k.isSuccess)
        assertEquals("k", k.getOrNull())
    }

    @Test
    fun toKotlinResult_failure_maps_to_Result_failure_wrapping_NetworkResultException() {
        val err = NetworkError.Protocol("pfft")
        val r: NetworkResult<String> = failure(err)
        val k = r.toKotlinResult()
        assertTrue(k.isFailure)
        val ex = k.exceptionOrNull()
        assertTrue(ex is NetworkResultException)
        assertEquals(err, ex.error)
    }

    @Test
    fun failure_is_NetworkResult_of_Nothing_and_compiles_as_any_T() {
        // Compile-time proof: `Failure` is `NetworkResult<Nothing>`,
        // therefore assignable to `NetworkResult<String>` (and any other T).
        val asString: NetworkResult<String> = failure()
        val asInt: NetworkResult<Int> = failure()
        assertTrue(asString is NetworkResult.Failure)
        assertTrue(asInt is NetworkResult.Failure)
    }
}
