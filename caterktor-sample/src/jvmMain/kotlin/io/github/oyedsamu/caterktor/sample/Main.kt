package io.github.oyedsamu.caterktor.sample

import kotlinx.coroutines.runBlocking

public fun main(): Unit = runBlocking {
    val run = DocsSamples.quickStartWithAuthRefresh()
    val progress = DocsSamples.progressEventsSnippet()

    println("Loaded user: ${run.user.name} (${run.user.id})")
    println("Refresh calls: ${run.refreshCalls}")
    println("HTTP attempts recorded by sample server: ${run.requestCount}")
    println("Authorization headers: ${run.authorizationHeaders.joinToString()}")
    println("Pipeline log:")
    run.logs.forEach { line -> println("  $line") }
    println("Progress events:")
    println("  Uploaded: ${progress.uploadedBytes}/${progress.uploadTotalBytes ?: "unknown"} bytes")
    println("  Downloaded: ${progress.downloadedBytes}/${progress.downloadTotalBytes ?: "unknown"} bytes")
}
