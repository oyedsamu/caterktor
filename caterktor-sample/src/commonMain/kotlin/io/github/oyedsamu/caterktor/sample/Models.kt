package io.github.oyedsamu.caterktor.sample

import kotlinx.serialization.Serializable

@Serializable
public data class User(
    public val id: String,
    public val name: String,
)

public data class SampleRun(
    public val user: User,
    public val refreshCalls: Int,
    public val requestCount: Int,
    public val authorizationHeaders: List<String?>,
    public val logs: List<String>,
)
