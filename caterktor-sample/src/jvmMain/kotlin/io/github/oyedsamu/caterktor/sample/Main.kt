package io.github.oyedsamu.caterktor.sample

import kotlinx.coroutines.runBlocking

public fun main(): Unit = runBlocking {
    val run = DocsSamples.quickStartWithAuthRefresh()

    println("Loaded user: ${run.user.name} (${run.user.id})")
    println("Refresh calls: ${run.refreshCalls}")
    println("HTTP attempts recorded by sample server: ${run.requestCount}")
    println("Authorization headers: ${run.authorizationHeaders.joinToString()}")
    println("Pipeline log:")
    run.logs.forEach { line -> println("  $line") }
}
