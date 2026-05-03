package io.github.oyedsamu.caterktor

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.runBlocking
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.buffered

internal actual fun createRawSource(channel: ByteReadChannel): kotlinx.io.Source =
    ByteReadChannelRawSource(channel).buffered()

private class ByteReadChannelRawSource(
    private val channel: ByteReadChannel,
) : RawSource {
    private val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    private var closed: Boolean = false

    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
        check(!closed) { "Source is closed." }
        require(byteCount >= 0L) { "byteCount must be >= 0, was $byteCount" }
        if (byteCount == 0L) return 0L

        val read = runBlocking {
            channel.readAvailable(buffer, 0, minOf(buffer.size.toLong(), byteCount).toInt())
        }
        if (read == -1) return -1L
        sink.write(buffer, 0, read)
        return read.toLong()
    }

    override fun close() {
        if (closed) return
        closed = true
        channel.cancel()
    }

    private companion object {
        const val DEFAULT_BUFFER_SIZE: Int = 8 * 1024
    }
}
