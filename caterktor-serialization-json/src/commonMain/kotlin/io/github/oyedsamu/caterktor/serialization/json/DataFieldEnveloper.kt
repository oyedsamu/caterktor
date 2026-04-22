package io.github.oyedsamu.caterktor.serialization.json

import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import io.github.oyedsamu.caterktor.RequestBody
import io.github.oyedsamu.caterktor.RequestEnveloper
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObject.Companion.serializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A [RequestEnveloper] that wraps the encoded payload inside a JSON object keyed by [field].
 *
 * For example, if [field] is `"data"` and the encoded bytes represent `{"id":1}`, the resulting
 * body sent to the server will be `{"data":{"id":1}}`.
 *
 * @param field The JSON key under which the original payload is nested in the envelope object.
 * @param json  The [Json] instance used for both parsing the incoming bytes and re-encoding the
 *              envelope. This should match the [Json] instance configured in [KotlinxJsonConverter]
 *              so that serialization settings (lenient mode, pretty print, etc.) remain consistent
 *              across the encode and envelop steps.
 *
 * @throws SerializationException if the bytes passed to [envelop] are not valid JSON.
 */
@ExperimentalCaterktor
public class DataFieldEnveloper(
    public val field: String,
    public val json: Json = Json,
) : RequestEnveloper {

    override fun envelop(encoded: ByteArray, contentType: String): RequestBody {
        val parsedElement: JsonElement = json.parseToJsonElement(encoded.decodeToString())
        val envelope: JsonObject = buildJsonObject { put(field, parsedElement) }
        val bytes: ByteArray = json.encodeToString(JsonObject.serializer(), envelope).encodeToByteArray()
        return RequestBody.Bytes(bytes, contentType)
    }
}
