package io.github.oyedsamu.caterktor.serialization.json

import io.github.oyedsamu.caterktor.ContentNegotiationRegistry
import io.github.oyedsamu.caterktor.ExperimentalCaterktor

/**
 * Register the kotlinx.serialization JSON converter for content negotiation.
 */
@ExperimentalCaterktor
public fun ContentNegotiationRegistry.Builder.json(
    converter: KotlinxJsonConverter = KotlinxJsonConverter(),
    contentType: String = "application/json",
    quality: Double = 1.0,
): ContentNegotiationRegistry.Builder =
    register(contentType, converter, quality)
