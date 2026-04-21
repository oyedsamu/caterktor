package io.github.oyedsamu.caterktor

/**
 * Marker for request body types. Concrete implementations are provided in
 * caterktor-core; the full hierarchy (Text, Source, Multipart, Form) lands
 * alongside the streaming body model in Wave B1.
 *
 * At this stage the only concrete variant is [Bytes] — an already-encoded
 * byte array, emitted by the typed call helpers after a [BodyConverter]
 * has run.
 */
public sealed interface RequestBody {

    /**
     * A pre-encoded byte-array body.
     *
     * Created automatically by the typed call helpers when a [BodyConverter]
     * encodes the request value. Do not construct manually unless you have
     * already-encoded bytes and want to bypass converter selection.
     *
     * @property bytes The encoded content.
     * @property contentType The MIME type, e.g. `"application/json"`.
     */
    public class Bytes(
        public val bytes: ByteArray,
        public val contentType: String,
    ) : RequestBody
}
