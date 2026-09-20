@file:OptIn(ExperimentalCaterktor::class)

package io.github.oyedsamu.caterktor

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetworkConfigTest {

    private companion object {
        val NoopResponse = NetworkResponse(HttpStatus.OK, Headers.Empty, byteArrayOf())
    }

    private class NoopTransport : Transport {
        override suspend fun execute(request: NetworkRequest): NetworkResponse = NoopResponse
    }

    /** A factory standing in for an engine with a chosen capability set. */
    private class FakeEngine(
        override val capabilities: Set<TransportCapability>,
    ) : TransportFactory {
        var lastContext: TransportContext? = null
        val transport: Transport = NoopTransport()

        override fun create(context: TransportContext): Transport {
            lastContext = context
            return transport
        }

        override fun toString(): String = "FakeEngine"
    }

    @Test
    fun proxy_defaults_to_Default_and_requires_no_capability() {
        assertEquals(ProxySpec.Default, NetworkConfig().proxy)
        assertTrue(NetworkConfig().requiredCapabilities().isEmpty())
    }

    @Test
    fun a_configured_proxy_requires_the_Proxy_capability() {
        val config = NetworkConfig(proxy = ProxySpec.Http("http://proxy.corp:8080"))
        assertEquals(setOf(TransportCapability.Proxy), config.requiredCapabilities())
    }

    @Test
    fun network_block_is_passed_to_the_engine_factory() {
        val engine = FakeEngine(setOf(TransportCapability.Proxy))
        val spec = ProxySpec.Socks("localhost", 1080)

        CaterKtor {
            engine(engine)
            network { proxy = spec }
        }

        assertEquals(spec, engine.lastContext?.network?.proxy)
    }

    @Test
    fun engine_factory_is_used_without_a_network_block() {
        val engine = FakeEngine(setOf(TransportCapability.Proxy))

        CaterKtor { engine(engine) }

        assertEquals(NetworkConfig.Default, engine.lastContext?.network)
    }

    @Test
    fun an_engine_lacking_Proxy_support_fails_the_build() {
        val engine = FakeEngine(emptySet())

        val error = assertFailsWith<IllegalStateException> {
            CaterKtor {
                engine(engine)
                network { proxy = ProxySpec.Http("http://proxy.corp:8080") }
            }
        }

        assertContains(error.message.orEmpty(), "FakeEngine")
        assertContains(error.message.orEmpty(), "Proxy")
        assertNull(engine.lastContext, "transport must not be created when a capability is missing")
    }

    @Test
    fun network_block_without_an_engine_fails_rather_than_being_ignored() {
        val error = assertFailsWith<IllegalStateException> {
            CaterKtor {
                transport = NoopTransport()
                network { proxy = ProxySpec.Http("http://proxy.corp:8080") }
            }
        }

        assertContains(error.message.orEmpty(), "engine(...)")
    }

    @Test
    fun a_default_network_block_without_an_engine_is_allowed() {
        CaterKtor {
            transport = NoopTransport()
            network { }
        }
    }

    @Test
    fun setting_both_engine_and_transport_fails() {
        val engine = FakeEngine(setOf(TransportCapability.Proxy))

        val error = assertFailsWith<IllegalStateException> {
            CaterKtor {
                engine(engine)
                transport = NoopTransport()
            }
        }

        assertContains(error.message.orEmpty(), "not both")
    }

    @Test
    fun proxy_specs_reject_malformed_input() {
        assertFailsWith<IllegalArgumentException> { ProxySpec.Http("  ") }
        assertFailsWith<IllegalArgumentException> { ProxySpec.Socks("", 1080) }
        assertFailsWith<IllegalArgumentException> { ProxySpec.Socks("localhost", 0) }
        assertFailsWith<IllegalArgumentException> { ProxySpec.Socks("localhost", 65536) }
    }
}
