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

public data class ProgressSampleRun(
    public val uploadedBytes: Long,
    public val uploadTotalBytes: Long?,
    public val downloadedBytes: Long,
    public val downloadTotalBytes: Long?,
    public val responseBytes: Int,
)
