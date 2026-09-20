@file:OptIn(ExperimentalCaterktor::class)

package io.github.oyedsamu.caterktor.engine.okhttp

import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import io.github.oyedsamu.caterktor.TransportCapability
import kotlin.test.Test
import kotlin.test.assertEquals

class OkHttpCapabilitiesTest {

    @Test
    fun okhttp_supports_proxy_and_custom_dns() {
        assertEquals(
            setOf(TransportCapability.Proxy, TransportCapability.CustomDns),
            OkHttp.capabilities,
        )
    }
}
