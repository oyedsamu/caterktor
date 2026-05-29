@file:OptIn(ExperimentalCaterktor::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.oyedsamu.caterktor

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NetworkEventProgressTest {

    @Test
    fun execute_emitsUploadProgressForSourceBody() = runTest {
        val payload = "stream me".encodeToByteArray()
        val client = CaterKtor {
            transport = Transport { request ->
                request.body?.bytes()
                NetworkResponse(HttpStatus.NoContent, Headers.Empty, ByteArray(0))
            }
        }
        val events = mutableListOf<NetworkEvent>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            client.events.take(1).toList(events)
        }

        client.execute(
            NetworkRequest(
                method = HttpMethod.POST,
                url = "https://example.test/upload",
                body = RequestBody.Source(
                    sourceFactory = { Buffer().also { it.write(payload) } },
                    contentType = "application/octet-stream",
                    contentLength = payload.size.toLong(),
                ),
            ),
        )
        collector.join()

        val progress = assertIs<NetworkEvent.UploadProgress>(events.single())
        assertEquals(payload.size.toLong(), progress.bytesSent)
        assertEquals(payload.size.toLong(), progress.totalBytes)
    }

    @Test
    fun execute_emitsUploadProgressForMultipartSourcePart() = runTest {
        val payload = "multipart file".encodeToByteArray()
        val client = CaterKtor {
            transport = Transport { request ->
                request.body?.bytes()
                NetworkResponse(HttpStatus.NoContent, Headers.Empty, ByteArray(0))
            }
        }
        val events = mutableListOf<NetworkEvent>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            client.events.take(1).toList(events)
        }

        client.execute(
            NetworkRequest(
                method = HttpMethod.POST,
                url = "https://example.test/upload",
                body = RequestBody.Multipart(
                    parts = listOf(
                        RequestBody.Multipart.Part.formData(
                            name = "file",
                            filename = "file.txt",
                            body = RequestBody.Source(
                                sourceFactory = { Buffer().also { it.write(payload) } },
                                contentType = "text/plain",
                                contentLength = payload.size.toLong(),
                            ),
                        ),
                    ),
                    boundary = "test-boundary",
                ),
            ),
        )
        collector.join()

        val progress = assertIs<NetworkEvent.UploadProgress>(events.single())
        assertEquals(payload.size.toLong(), progress.bytesSent)
        assertEquals(payload.size.toLong(), progress.totalBytes)
    }

    @Test
    fun execute_emitsDownloadProgressWhenSourceResponseIsConsumed() = runTest {
        val payload = "download me".encodeToByteArray()
        val client = CaterKtor {
            transport = Transport {
                NetworkResponse(
                    status = HttpStatus.OK,
                    headers = Headers.Empty,
                    body = ResponseBody.Source(
                        sourceFactory = { Buffer().also { it.write(payload) } },
                        contentType = "application/octet-stream",
                        contentLength = payload.size.toLong(),
                    ),
                )
            }
        }
        val events = mutableListOf<NetworkEvent>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            client.events.take(1).toList(events)
        }

        val response = client.execute(NetworkRequest(HttpMethod.GET, "https://example.test/download"))
        assertTrue(response.body is ResponseBody.Source)
        response.body.bytes()
        collector.join()

        val progress = assertIs<NetworkEvent.DownloadProgress>(events.single())
        assertEquals(payload.size.toLong(), progress.bytesRead)
        assertEquals(payload.size.toLong(), progress.totalBytes)
    }
}
