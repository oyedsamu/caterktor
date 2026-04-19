package io.github.oyedsamu.caterktor

/** Returns the body if this is a [NetworkResult.Success], or `null` otherwise. */
public fun <T> NetworkResult<T>.getOrNull(): T? = when (this) {
    is NetworkResult.Success -> body
    is NetworkResult.Failure -> null
}

/** Returns the error if this is a [NetworkResult.Failure], or `null` otherwise. */
public fun <T> NetworkResult<T>.errorOrNull(): NetworkError? = when (this) {
    is NetworkResult.Success -> null
    is NetworkResult.Failure -> error
}

/**
 * Returns the body if this is a [NetworkResult.Success], or throws a
 * [NetworkResultException] wrapping the [NetworkError] otherwise.
 *
 * Prefer [fold] or an explicit `when` branch in production code where callers
 * handle errors. [getOrThrow] is intended for tests and one-off scripts.
 */
public fun <T> NetworkResult<T>.getOrThrow(): T = when (this) {
    is NetworkResult.Success -> body
    is NetworkResult.Failure -> throw NetworkResultException(error)
}

/**
 * Transforms the body if this is a [NetworkResult.Success], leaving
 * [NetworkResult.Failure] unchanged. All [NetworkResult.Success] metadata
 * (`status`, `headers`, `durationMs`, `attempts`, `requestId`) is preserved.
 */
public fun <T, R> NetworkResult<T>.map(transform: (T) -> R): NetworkResult<R> = when (this) {
    is NetworkResult.Success -> NetworkResult.Success(
        body = transform(body),
        status = status,
        headers = headers,
        durationMs = durationMs,
        attempts = attempts,
        requestId = requestId,
    )
    is NetworkResult.Failure -> this
}

/**
 * Executes [onSuccess] if this is a [NetworkResult.Success], [onFailure] if this
 * is a [NetworkResult.Failure], and returns the result of the invoked branch.
 */
public inline fun <T, R> NetworkResult<T>.fold(
    onSuccess: (NetworkResult.Success<T>) -> R,
    onFailure: (NetworkResult.Failure) -> R,
): R = when (this) {
    is NetworkResult.Success -> onSuccess(this)
    is NetworkResult.Failure -> onFailure(this)
}

/** Invokes [action] with the success value and returns `this` unchanged. */
public inline fun <T> NetworkResult<T>.onSuccess(
    action: (NetworkResult.Success<T>) -> Unit,
): NetworkResult<T> {
    if (this is NetworkResult.Success) action(this)
    return this
}

/** Invokes [action] with the failure and returns `this` unchanged. */
public inline fun <T> NetworkResult<T>.onFailure(
    action: (NetworkResult.Failure) -> Unit,
): NetworkResult<T> {
    if (this is NetworkResult.Failure) action(this)
    return this
}

/**
 * Converts to a Kotlin [Result]. A [NetworkResult.Success] becomes
 * [Result.success] carrying the body; a [NetworkResult.Failure] becomes
 * [Result.failure] wrapping a [NetworkResultException].
 *
 * Use this only at integration boundaries that require a Kotlin [Result].
 * Prefer [NetworkResult] throughout your own code — it carries richer metadata.
 */
public fun <T> NetworkResult<T>.toKotlinResult(): Result<T> = when (this) {
    is NetworkResult.Success -> Result.success(body)
    is NetworkResult.Failure -> Result.failure(NetworkResultException(error))
}
