@file:OptIn(ExperimentalCaterktor::class)

package io.github.oyedsamu.caterktor.auth

import io.github.oyedsamu.caterktor.CaterKtor
import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import io.github.oyedsamu.caterktor.Headers
import io.github.oyedsamu.caterktor.HttpStatus
import io.github.oyedsamu.caterktor.NetworkResponse
import io.github.oyedsamu.caterktor.NetworkResult
import io.github.oyedsamu.caterktor.Transport
import io.github.oyedsamu.caterktor.get
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class AuthDslTest {

    @Test
    fun authBearerDslInstallsBearerInterceptor() = runTest {
        var seenAuth: String? = null
        val client = CaterKtor {
            transport = Transport { request ->
                seenAuth = request.headers["Authorization"]
                response(HttpStatus.NoContent)
            }
            auth {
                bearer {
                    tokenProvider { "dsl-token" }
                }
            }
        }

        val result = client.get<Unit>("https://example.test/private")

        assertIs<NetworkResult.Success<Unit>>(result)
        assertEquals("Bearer dsl-token", seenAuth)
    }

    @Test
    fun authDslWithRefreshInstallsRefreshInterceptor() = runTest {
        val seenAuth = mutableListOf<String?>()
        var refreshCalls = 0
        val client = CaterKtor {
            transport = Transport { request ->
                val auth = request.headers["Authorization"]
                seenAuth += auth
                if (auth == "Bearer new") response(HttpStatus.NoContent) else response(HttpStatus.Unauthorized)
            }
            auth {
                bearer("old")
                refresh {
                    refreshToken {
                        refreshCalls += 1
                        "new"
                    }
                    budget(maxRefreshes = 2, windowMs = 60_000L)
                }
            }
        }

        val result = client.get<Unit>("https://example.test/private")

        val success = assertIs<NetworkResult.Success<Unit>>(result)
        assertEquals(2, success.attempts)
        assertEquals(listOf<String?>("Bearer old", "Bearer new"), seenAuth)
        assertEquals(1, refreshCalls)
    }

    @Test
    fun authDslRequiresBearerConfiguration() {
        val error = assertFailsWith<IllegalStateException> {
            CaterKtor {
                transport = Transport { response(HttpStatus.NoContent) }
                auth {
                    refresh {
                        refreshToken { "new" }
                    }
                }
            }
        }

        assertEquals(
            "auth { bearer { ... } } must be configured before installing auth.",
            error.message,
        )
    }

    @Test
    fun authDslRequiresBearerTokenProvider() {
        val error = assertFailsWith<IllegalStateException> {
            CaterKtor {
                transport = Transport { response(HttpStatus.NoContent) }
                auth {
                    bearer {
                    }
                }
            }
        }

        assertEquals(
            "auth { bearer { tokenProvider { ... } } } or auth { bearer(\"token\") } must be configured.",
            error.message,
        )
    }

    private fun response(status: HttpStatus): NetworkResponse =
        NetworkResponse(status = status, headers = Headers.Empty, body = byteArrayOf())
}
