package io.github.oyedsamu.caterktor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NetworkErrorTest {

    @Test
    fun http_is_constructable_without_cause() {
        val e = NetworkError.Http(
            status = HttpStatus.BadRequest,
            headers = Headers.Empty,
            body = ErrorBody.Empty,
        )
        assertNull(e.cause)
        assertEquals(HttpStatus.BadRequest, e.status)
    }

    @Test
    fun http_is_constructable_with_cause() {
        val t = RuntimeException("x")
        val e = NetworkError.Http(
            status = HttpStatus.InternalServerError,
            headers = Headers.Empty,
            body = ErrorBody.Empty,
            cause = t,
        )
        assertSame(t, e.cause)
    }

    @Test
    fun connectionFailed_is_constructable_with_and_without_cause() {
        val a = NetworkError.ConnectionFailed(ConnectionFailureKind.Dns)
        assertNull(a.cause)
        val t = IllegalStateException()
        val b = NetworkError.ConnectionFailed(ConnectionFailureKind.TlsHandshake, cause = t)
        assertSame(t, b.cause)
    }

    @Test
    fun timeout_is_constructable_with_and_without_cause() {
        val a = NetworkError.Timeout(TimeoutKind.Connect)
        assertNull(a.cause)
        val t = RuntimeException()
        val b = NetworkError.Timeout(TimeoutKind.Deadline, cause = t)
        assertSame(t, b.cause)
    }

    @Test
    fun serialization_is_constructable_with_and_without_optional_fields() {
        val a = NetworkError.Serialization(SerializationPhase.Encoding)
        assertNull(a.cause)
        assertNull(a.rawBody)

        val raw = RawBody(bytes = byteArrayOf(1, 2, 3), contentType = "application/json")
        val t = RuntimeException()
        val b = NetworkError.Serialization(
            phase = SerializationPhase.Decoding,
            rawBody = raw,
            cause = t,
        )
        assertSame(t, b.cause)
        assertEquals(raw, b.rawBody)
    }

    @Test
    fun protocol_is_constructable_with_and_without_cause() {
        val a = NetworkError.Protocol("malformed")
        assertNull(a.cause)
        assertEquals("malformed", a.message)
        val t = RuntimeException()
        val b = NetworkError.Protocol("bad framing", cause = t)
        assertSame(t, b.cause)
    }

    @Test
    fun unknown_requires_nonnull_cause() {
        val t = RuntimeException("why")
        val e = NetworkError.Unknown(cause = t)
        // `cause` on NetworkError.Unknown is typed as Throwable (non-null).
        val viaInterface: Throwable? = (e as NetworkError).cause
        assertNotNull(viaInterface)
        val viaData: Throwable = e.cause
        assertSame(t, viaData)
    }

    @Test
    fun connectionFailureKind_has_exact_values() {
        val values = ConnectionFailureKind.entries.toSet()
        assertEquals(
            setOf(
                ConnectionFailureKind.Dns,
                ConnectionFailureKind.Refused,
                ConnectionFailureKind.Unreachable,
                ConnectionFailureKind.TlsHandshake,
            ),
            values,
        )
    }

    @Test
    fun timeoutKind_has_exact_values() {
        val values = TimeoutKind.entries.toSet()
        assertEquals(
            setOf(
                TimeoutKind.Connect,
                TimeoutKind.Socket,
                TimeoutKind.Request,
                TimeoutKind.Deadline,
            ),
            values,
        )
    }

    @Test
    fun serializationPhase_has_exact_values() {
        val values = SerializationPhase.entries.toSet()
        assertEquals(
            setOf(SerializationPhase.Encoding, SerializationPhase.Decoding),
            values,
        )
    }

    /**
     * Change-detector test: the set of [NetworkError] variants is part of the
     * public contract. Adding or removing a variant MUST be a deliberate act,
     * and in particular a `Cancelled` variant MUST NEVER be added — see the
     * KDoc on [NetworkError] and PRD-v2 §5.3.
     *
     * This test enumerates the exact known variants via an exhaustive `when`.
     * Adding a new variant will fail compilation here; removing one will fail
     * the assertion. Either way, the author must come update this test and
     * justify the change.
     */
    @Test
    fun exhaustive_when_covers_the_exact_known_variants_and_proves_no_Cancelled_variant() {
        val samples: List<NetworkError> = listOf(
            NetworkError.Http(HttpStatus.OK, Headers.Empty, ErrorBody.Empty),
            NetworkError.ConnectionFailed(ConnectionFailureKind.Dns),
            NetworkError.Timeout(TimeoutKind.Connect),
            NetworkError.Serialization(SerializationPhase.Encoding),
            NetworkError.Protocol("x"),
            NetworkError.CircuitOpen("default", CircuitBreakerState.Open),
            NetworkError.Offline(),
            NetworkError.Unknown(RuntimeException()),
        )

        // Exhaustive when: compilation will fail if a new sealed subtype is added,
        // forcing the author to update this test intentionally.
        val tags: List<String> = samples.map { err ->
            when (err) {
                is NetworkError.Http -> "Http"
                is NetworkError.ConnectionFailed -> "ConnectionFailed"
                is NetworkError.Timeout -> "Timeout"
                is NetworkError.Serialization -> "Serialization"
                is NetworkError.Protocol -> "Protocol"
                is NetworkError.CircuitOpen -> "CircuitOpen"
                is NetworkError.Offline -> "Offline"
                is NetworkError.Unknown -> "Unknown"
            }
        }

        assertEquals(
            listOf("Http", "ConnectionFailed", "Timeout", "Serialization", "Protocol", "CircuitOpen", "Offline", "Unknown"),
            tags,
        )

        // And explicitly: no variant name contains "Cancel".
        val allNames = tags.toSet()
        assertTrue(
            allNames.none { it.contains("Cancel", ignoreCase = true) },
            "NetworkError must not have a Cancelled-style variant. See PRD-v2 §5.3.",
        )
    }

    @Test
    fun rawBody_structural_equality_on_byte_contents() {
        val a = RawBody(byteArrayOf(1, 2, 3), "application/json")
        val b = RawBody(byteArrayOf(1, 2, 3), "application/json")
        val c = RawBody(byteArrayOf(1, 2, 4), "application/json")
        val d = RawBody(byteArrayOf(1, 2, 3), "text/plain")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertTrue(a != c)
        assertTrue(a != d)
    }

    @Test
    fun rawBody_asString_defaults_to_utf8() {
        val r = RawBody("héllo".encodeToByteArray(), "text/plain")
        assertEquals("héllo", r.asString())
    }

    @Test
    fun rawBody_toString_does_not_leak_contents() {
        val r = RawBody(byteArrayOf(1, 2, 3, 4), "application/json")
        val s = r.toString()
        assertTrue("contentType=application/json" in s)
        assertTrue("size=4" in s)
    }

    @Test
    fun errorBody_structural_equality() {
        val raw = RawBody(byteArrayOf(1), "text/plain")
        val a = ErrorBody(raw = raw, parsed = "parsed")
        val b = ErrorBody(raw = RawBody(byteArrayOf(1), "text/plain"), parsed = "parsed")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun networkResultException_wraps_error_and_cause() {
        val t = RuntimeException("root")
        val err = NetworkError.Unknown(cause = t)
        val ex = NetworkResultException(err)
        assertSame(err, ex.error)
        assertSame(t, ex.cause)
    }
}
