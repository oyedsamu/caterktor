@file:OptIn(ExperimentalCaterktor::class)

package io.github.oyedsamu.caterktor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UrlResolverTest {

    @Test
    fun absolute_http_url_is_returned_unchanged() {
        assertEquals(
            "http://example.test/foo",
            resolveUrl(baseUrl = "https://api.other.test", path = "http://example.test/foo"),
        )
    }

    @Test
    fun absolute_https_url_is_returned_unchanged() {
        assertEquals(
            "https://example.test/foo",
            resolveUrl(baseUrl = null, path = "https://example.test/foo"),
        )
    }

    @Test
    fun relative_path_combines_with_base_url() {
        assertEquals(
            "https://api.test/v1/users",
            resolveUrl(baseUrl = "https://api.test/v1", path = "users"),
        )
    }

    @Test
    fun relative_path_combines_with_base_url_ignoring_trailing_slash() {
        assertEquals(
            "https://api.test/v1/users",
            resolveUrl(baseUrl = "https://api.test/v1/", path = "users"),
        )
    }

    @Test
    fun leading_slash_path_uses_origin_of_base_url() {
        assertEquals(
            "https://api.test/raw",
            resolveUrl(baseUrl = "https://api.test/v1/deep", path = "/raw"),
        )
    }

    @Test
    fun null_base_url_with_relative_path_throws() {
        val ex = assertFailsWith<IllegalArgumentException> {
            resolveUrl(baseUrl = null, path = "users")
        }
        assertTrue("baseUrl" in ex.message.orEmpty())
    }

    @Test
    fun expand_path_template_replaces_single_placeholder() {
        assertEquals(
            "users/42",
            expandPathTemplate("users/{id}", mapOf("id" to "42")),
        )
    }

    @Test
    fun expand_path_template_replaces_multiple_placeholders() {
        assertEquals(
            "users/42/posts/7",
            expandPathTemplate(
                template = "users/{id}/posts/{postId}",
                params = mapOf("id" to "42", "postId" to "7"),
            ),
        )
    }

    @Test
    fun expand_path_template_percent_encodes_special_chars() {
        assertEquals(
            "search/hello%20world%2F%3F",
            expandPathTemplate("search/{q}", mapOf("q" to "hello world/?")),
        )
    }

    @Test
    fun expand_path_template_leaves_unknown_placeholder_as_is() {
        assertEquals(
            "users/{id}",
            expandPathTemplate("users/{id}", mapOf("other" to "x")),
        )
    }

    @Test
    fun expand_path_template_with_empty_params_is_noop() {
        assertEquals(
            "users/{id}",
            expandPathTemplate("users/{id}", emptyMap()),
        )
    }

    @Test
    fun append_query_parameters_adds_encoded_query_to_url_without_query() {
        assertEquals(
            "https://api.test/search?q=hello%20world&page=2",
            appendQueryParameters(
                "https://api.test/search",
                QueryParameters {
                    add("q", "hello world")
                    add("page", 2)
                },
            ),
        )
    }

    @Test
    fun append_query_parameters_preserves_existing_query_and_fragment() {
        assertEquals(
            "https://api.test/search?existing=true&q=kmp#section",
            appendQueryParameters(
                "https://api.test/search?existing=true#section",
                QueryParameters { add("q", "kmp") },
            ),
        )
    }

    @Test
    fun append_query_parameters_preserves_repeated_keys_and_skips_nulls_from_map() {
        assertEquals(
            "https://api.test/items?tag=kmp&tag=ktor&limit=20",
            appendQueryParameters(
                "https://api.test/items",
                queryParameters(
                    mapOf(
                        "tag" to listOf("kmp", null, "ktor"),
                        "unused" to null,
                        "limit" to 20,
                    ),
                ),
            ),
        )
    }
}
