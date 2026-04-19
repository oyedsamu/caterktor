package io.github.oyedsamu.caterktor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HeadersTest {

    // ── get / case-insensitivity ──────────────────────────────────────────────

    @Test
    fun get_isCaseInsensitive() {
        val headers = Headers {
            set("Content-Type", "application/json")
        }
        assertEquals("application/json", headers["Content-Type"])
        assertEquals("application/json", headers["content-type"])
        assertEquals("application/json", headers["CONTENT-TYPE"])
        assertEquals("application/json", headers["cOnTeNt-TyPe"])
    }

    @Test
    fun get_returnsNullForAbsentHeader() {
        val headers = Headers.Empty
        assertNull(headers["Content-Type"])
    }

    @Test
    fun get_returnsFirstValueWhenMultipleExist() {
        val headers = Headers {
            add("Accept", "application/json")
            add("Accept", "text/plain")
        }
        assertEquals("application/json", headers["Accept"])
    }

    // ── getAll ────────────────────────────────────────────────────────────────

    @Test
    fun getAll_returnsAllValuesInInsertionOrder() {
        val headers = Headers {
            add("Accept", "application/json")
            add("Accept", "text/plain")
            add("Accept", "image/webp")
        }
        assertEquals(listOf("application/json", "text/plain", "image/webp"), headers.getAll("Accept"))
    }

    @Test
    fun getAll_isCaseInsensitive() {
        val headers = Headers {
            add("Accept", "application/json")
            add("Accept", "text/plain")
        }
        assertEquals(listOf("application/json", "text/plain"), headers.getAll("ACCEPT"))
        assertEquals(listOf("application/json", "text/plain"), headers.getAll("accept"))
    }

    @Test
    fun getAll_returnsEmptyListForAbsentHeader() {
        assertEquals(emptyList(), Headers.Empty.getAll("Missing"))
    }

    // ── contains ──────────────────────────────────────────────────────────────

    @Test
    fun contains_trueWhenHeaderPresent() {
        val headers = Headers { set("Authorization", "Bearer token") }
        assertTrue("Authorization" in headers)
        assertTrue("authorization" in headers)
        assertTrue("AUTHORIZATION" in headers)
    }

    @Test
    fun contains_falseWhenHeaderAbsent() {
        assertFalse("Authorization" in Headers.Empty)
    }

    // ── names ────────────────────────────────────────────────────────────────

    @Test
    fun names_returnsLowercaseNormalizedKeys() {
        val headers = Headers {
            set("Content-Type", "application/json")
            set("Authorization", "Bearer token")
        }
        val names = headers.names
        assertTrue("content-type" in names)
        assertTrue("authorization" in names)
        assertFalse("Content-Type" in names)
    }

    // ── plus / merge ──────────────────────────────────────────────────────────

    @Test
    fun plus_mergesWithoutDroppingValues() {
        val a = Headers { set("Content-Type", "application/json") }
        val b = Headers { set("Accept", "text/plain") }
        val merged = a + b
        assertEquals("application/json", merged["Content-Type"])
        assertEquals("text/plain", merged["Accept"])
    }

    @Test
    fun plus_concatenatesValuesForSharedNames() {
        val a = Headers { add("Accept", "application/json") }
        val b = Headers { add("Accept", "text/plain") }
        val merged = a + b
        assertEquals(listOf("application/json", "text/plain"), merged.getAll("Accept"))
    }

    @Test
    fun plus_withEmptyReturnsOriginal() {
        val a = Headers { set("Content-Type", "application/json") }
        val merged = a + Headers.Empty
        assertEquals(a, merged)
    }

    @Test
    fun plus_emptyPlusOtherReturnsOther() {
        val b = Headers { set("Accept", "text/plain") }
        val merged = Headers.Empty + b
        assertEquals(b, merged)
    }

    // ── equals and hashCode ──────────────────────────────────────────────────

    @Test
    fun equals_sameLogicalContentIsEqual() {
        val a = Headers {
            set("Content-Type", "application/json")
            set("Accept", "text/html")
        }
        val b = Headers {
            set("content-type", "application/json")
            set("accept", "text/html")
        }
        assertEquals(a, b)
    }

    @Test
    fun hashCode_equalHeadersHaveSameHashCode() {
        val a = Headers { set("Content-Type", "application/json") }
        val b = Headers { set("content-type", "application/json") }
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun equals_differentValuesAreNotEqual() {
        val a = Headers { set("Content-Type", "application/json") }
        val b = Headers { set("Content-Type", "text/plain") }
        assertTrue(a != b)
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    @Test
    fun builder_set_replacesExistingValues() {
        val headers = Headers {
            add("Accept", "application/json")
            add("Accept", "text/plain")
            set("Accept", "image/webp")
        }
        assertEquals(listOf("image/webp"), headers.getAll("Accept"))
    }

    @Test
    fun builder_add_appendsValue() {
        val headers = Headers {
            set("Accept", "application/json")
            add("Accept", "text/plain")
        }
        assertEquals(listOf("application/json", "text/plain"), headers.getAll("Accept"))
    }

    @Test
    fun builder_returnsBuilderForChaining() {
        val builder = Headers.Builder()
        val result = builder.set("Accept", "application/json").add("Accept", "text/plain")
        // fluent chain works — just verify the built headers
        val headers = result.build()
        assertEquals(listOf("application/json", "text/plain"), headers.getAll("Accept"))
    }

    // ── of factory ────────────────────────────────────────────────────────────

    @Test
    fun of_createsSingleEntry() {
        val headers = Headers.of("Content-Type" to "application/json")
        assertEquals("application/json", headers["Content-Type"])
    }

    @Test
    fun of_preservesDuplicateNames() {
        val headers = Headers.of(
            "Accept" to "application/json",
            "Accept" to "text/plain",
        )
        assertEquals(listOf("application/json", "text/plain"), headers.getAll("Accept"))
    }

    @Test
    fun of_isCaseInsensitive() {
        val headers = Headers.of("Content-Type" to "application/json")
        assertEquals("application/json", headers["content-type"])
    }

    // ── Empty ────────────────────────────────────────────────────────────────

    @Test
    fun empty_hasNoHeaders() {
        assertTrue(Headers.Empty.names.isEmpty())
        assertNull(Headers.Empty["any"])
        assertEquals(emptyList(), Headers.Empty.getAll("any"))
        assertFalse("any" in Headers.Empty)
    }

    // ── toMap ────────────────────────────────────────────────────────────────

    @Test
    fun toMap_hasLowercaseKeys() {
        val headers = Headers {
            set("Content-Type", "application/json")
            add("Accept", "text/plain")
        }
        val map = headers.toMap()
        assertTrue(map.containsKey("content-type"))
        assertTrue(map.containsKey("accept"))
        assertFalse(map.containsKey("Content-Type"))
    }

    @Test
    fun toMap_preservesAllValues() {
        val headers = Headers {
            add("Accept", "application/json")
            add("Accept", "text/plain")
        }
        assertEquals(listOf("application/json", "text/plain"), headers.toMap()["accept"])
    }
}
