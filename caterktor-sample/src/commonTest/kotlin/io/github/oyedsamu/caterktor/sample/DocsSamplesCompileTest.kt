package io.github.oyedsamu.caterktor.sample

import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCaterktor::class)
class DocsSamplesCompileTest {
    @Test
    fun quickStartSampleRunsAndRefreshesOnce(): Unit = runTest {
        val run = DocsSamples.quickStartWithAuthRefresh()

        assertEquals(User(id = "42", name = "Ada"), run.user)
        assertEquals(1, run.refreshCalls)
        assertEquals(2, run.requestCount)
        assertEquals(
            listOf("Bearer expired-token", "Bearer fresh-token"),
            run.authorizationHeaders,
        )
        assertTrue(run.logs.any { line -> line.contains("GET") && line.contains("/users/me") })
    }

    @Test
    fun repositoryTestSnippetCompilesAndRuns(): Unit = runTest {
        val user = DocsSamples.repositoryTestSnippet()

        assertEquals(User(id = "7", name = "Grace"), user)
    }

    @Test
    fun rawRequestSnippetCompiles(): Unit {
        val request = DocsSamples.rawRequestSnippet()

        assertEquals("https://caterktor.test/users/me", request.url)
        assertEquals("application/json", request.headers["Accept"])
    }
}
