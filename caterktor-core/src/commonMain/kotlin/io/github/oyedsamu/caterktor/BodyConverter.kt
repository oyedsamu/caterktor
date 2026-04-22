package io.github.oyedsamu.caterktor

import kotlin.reflect.KType

/**
 * Converts between typed values and raw bytes for the request/response body.
 *
 * [BodyConverter] instances are registered on [CaterKtorBuilder] in order.
 * CaterKtor selects the first converter that returns `true` from [supports]
 * for the requested content-type. If no converter matches, the call fails
 * with [NetworkError.Serialization].
 *
 * Implementations must be stateless or thread-safe; the same instance is
 * called concurrently for every in-flight request.
 *
 * ## Buffering
 *
 * The current converter contract is byte-based. [decode] receives a fully
 * buffered [RawBody], and [NetworkClient] enforces
 * [CaterKtorBuilder.maxBodyDecodeBytes] before materialising response bytes for
 * typed decoding. Streaming consumers should use [NetworkResponse.body]
 * directly until a streaming converter API is introduced.
 *
 * ## Content-type matching
 *
 * [supports] receives the bare media-type only, e.g. `"application/json"` — not
 * the full header value with parameters like `"; charset=utf-8"`. CaterKtor
 * strips parameters before calling [supports].
 *
 * ## Type resolution
 *
 * The [KType] arguments are always obtained via `typeOf<T>()` at the call
 * site — never constructed manually. Implementations may cast to
 * [kotlinx.serialization.serializerOrNull] or any other reflective lookup.
 */
@ExperimentalCaterktor
public interface BodyConverter {

    /**
     * Return `true` if this converter can encode/decode [contentType].
     *
     * @param contentType The bare media-type string, e.g. `"application/json"`.
     */
    public fun supports(contentType: String): Boolean

    /**
     * Serialize [value] to bytes.
     *
     * @param value The value to serialize. Never null.
     * @param type The Kotlin runtime type of [value], obtained via `typeOf<T>()`.
     * @param contentType The target content-type (already confirmed supported).
     * @return The serialized bytes.
     * @throws NetworkErrorException wrapping [NetworkError.Serialization] on failure.
     */
    public fun <T : Any> encode(value: T, type: KType, contentType: String): ByteArray

    /**
     * Deserialize [raw] bytes into a value of type [T].
     *
     * [raw] has already been fully buffered by CaterKtor and is bounded by
     * [CaterKtorBuilder.maxBodyDecodeBytes].
     *
     * @param raw The raw bytes and optional content-type from the response.
     * @param type The Kotlin runtime type to decode into, obtained via `typeOf<T>()`.
     * @return The decoded value, never null.
     * @throws NetworkErrorException wrapping [NetworkError.Serialization] on failure.
     */
    public fun <T : Any> decode(raw: RawBody, type: KType): T
}
