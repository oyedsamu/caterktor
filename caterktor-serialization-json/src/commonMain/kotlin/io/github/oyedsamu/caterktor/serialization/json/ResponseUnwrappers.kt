package io.github.oyedsamu.caterktor.serialization.json

import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import io.github.oyedsamu.caterktor.NetworkResponse
import io.github.oyedsamu.caterktor.ResponseUnwrapper
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * A [ResponseUnwrapper] that extracts a single named field from the top-level JSON object
 * and passes its value — re-encoded as bytes — to the [io.github.oyedsamu.caterktor.BodyConverter].
 *
 * Typical use case: a server wraps every response in `{ "data": { ... } }` and you want the
 * converter to see only the inner object:
 *
 * ```kotlin
 * DataFieldUnwrapper(field = "data", json = myJson)
 * ```
 *
 * The same [Json] instance is used for both parsing the envelope and re-encoding the extracted
 * field. Supply the same instance you gave to [KotlinxJsonConverter] so that configuration
 * (e.g. `ignoreUnknownKeys`, custom serializers) is applied consistently.
 *
 * Throws [SerializationException] if the response body is not a JSON object or if [field] is
 * absent from the top-level object; the framework maps this to
 * [io.github.oyedsamu.caterktor.NetworkError.Serialization].
 *
 * @property field The name of the top-level JSON key whose value should be forwarded to the decoder.
 * @property json  The [Json] instance used for both envelope parsing and field re-encoding.
 */
@ExperimentalCaterktor
public class DataFieldUnwrapper(
    public val field: String,
    public val json: Json = Json,
) : ResponseUnwrapper {

    public override fun unwrap(raw: ByteArray, contentType: String?, response: NetworkResponse): ByteArray {
        val root = try {
            json.decodeFromString(JsonElement.serializer(), raw.decodeToString())
        } catch (e: SerializationException) {
            throw SerializationException(
                "DataFieldUnwrapper: failed to parse response as JSON. field=\"$field\"",
                e,
            )
        }
        if (root !is JsonObject) {
            throw SerializationException(
                "DataFieldUnwrapper: expected a JSON object at the root but got ${root::class.simpleName}. field=\"$field\"",
            )
        }
        val element = root[field]
            ?: throw SerializationException(
                "DataFieldUnwrapper: field \"$field\" not found in root JSON object. " +
                    "Available keys: ${root.keys}",
            )
        return json.encodeToString(JsonElement.serializer(), element).encodeToByteArray()
    }
}

/**
 * A [ResponseUnwrapper] that extracts an items array from a paged JSON response and passes
 * it — re-encoded as bytes — to the [io.github.oyedsamu.caterktor.BodyConverter].
 *
 * This unwrapper is useful when your endpoint returns a paged envelope such as:
 *
 * ```json
 * { "items": [...], "page": 1, "totalPages": 5 }
 * ```
 *
 * and your decoder is configured to handle only the items list (e.g. `List<MyItem>`).
 *
 * If you also need pagination metadata (page number, total count, etc.), decode the full
 * response as a `PagedResponse<T>` user-defined data class instead of using this unwrapper.
 *
 * Throws [SerializationException] if the response body is not a JSON object or if [itemsField]
 * is absent from the top-level object; the framework maps this to
 * [io.github.oyedsamu.caterktor.NetworkError.Serialization].
 *
 * @property itemsField The name of the top-level JSON key that holds the items array.
 *                      Defaults to `"items"`.
 * @property json       The [Json] instance used for both envelope parsing and array re-encoding.
 *                      Supply the same instance you gave to [KotlinxJsonConverter].
 */
@ExperimentalCaterktor
public class PagedUnwrapper(
    public val itemsField: String = "items",
    public val json: Json = Json,
) : ResponseUnwrapper {

    public override fun unwrap(raw: ByteArray, contentType: String?, response: NetworkResponse): ByteArray {
        val root = try {
            json.decodeFromString(JsonElement.serializer(), raw.decodeToString())
        } catch (e: SerializationException) {
            throw SerializationException(
                "PagedUnwrapper: failed to parse response as JSON. itemsField=\"$itemsField\"",
                e,
            )
        }
        if (root !is JsonObject) {
            throw SerializationException(
                "PagedUnwrapper: expected a JSON object at the root but got ${root::class.simpleName}. " +
                    "itemsField=\"$itemsField\"",
            )
        }
        val element = root[itemsField]
            ?: throw SerializationException(
                "PagedUnwrapper: field \"$itemsField\" not found in root JSON object. " +
                    "Available keys: ${root.keys}",
            )
        return json.encodeToString(JsonElement.serializer(), element).encodeToByteArray()
    }
}
