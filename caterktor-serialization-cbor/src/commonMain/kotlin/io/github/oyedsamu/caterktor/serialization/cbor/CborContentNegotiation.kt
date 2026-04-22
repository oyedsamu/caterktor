package io.github.oyedsamu.caterktor.serialization.cbor

import io.github.oyedsamu.caterktor.ContentNegotiationRegistry
import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import kotlinx.serialization.ExperimentalSerializationApi

/**
 * Register the kotlinx.serialization CBOR converter for content negotiation.
 */
@ExperimentalCaterktor
@OptIn(ExperimentalSerializationApi::class)
public fun ContentNegotiationRegistry.Builder.cbor(
    converter: KotlinxCborConverter = KotlinxCborConverter(),
    contentType: String = "application/cbor",
    quality: Double = 1.0,
): ContentNegotiationRegistry.Builder =
    register(contentType, converter, quality)
