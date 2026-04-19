package io.github.oyedsamu.caterktor

/**
 * The result of a network operation. Every call through the CaterKtor
 * `NetworkClient` returns a [NetworkResult].
 *
 * Branch on the result:
 * ```
 * when (val result = client.get<User>("/users/me")) {
 *     is NetworkResult.Success -> showUser(result.body)
 *     is NetworkResult.Failure -> handleError(result.error)
 * }
 * ```
 *
 * ## Cancellation
 * Coroutine cancellation is **not** represented as a [NetworkResult.Failure].
 * If the coroutine is cancelled, `CancellationException` propagates to the
 * caller directly — it is never caught, never wrapped, never turned into a
 * result. See [NetworkError] for why.
 */
public sealed interface NetworkResult<out T> {

    /**
     * The operation completed successfully.
     *
     * @param body The decoded response body.
     * @param status The HTTP status code.
     * @param headers The response headers.
     * @param durationMs Wall-clock duration of the operation in milliseconds,
     *   including all retry attempts and any auth-refresh wait.
     * @param attempts Total number of attempts made (`1` = no retries).
     * @param requestId A correlation ID for this logical request. Populated by
     *   the pipeline; useful for log correlation.
     */
    public data class Success<out T>(
        public val body: T,
        public val status: HttpStatus,
        public val headers: Headers,
        public val durationMs: Long,
        public val attempts: Int,
        public val requestId: String,
    ) : NetworkResult<T>

    /**
     * The operation failed.
     *
     * @param error The typed failure reason.
     * @param durationMs Wall-clock duration up to the point of failure,
     *   including retry attempts.
     * @param attempts Total number of attempts made before giving up.
     * @param requestId The correlation ID for this logical request.
     */
    public data class Failure(
        public val error: NetworkError,
        public val durationMs: Long,
        public val attempts: Int,
        public val requestId: String,
    ) : NetworkResult<Nothing>
}
