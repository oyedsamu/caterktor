package io.github.oyedsamu.caterktor

/**
 * Marker for request body types. Concrete implementations are provided in
 * caterktor-core once the streaming body model is finalised (B1).
 *
 * The full hierarchy ([Json][io.github.oyedsamu.caterktor.RequestBody], Text, Bytes,
 * Source, Multipart, Form) is defined in §6.6 of the PRD and will be added in the
 * B-stream once the streaming primitives land.
 */
public sealed interface RequestBody
