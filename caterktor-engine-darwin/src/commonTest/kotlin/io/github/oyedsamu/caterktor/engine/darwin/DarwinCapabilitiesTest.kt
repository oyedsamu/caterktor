@file:OptIn(ExperimentalCaterktor::class)

package io.github.oyedsamu.caterktor.engine.darwin

import io.github.oyedsamu.caterktor.CaterKtor
import io.github.oyedsamu.caterktor.DnsResolver
import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import io.github.oyedsamu.caterktor.ProxySpec
import io.github.oyedsamu.caterktor.TransportCapability
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DarwinCapabilitiesTest {

    @Test
    fun darwin_supports_proxy_but_not_custom_dns() {
        assertEquals(setOf(TransportCapability.Proxy), Darwin.capabilities)
    }

    @Test
    fun configuring_a_resolver_on_darwin_fails_the_build() {
        val error = assertFailsWith<IllegalStateException> {
            CaterKtor {
                engine(Darwin)
                network { dns = DnsResolver { listOf("127.0.0.1") } }
            }
        }

        assertContains(error.message.orEmpty(), "CustomDns")
        assertContains(error.message.orEmpty(), "Darwin")
    }

    @Test
    fun a_proxy_on_darwin_is_accepted() {
        CaterKtor {
            engine(Darwin)
            network { proxy = ProxySpec.Socks("localhost", 1080) }
        }
    }
}
