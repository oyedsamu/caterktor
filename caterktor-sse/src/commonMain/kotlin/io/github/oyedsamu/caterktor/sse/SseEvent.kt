package io.github.oyedsamu.caterktor.sse

import io.github.oyedsamu.caterktor.ExperimentalCaterktor

/**
 * Represents a single Server-Sent Event received from the server.
 *
 * @property data The data field of the event.
 * @property event String identifying the type of event, or null if not specified.
 * @property id The event ID, or null if not specified.
 * @property retry Reconnection time in milliseconds, or null if not specified.
 * @property comments Comment lines starting with a ':' character, or null if not present.
 */
@ExperimentalCaterktor
public data class SseEvent(
    public val data: String,
    public val event: String? = null,
    public val id: String? = null,
    public val retry: Long? = null,
    public val comments: String? = null,
)
