@file:OptIn(ExperimentalCaterktor::class)

package io.github.oyedsamu.caterktor.engine.cio

import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import io.github.oyedsamu.caterktor.TransportCapability
import kotlin.test.Test
import kotlin.test.assertEquals

class CioCapabilitiesTest {

    @Test
    fun cio_supports_proxy_and_custom_dns() {
        assertEquals(
            setOf(TransportCapability.Proxy, TransportCapability.CustomDns),
            Cio.capabilities,
        )
    }
}
