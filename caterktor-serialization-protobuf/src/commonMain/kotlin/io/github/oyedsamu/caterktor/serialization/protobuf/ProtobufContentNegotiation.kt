package io.github.oyedsamu.caterktor.serialization.protobuf

import io.github.oyedsamu.caterktor.ContentNegotiationRegistry
import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import kotlinx.serialization.ExperimentalSerializationApi

/**
 * Register the kotlinx.serialization Protobuf converter for content negotiation.
 */
@ExperimentalCaterktor
@OptIn(ExperimentalSerializationApi::class)
public fun ContentNegotiationRegistry.Builder.protobuf(
    converter: KotlinxProtobufConverter = KotlinxProtobufConverter(),
    contentTypes: List<String> = listOf("application/x-protobuf", "application/protobuf"),
    quality: Double = 1.0,
): ContentNegotiationRegistry.Builder = apply {
    require(contentTypes.isNotEmpty()) { "contentTypes must not be empty" }
    contentTypes.forEach { register(it, converter, quality) }
}
