package io.github.oyedsamu.caterktor

import io.ktor.utils.io.ByteReadChannel

internal actual fun createRawSource(channel: ByteReadChannel): kotlinx.io.Source =
    throw UnsupportedOperationException(
        "Streaming download via ResponseBody.Source is not supported on Kotlin/JS. " +
            "Use KtorTransport.execute() instead.",
    )
