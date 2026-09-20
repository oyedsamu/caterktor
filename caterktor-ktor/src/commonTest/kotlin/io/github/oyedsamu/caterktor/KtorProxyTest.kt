@file:OptIn(ExperimentalCaterktor::class)

package io.github.oyedsamu.caterktor

import io.ktor.client.engine.ProxyType
import io.ktor.client.engine.type
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class KtorProxyTest {

    @Test
    fun default_leaves_the_engine_setting_untouched() {
        assertNull(ProxySpec.Default.toProxyConfig())
    }

    @Test
    fun http_spec_maps_to_an_http_proxy() {
        val config = ProxySpec.Http("http://proxy.corp:8080").toProxyConfig()
        assertEquals(ProxyType.HTTP, config?.type)
    }

    @Test
    fun an_https_proxy_url_is_rejected_on_every_platform() {
        assertFailsWith<IllegalArgumentException> { ProxySpec.Http("https://proxy.corp:8443") }
    }

    @Test
    fun socks_spec_maps_to_a_socks_proxy() {
        val config = ProxySpec.Socks("localhost", 1080).toProxyConfig()
        assertEquals(ProxyType.SOCKS, config?.type)
    }
}
