# CaterKtor

The application networking layer for Kotlin Multiplatform.

![Kotlin Multiplatform](https://img.shields.io/badge/Kotlin-Multiplatform-7F52FF?logo=kotlin)
![License](https://img.shields.io/badge/License-Apache%202.0-blue)
![Version](https://img.shields.io/badge/version-0.1.0--SNAPSHOT-orange)

## At a glance

CaterKtor is an opinionated KMP HTTP client built on top of Ktor. It adds the layer that Ktor
deliberately leaves to you: a typed result model (`NetworkResult<T>`), an ordered and introspectable
interceptor pipeline, auth token injection with single-flight 401 refresh, retry with exponential
backoff and jitter, a circuit breaker, content negotiation, structured logging with multi-axis
redaction, and testing utilities that keep repository tests under 15 lines. CaterKtor is not a Ktor
replacement — it depends on Ktor for transport and surfaces Ktor's engines directly.

## Installation

Add one or more artifacts to your Gradle dependencies. Every published module lives under
`io.github.oyedsamu`.

```kotlin
// build.gradle.kts
dependencies {
    // Required: core contracts, interceptor pipeline, NetworkClient, CaterKtor {} DSL
    implementation("io.github.oyedsamu:caterktor-core:0.1.0-SNAPSHOT")

    // Required: KtorTransport base, shared by the engine modules below
    implementation("io.github.oyedsamu:caterktor-ktor:0.1.0-SNAPSHOT")

    // Pick the engine(s) that match your targets
    implementation("io.github.oyedsamu:caterktor-engine-okhttp:0.1.0-SNAPSHOT")  // Android / JVM
    implementation("io.github.oyedsamu:caterktor-engine-darwin:0.1.0-SNAPSHOT")  // iOS / macOS
    implementation("io.github.oyedsamu:caterktor-engine-cio:0.1.0-SNAPSHOT")     // Linux / JVM

    // Optional features
    implementation("io.github.oyedsamu:caterktor-auth:0.1.0-SNAPSHOT")                   // BearerAuthInterceptor, AuthRefreshInterceptor
    implementation("io.github.oyedsamu:caterktor-serialization-json:0.1.0-SNAPSHOT")     // KotlinxJsonConverter
    implementation("io.github.oyedsamu:caterktor-serialization-protobuf:0.1.0-SNAPSHOT") // KotlinxProtobufConverter
    implementation("io.github.oyedsamu:caterktor-serialization-cbor:0.1.0-SNAPSHOT")     // KotlinxCborConverter
    implementation("io.github.oyedsamu:caterktor-logging:0.1.0-SNAPSHOT")                // LoggerInterceptor, RedactionEngine
    implementation("io.github.oyedsamu:caterktor-testing:0.1.0-SNAPSHOT")                // FakeNetworkClient, CaterktorTestServer
}
```

## Quick start

```kotlin
@OptIn(ExperimentalCaterktor::class)
val client = CaterKtor {
    transport = OkHttpTransport()
    baseUrl = "https://api.example.com"
    addConverter(KotlinxJsonConverter())
    auth {
        bearer { tokenProvider { tokenStore.accessToken() } }
        refresh { refreshToken { tokenStore.refreshAccessToken() } }
    }
    addInterceptor(RetryInterceptor())
}

val result: NetworkResult<User> = client.get("/users/me")
when (result) {
    is NetworkResult.Success -> println(result.body)
    is NetworkResult.Failure -> println(result.error)
}
```

## NetworkResult

Every call returns a `NetworkResult<T>`. There are exactly two variants.

```kotlin
sealed interface NetworkResult<out T> {
    data class Success<T>(
        val body: T,
        val status: HttpStatus,
        val headers: Headers,
        val durationMs: Long,   // wall-clock ms including all retries and refresh waits
        val attempts: Int,      // 1 = no retries
        val requestId: String,  // correlation ID for log joining
    ) : NetworkResult<T>

    data class Failure(
        val error: NetworkError,
        val durationMs: Long,
        val attempts: Int,
        val requestId: String,
    ) : NetworkResult<Nothing>
}
```

`NetworkError` is a sealed interface with typed variants:

```kotlin
sealed interface NetworkError {
    // Server returned 4xx / 5xx
    data class Http(val status: HttpStatus, val headers: Headers, val body: ErrorBody) : NetworkError
    // TCP / DNS / TLS could not be established; see ConnectionFailureKind (Dns, Refused, Unreachable, TlsHandshake)
    data class ConnectionFailed(val kind: ConnectionFailureKind) : NetworkError
    // A time limit was exceeded; see TimeoutKind (Connect, Socket, Request, Deadline)
    data class Timeout(val kind: TimeoutKind) : NetworkError
    // Encode or decode failed; see SerializationPhase (Encoding, Decoding)
    data class Serialization(val phase: SerializationPhase, val rawBody: RawBody?) : NetworkError
    // HTTP protocol violation (malformed response, too many redirects)
    data class Protocol(val message: String) : NetworkError
    // A circuit breaker rejected the call before it reached the transport
    data class CircuitOpen(val name: String, val state: CircuitBreakerState) : NetworkError
    // Catch-all; cause is guaranteed non-null
    data class Unknown(override val cause: Throwable) : NetworkError
}
```

`CancellationException` is never wrapped as a `Failure`. It propagates from the pipeline directly
to the caller's coroutine scope. If you need to model cancellation as a result, catch it yourself
at the scope you own.

## Auth

```kotlin
@OptIn(ExperimentalCaterktor::class)
val client = CaterKtor {
    transport = OkHttpTransport()
    auth {
        bearer {
            tokenProvider { tokenStore.accessToken() }
        }
        refresh {
            refreshToken { tokenStore.refreshAccessToken() }
            budget(maxRefreshes = 1, windowMs = 60_000L) // default
            onRefreshFailed { cause -> /* re-authenticate */ }
        }
    }
}
```

`auth { bearer { } }` installs `BearerAuthInterceptor`. Adding a `refresh { }` block upgrades to
`AuthRefreshInterceptor`, which performs single-flight refresh: concurrent 401 responses join the
same refresh job and each waiter retries with the shared token without triggering a stampede.

To prevent the refresh call from re-entering auth, tag it with `CaterKtorKeys.SKIP_AUTH`:

```kotlin
client.get<Token>(
    url = "auth/token",
    attributes = Attributes { put(CaterKtorKeys.SKIP_AUTH, true) },
)
```

## Retry

```kotlin
@OptIn(ExperimentalCaterktor::class)
val client = CaterKtor {
    transport = OkHttpTransport()
    addInterceptor(RetryInterceptor(maxAttempts = 3))
}
```

`RetryInterceptor` uses `ExponentialBackoffPolicy` by default:

- Retries on `NetworkError.Timeout`, `NetworkError.ConnectionFailed`, and HTTP 502 / 503 / 504.
- Full jitter (AWS-style) is on by default — delay is uniformly random in `[0, cap]`.
- `Retry-After` headers (delay-seconds or IMF-fixdate) override the computed delay.
- Only idempotent methods (GET, HEAD, DELETE, PUT, OPTIONS) are retried by default.
  Set `retryNonIdempotent = true` and include an `Idempotency-Key` header for POST / PATCH.

Supply a custom policy for any other strategy:

```kotlin
RetryInterceptor(
    maxAttempts = 5,
    policy = ExponentialBackoffPolicy(
        baseDelayMs = 200L,
        maxDelayMs = 10_000L,
        jitterFactor = 1.0,
    ),
)
```

## Circuit breaker

```kotlin
@OptIn(ExperimentalCaterktor::class)
val client = CaterKtor {
    transport = OkHttpTransport()
    addInterceptor(CircuitBreaker(failureThreshold = 5, openDurationMs = 30_000))
}
```

The breaker starts `Closed`. After `failureThreshold` consecutive failures it moves to `Open` and
rejects calls with `NetworkError.CircuitOpen` until `openDurationMs` has elapsed. The next allowed
call moves it to `HalfOpen`; success closes the breaker, failure reopens it. State transitions are
emitted as `NetworkEvent.CircuitBreakerTransition`.

## Logging

```kotlin
@OptIn(ExperimentalCaterktor::class)
val client = CaterKtor {
    transport = OkHttpTransport()
    // Level options: None, Basic, Headers, Body
    addInterceptor(LoggerInterceptor(level = LogLevel.Headers) { println(it) })
}
```

With custom redaction:

```kotlin
addInterceptor(
    LoggerInterceptor(
        level = LogLevel.Body,
        redaction = RedactionEngine(
            jsonBodyFields = setOf("password", "token"),
        ),
        logger = { println(it) },
    )
)
```

`RedactionEngine` redacts on four axes: headers (default: `Authorization`, `Cookie`, `Set-Cookie`,
`Proxy-Authorization`, `X-Auth-Token`, `X-Api-Key`), query parameters, JSON body fields (JSONPath-lite
selectors — bare field name, `$.root.field`, or `$..anywhere`), and free-form regex patterns.
Redaction is enabled whenever logging is enabled. You opt out per field, never globally.

## Content negotiation

```kotlin
@OptIn(ExperimentalCaterktor::class)
val client = CaterKtor {
    transport = OkHttpTransport()
    contentNegotiation {
        register("application/json", KotlinxJsonConverter())
        register("application/x-protobuf", KotlinxProtobufConverter(), quality = 0.9)
    }
}
```

Registered converters drive three things: the `Accept` header constructed for typed calls, dispatch
from the response `Content-Type`, and request body encoding. Use `addConverter()` instead if you
only need decoding without `Accept` negotiation.

## Testing

**Unit tests — `FakeNetworkClient`:** queue scripted responses against a real `NetworkClient` backed
by an in-memory transport. No I/O, no sockets.

```kotlin
@OptIn(ExperimentalCaterktor::class)
val fake = FakeNetworkClient()
fake.enqueue(
    testResponse(status = HttpStatus.OK, body = """{"name":"Ada"}""".encodeToByteArray())
)
val response = fake.execute(request)
assertEquals(HttpStatus.OK, response.status)
```

Helper builders from `caterktor-testing`:

```kotlin
testResponse(status = HttpStatus.OK, body = byteArrayOf())  // blank 200
jsonResponse("""{"name":"Ada"}""")                           // 200 with Content-Type: application/json
httpFailure(HttpStatus.Unauthorized)                         // NetworkError.Http convenience
```

**Unit tests — `CaterktorTestServer`:** route-based matching backed by an in-memory transport.
Use this when you want base-URL resolution, route scripts, and request capture without opening sockets.

```kotlin
@OptIn(ExperimentalCaterktor::class)
val server = CaterktorTestServer()
server.route(HttpMethod.GET, "/users/me", jsonResponse("""{"name":"Ada"}"""))
val client = server.client {
    addConverter(KotlinxJsonConverter())
}
val result = client.get<User>("/users/me")
assertTrue(result is NetworkResult.Success)
```

## Events / observability

`NetworkClient.events` is a `SharedFlow<NetworkEvent>`. Collect it to receive structured,
typed events for every request. Events are emitted non-blocking with `tryEmit`; a slow collector
never stalls the pipeline.

```kotlin
@OptIn(ExperimentalCaterktor::class)
client.events.collect { event ->
    when (event) {
        is NetworkEvent.CallSuccess -> log(event.requestId, event.durationMs)
        is NetworkEvent.CallFailure -> log(event.requestId, event.error)
        is NetworkEvent.CircuitBreakerTransition -> log("${event.name}: ${event.from} -> ${event.to}")
        else -> {}
    }
}
```

`NetworkEvent.requestId` links every event that belongs to the same logical request, including
`CallStart`, `ResponseReceived`, and `CallSuccess` / `CallFailure`.

## Pipeline introspection

```kotlin
println(client.describePipeline())
// [0] DefaultHeadersInterceptor
// [1] AuthRefreshInterceptor
// [2] RetryInterceptor
// [3] LoggerInterceptor
// [4] Transport(KtorTransport)
```

Interceptors run in registration order (first registered, first to see the request). The last
entry is always the terminal transport. This output is the contract — what you read is what runs.

## API stability

Binary compatibility is enforced by `kotlinx-binary-compatibility-validator` from `0.1.0`.
`@ExperimentalCaterktor` marks surfaces that may change between minor versions — annotate
call sites with `@OptIn(ExperimentalCaterktor::class)` to use them. Stable surfaces follow the
deprecation lifecycle: `WARNING` for one minor, `ERROR` for one minor, then removal.

## Known limitations

- **Streaming decode:** response bodies are buffered into a `ByteArray` before typed decode.
  The default cap is 10 MiB, configurable via `CaterKtorBuilder.maxBodyDecodeBytes()`. Bodies
  without a known `Content-Length` (chunked transfer) are not guarded by this limit.
- **OTel adapter (`caterktor-otel`) and Ktorfit adapter (`caterktor-ktorfit`)** are not yet released.
  `caterktor-otel` is scaffolded; `caterktor-ktorfit` follows in a later wave.
- The `@ExperimentalCaterktor` opt-in is required on all public API until stabilized at `0.3.0`.

## License

```
Copyright 2024 Samuel Oyedele

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
