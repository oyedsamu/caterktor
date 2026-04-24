# CaterKtor

**The application networking layer Ktor doesn't ship.**

[![Kotlin Multiplatform](https://img.shields.io/badge/Kotlin-Multiplatform-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/docs/multiplatform.html)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Version](https://img.shields.io/badge/version-0.1.0-brightgreen)](https://central.sonatype.com/search?q=io.github.oyedsamu)
[![API](https://img.shields.io/badge/API-BCV%20gated-7F52FF)](https://github.com/Kotlin/binary-compatibility-validator)

---

## Why CaterKtor instead of Ktor directly?

Ktor is an excellent HTTP engine. It handles TCP, TLS, HTTP/1.1, HTTP/2 — the transport layer — very well. What it deliberately does not handle is everything *above* the transport:

- **No typed result model.** Ktor throws exceptions. Your app catches them, pattern-matches on type, and rebuilds domain errors from exception messages. Every team does this differently.
- **No interceptor pipeline.** Ktor has a plugin system driven by coroutine phases (`sendPipeline`, `receivePipeline`). It works for transport concerns. It breaks for application concerns like auth, because you cannot control ordering across plugins.
- **No auth refresh that works under concurrency.** Ten parallel requests all get a 401. Ten parallel calls to your refresh endpoint. Your backend rotates the token on the first, the other nine get `invalid_grant`. This is the single most common networking bug in KMP apps, and Ktor leaves it to you.
- **No retry with correct defaults.** Adding retry in a Ktor plugin means writing the exponential backoff, the jitter, the `Retry-After` parsing, the idempotency guard, and the deadline propagation yourself. Most implementations get at least one of these wrong.
- **No structured error type.** A `ClientRequestException` tells you something failed. It does not tell you whether it was a DNS failure, a TLS handshake, a 401, a timeout on connect vs. read vs. a logical deadline. Branching on error kind requires exception message parsing.
- **No redaction.** Ktor's `Logging` plugin logs what it receives. If `Authorization` or `password` is in that payload, it goes to your log sink.
- **No test doubles.** Testing a repository that calls Ktor requires MockEngine, which is not part of Ktor's stable API, or a real HTTP server.

CaterKtor solves all of this — on top of Ktor's engines, without replacing them.

```
Your App
    │
    ▼
NetworkClient  (CaterKtor)
├── Auth interceptor       ← single-flight 401 refresh, budgeted
├── Retry interceptor      ← exponential backoff, jitter, Retry-After
├── Circuit breaker        ← fail fast when downstream is broken
├── Logging interceptor    ← structured, redacted
└── Transport              ← KtorTransport → OkHttp / Darwin / CIO
```

One object. Explicit ordering. Typed results. Correct concurrency semantics.

---

## Installation

### Version catalog (recommended)

```toml
# gradle/libs.versions.toml
[versions]
caterktor = "0.1.0"

[libraries]
caterktor-core              = { module = "io.github.oyedsamu:caterktor-core",              version.ref = "caterktor" }
caterktor-ktor              = { module = "io.github.oyedsamu:caterktor-ktor",              version.ref = "caterktor" }
caterktor-auth              = { module = "io.github.oyedsamu:caterktor-auth",              version.ref = "caterktor" }
caterktor-logging           = { module = "io.github.oyedsamu:caterktor-logging",           version.ref = "caterktor" }
caterktor-serialization-json = { module = "io.github.oyedsamu:caterktor-serialization-json", version.ref = "caterktor" }
caterktor-engine-okhttp     = { module = "io.github.oyedsamu:caterktor-engine-okhttp",    version.ref = "caterktor" }
caterktor-engine-darwin     = { module = "io.github.oyedsamu:caterktor-engine-darwin",    version.ref = "caterktor" }
caterktor-engine-cio        = { module = "io.github.oyedsamu:caterktor-engine-cio",       version.ref = "caterktor" }
caterktor-testing           = { module = "io.github.oyedsamu:caterktor-testing",          version.ref = "caterktor" }
```

### Kotlin Multiplatform (shared module)

```kotlin
// shared/build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.caterktor.core)
            implementation(libs.caterktor.ktor)
            implementation(libs.caterktor.auth)
            implementation(libs.caterktor.logging)
            implementation(libs.caterktor.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.caterktor.engine.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.caterktor.engine.darwin)
        }
        jvmMain.dependencies {
            implementation(libs.caterktor.engine.cio)
        }
        commonTest.dependencies {
            implementation(libs.caterktor.testing)
        }
    }
}
```

### Android-only project

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("io.github.oyedsamu:caterktor-core:0.1.0")
    implementation("io.github.oyedsamu:caterktor-ktor:0.1.0")
    implementation("io.github.oyedsamu:caterktor-engine-okhttp:0.1.0")
    implementation("io.github.oyedsamu:caterktor-auth:0.1.0")
    implementation("io.github.oyedsamu:caterktor-serialization-json:0.1.0")
    implementation("io.github.oyedsamu:caterktor-logging:0.1.0")
    testImplementation("io.github.oyedsamu:caterktor-testing:0.1.0")
}
```

### JVM server / CLI

```kotlin
dependencies {
    implementation("io.github.oyedsamu:caterktor-core:0.1.0")
    implementation("io.github.oyedsamu:caterktor-ktor:0.1.0")
    implementation("io.github.oyedsamu:caterktor-engine-cio:0.1.0")
    implementation("io.github.oyedsamu:caterktor-serialization-json:0.1.0")
    testImplementation("io.github.oyedsamu:caterktor-testing:0.1.0")
}
```

---

## Quick start

> A CI-compiled, runnable version of this exact sample lives in
> [`caterktor-sample/`](caterktor-sample/src/jvmMain/kotlin/io/github/oyedsamu/caterktor/sample/).
> Run it locally: `./gradlew :caterktor-sample:jvmRun`

```kotlin
@OptIn(ExperimentalCaterktor::class)
val client = CaterKtor {
    transport    = OkHttpTransport()
    baseUrl      = "https://api.example.com"

    addConverter(KotlinxJsonConverter())

    auth {
        bearer  { tokenProvider { tokenStore.accessToken() } }
        refresh { refreshToken  { tokenStore.refreshAccessToken() } }
    }

    addInterceptor(RetryInterceptor(maxAttempts = 3))
    addInterceptor(LoggerInterceptor(level = LogLevel.Headers) { line -> println(line) })
}

val result: NetworkResult<User> = client.get("/users/me")

when (result) {
    is NetworkResult.Success -> showProfile(result.body)
    is NetworkResult.Failure -> when (val error = result.error) {
        is NetworkError.Http        -> if (error.status == HttpStatus.Unauthorized) reLogin()
        is NetworkError.Timeout     -> showRetryPrompt()
        is NetworkError.ConnectionFailed -> showOfflineBanner()
        else                        -> reportUnexpected(error)
    }
}
```

Typed helpers also build query strings without manual concatenation:

```kotlin
val page: NetworkResult<PokemonResponse> = client.get(
    url = "pokemon",
    queryParams = QueryParameters {
        add("limit", 20)
        add("offset", 40)
        add("type", "electric")
        add("type", "flying")
    },
)
```

`queryParameters(mapOf("limit" to 20, "offset" to 40))` is available when a
map is the more natural shape. `null` values are omitted, repeated names are
preserved, and names/values are percent-encoded.

---

## Core concepts

### `NetworkResult<T>` — exactly two outcomes

Every call returns a sealed `NetworkResult<T>`. There is no third state, no thrown exception to
catch, no null to guard.

```kotlin
sealed interface NetworkResult<out T> {
    data class Success<T>(
        val body: T,
        val status: HttpStatus,
        val headers: Headers,
        val durationMs: Long,   // wall-clock ms, includes all retries and refresh waits
        val attempts: Int,      // 1 = first attempt succeeded
        val requestId: String,  // log correlation ID
    ) : NetworkResult<T>

    data class Failure(
        val error: NetworkError,
        val durationMs: Long,
        val attempts: Int,
        val requestId: String,
    ) : NetworkResult<Nothing>
}
```

Extension functions cover the common patterns without unwrapping manually:

```kotlin
val user = result.getOrThrow()                     // throws on Failure
val user = result.getOrDefault(User.ANONYMOUS)     // fallback on Failure
result.onSuccess { user -> render(user) }
       .onFailure { error -> log(error) }
val mapped = result.map { user -> UserUiModel(user) }
```

### `NetworkError` — branch on kind, not message

```kotlin
sealed interface NetworkError {
    data class Http(val status: HttpStatus, val headers: Headers, val body: ErrorBody) : NetworkError
    data class ConnectionFailed(val kind: ConnectionFailureKind) : NetworkError  // Dns, Refused, Unreachable, TlsHandshake
    data class Timeout(val kind: TimeoutKind) : NetworkError                     // Connect, Socket, Request, Deadline
    data class Serialization(val phase: SerializationPhase, val rawBody: RawBody?) : NetworkError
    data class Protocol(val message: String) : NetworkError
    data class CircuitOpen(val name: String, val state: CircuitBreakerState) : NetworkError
    data class Unknown(override val cause: Throwable) : NetworkError
}
```

`CancellationException` is **never** wrapped as a `Failure`. It propagates directly through the
pipeline to the calling coroutine. If coroutine cancellation can reach your call site, let it.

### The interceptor pipeline

Interceptors run in the order they are added. The terminal transport is always last.

```kotlin
@OptIn(ExperimentalCaterktor::class)
val client = CaterKtor {
    transport = OkHttpTransport()

    addInterceptor(DefaultHeadersInterceptor("User-Agent" to "MyApp/1.0"))
    addInterceptor(AuthRefreshInterceptor(...))   // auth before retry — intentional
    addInterceptor(RetryInterceptor())
    addInterceptor(LoggerInterceptor(LogLevel.Body) { println(it) })
}

// Print the exact execution order at any time:
println(client.describePipeline())
// [0] DefaultHeadersInterceptor
// [1] AuthRefreshInterceptor
// [2] RetryInterceptor
// [3] LoggerInterceptor
// [4] Transport(KtorTransport)
```

Ordering matters. Auth before retry means a refreshed token is used on the retry attempt.
Logging after retry means you see exactly what was sent on each attempt, not just the first.

---

## Auth

### Bearer token injection

```kotlin
@OptIn(ExperimentalCaterktor::class)
val client = CaterKtor {
    transport = OkHttpTransport()
    auth {
        bearer { tokenProvider { tokenStore.accessToken() } }
    }
}
```

### Bearer + single-flight 401 refresh

When ten concurrent requests all receive a 401, exactly **one** refresh call is made. The other
nine suspend on the same `Deferred<Token>` and resume with the refreshed token without racing.

```kotlin
@OptIn(ExperimentalCaterktor::class)
val client = CaterKtor {
    transport = OkHttpTransport()
    auth {
        bearer {
            tokenProvider { tokenStore.accessToken() }
        }
        refresh {
            refreshToken {
                // Called at most once per budget window, regardless of concurrency
                tokenStore.refreshAccessToken()
            }
            budget(maxRefreshes = 1, windowMs = 60_000L)  // default: 1 refresh per 60 s
            onRefreshFailed { cause ->
                // Fires exactly once when the budget is exhausted or refresh throws
                navigator.navigateToLogin()
            }
        }
    }
}
```

To call auth endpoints through the same client without triggering the auth interceptor:

```kotlin
client.post<Token>(
    url = "auth/refresh",
    attributes = Attributes { put(CaterKtorKeys.SKIP_AUTH, true) },
)
```

---

## Retry

```kotlin
@OptIn(ExperimentalCaterktor::class)
addInterceptor(
    RetryInterceptor(
        maxAttempts = 3,
        policy = ExponentialBackoffPolicy(
            baseDelayMs   = 200L,
            maxDelayMs    = 10_000L,
            jitterFactor  = 1.0,  // full jitter — default and recommended
        ),
        retryNonIdempotent = false,  // set true + add Idempotency-Key for POST/PATCH
    )
)
```

Default behaviour out of the box:

- Retries on `NetworkError.Timeout`, `NetworkError.ConnectionFailed`, and HTTP 502 / 503 / 504
- Full jitter (AWS-style) — delay is uniform in `[0, cap]`, preventing thundering herds
- Honoured `Retry-After` headers override the computed delay
- Only idempotent methods by default (GET, HEAD, DELETE, PUT, OPTIONS)
- POST and PATCH require `retryNonIdempotent = true` **and** an `Idempotency-Key` request header — missing the header with opt-in enabled throws immediately rather than silently skipping

---

## Circuit breaker

```kotlin
@OptIn(ExperimentalCaterktor::class)
addInterceptor(
    CircuitBreaker(
        failureThreshold = 5,
        openDurationMs   = 30_000L,
    )
)
```

After `failureThreshold` consecutive failures the circuit opens and rejects all calls with
`NetworkError.CircuitOpen` until the open window expires. The next probe attempt moves it to
`HalfOpen`. A successful probe closes it; a failed probe reopens it. State transitions surface
as `NetworkEvent.CircuitBreakerTransition`.

---

## Serialization

### JSON (most common)

```kotlin
@OptIn(ExperimentalCaterktor::class)
val client = CaterKtor {
    transport = OkHttpTransport()
    addConverter(KotlinxJsonConverter())        // lenient defaults
    // or with custom Json instance:
    addConverter(KotlinxJsonConverter(Json { ignoreUnknownKeys = true }))
}

@Serializable data class User(val id: String, val name: String)
val result: NetworkResult<User> = client.get("/users/me")
```

### Multiple formats via content negotiation

```kotlin
@OptIn(ExperimentalCaterktor::class)
val client = CaterKtor {
    transport = OkHttpTransport()
    contentNegotiation {
        register("application/json",       KotlinxJsonConverter(),     quality = 1.0)
        register("application/x-protobuf", KotlinxProtobufConverter(), quality = 0.9)
        register("application/cbor",       KotlinxCborConverter(),     quality = 0.8)
    }
}
```

The `Accept` header is constructed automatically from registered converters and their quality
values. The response `Content-Type` drives decode dispatch — no branching in app code.

### Response envelope unwrapping

Many APIs wrap responses in an outer envelope. Unwrap it before decode:

```kotlin
@OptIn(ExperimentalCaterktor::class)
val client = CaterKtor {
    transport = OkHttpTransport()
    addConverter(KotlinxJsonConverter())
    responseUnwrapper = DataFieldUnwrapper("data")  // unwraps {"data": {...}}
}
```

Built-in unwrappers: `DataFieldUnwrapper`, `PagedUnwrapper`. Implement `ResponseUnwrapper` for
custom shapes. Override per-request via `NetworkRequest.attributes`.

---

## Logging and redaction

```kotlin
@OptIn(ExperimentalCaterktor::class)
addInterceptor(
    LoggerInterceptor(
        level  = LogLevel.Body,
        logger = { line -> Napier.d(line) },  // wire any logger
        redaction = RedactionEngine(
            headerNames    = setOf("Authorization", "X-Session-Token"),
            queryParams    = setOf("api_key", "token"),
            jsonBodyFields = setOf("password", "ssn", "cardNumber"),
        ),
    )
)
```

Redaction is **on by default** when logging is enabled. The default `RedactionEngine` redacts
`Authorization`, `Cookie`, `Set-Cookie`, `Proxy-Authorization`, `X-Auth-Token`, and `X-Api-Key`
headers automatically. Add fields; you cannot globally disable. Transport failures (DNS, TLS, timeout)
are logged as `<! ErrorType after Xms: message` on the failure path, not only on successful responses.

Log levels:

| Level | What you see |
|---|---|
| `None` | Nothing |
| `Basic` | Method, URL, status, duration |
| `Headers` | Above + request and response headers (redacted) |
| `Body` | Above + request and response bodies (redacted) |

---

## Testing

CaterKtor ships a dedicated testing artifact so your repository tests never touch a real network.

### Fast unit tests — `FakeNetworkClient`

```kotlin
@OptIn(ExperimentalCaterktor::class)
class UserRepositoryTest {
    @Test
    fun returnsUserOnSuccess() = runTest {
        val fake = FakeNetworkClient {
            addConverter(KotlinxJsonConverter())
        }
        fake.enqueue(jsonResponse("""{"id":"1","name":"Ada"}"""))

        val repo = UserRepository(fake.client)
        val user = repo.getUser("1")

        assertEquals("Ada", user.name)
        assertEquals(1, fake.requests.size)
    }

    @Test
    fun handlesUnauthorized() = runTest {
        val fake = FakeNetworkClient()
        fake.enqueue(testResponse(status = HttpStatus.Unauthorized))

        val repo = UserRepository(fake.client)
        assertIs<NetworkResult.Failure>(repo.getUser("1"))
    }
}
```

### Route-scripted tests — `CaterktorTestServer`

```kotlin
@OptIn(ExperimentalCaterktor::class)
class UserApiTest {
    @Test
    fun fetchesUserFromRoute() = runTest {
        val server = CaterktorTestServer()
        server.route(HttpMethod.GET, "/users/me", jsonResponse("""{"id":"1","name":"Ada"}"""))

        val client = server.client { addConverter(KotlinxJsonConverter()) }
        val result = client.get<User>("/users/me")

        assertIs<NetworkResult.Success<User>>(result)
        assertEquals("Ada", result.body.name)
    }
}
```

Test helpers at a glance:

```kotlin
testResponse(status = HttpStatus.OK, body = byteArrayOf())  // blank 200
jsonResponse("""{"key":"value"}""")                          // 200 + Content-Type: application/json
httpFailure(HttpStatus.NotFound)                             // NetworkError.Http pre-built
```

---

## Observability

`NetworkClient.events` is a `SharedFlow<NetworkEvent>`. Collect it to wire any observability
backend — structured logs, metrics, traces — without coupling to the interceptor chain.

```kotlin
@OptIn(ExperimentalCaterktor::class)
scope.launch {
    client.events.collect { event ->
        when (event) {
            is NetworkEvent.CallStart      -> metrics.requestStarted(event.requestId)
            is NetworkEvent.CallSuccess    -> metrics.recordLatency(event.requestId, event.durationMs)
            is NetworkEvent.CallFailure    -> metrics.recordError(event.requestId, event.error)
            is NetworkEvent.CircuitBreakerTransition ->
                log.warn("Circuit ${event.name}: ${event.from} → ${event.to}")
            else -> {}
        }
    }
}
```

`requestId` is consistent across all events for the same logical call — start, retries, refresh
waits, and final outcome all share one ID, making log correlation across interceptors exact.

---

## Timeouts

```kotlin
@OptIn(ExperimentalCaterktor::class)
val client = CaterKtor {
    transport = OkHttpTransport()
    timeout {
        requestTimeoutMs = 30_000L   // per attempt
    }
}
```

Per-call deadlines are passed to typed helpers through the `deadline` parameter and propagate
through `Chain`, so retry delays and auth refresh waits can honor the same logical budget.

---

## Conventions

**Always handle both variants.** `NetworkResult` is sealed. The compiler will warn on a non-exhaustive
`when`. Do not suppress the warning — handle the `Failure` branch.

**Do not catch `CancellationException`.** CaterKtor never wraps it. If a coroutine is cancelled
mid-request, the exception propagates to the scope that owns the coroutine. Catching it breaks
structured concurrency.

**Keep the `NetworkClient` as a singleton.** Constructing a new client per request creates a new
Ktor `HttpClient` and its underlying engine thread pool. One client per logical backend (API, CDN,
internal service) is the right granularity.

**Use `describePipeline()` when debugging ordering issues.** The output is the contract — the list
matches the exact execution order at runtime.

**Scope `@OptIn(ExperimentalCaterktor::class)` to the file, not the module.** This makes it easy
to find and update call sites when surfaces stabilise.

---

## What's next

CaterKtor is at `0.1.0`. The foundation is stable and BCV-gated. The `0.1.1`
patch line targets query-parameter ergonomics and the Ktor `3.4.3` patch
baseline. Planned milestones:

### `0.2.0` — streaming, testing, observability
- Streaming response download — `KtorTransport` will return `ResponseBody.Source` so large payloads are never buffered
- `RequestBody.Multipart` and `RequestBody.Form` for file upload and form submission
- Rule-based `FakeNetworkClient` DSL — `on { GET("/users/{id}") } respond { ok(user) }`
- Fully embedded `CaterktorTestServer` backed by real TCP for HTTP semantics tests
- Event-first logging — `LoggerInterceptor` subscribes to `NetworkEvent` rather than intercepting the chain directly
- WebSocket support via `caterktor-websocket`

### `0.3.0` — adapters, platforms
- `ConnectivityProbe` — Android `ConnectivityManager` / iOS `NWPathMonitor` integration surfacing `NetworkError.Offline`
- OpenTelemetry tracing adapter (`caterktor-otel`) when a stable KMP OTel SDK ships
- Ktorfit declarative adapter (`caterktor-ktorfit`) when upstream KSP support covers all 9 targets
- JS (IR) and wasmJs targets
- SSE (Server-Sent Events) via `caterktor-sse`

### `1.0.0` — API stability
- `Interceptor` and `Chain` graduate out of `@ExperimentalCaterktor`
- Full semver breaking-change guarantee

### Explicit non-goals
These are out of scope and will not be added:
- **XML serialization** — bring your own `BodyConverter` implementation
- **gRPC** — different transport protocol, separate project
- **Our own annotation processor / codegen** — the Ktorfit adapter is the declarative path, not a fork
- **DI framework integrations** — CaterKtor composes into any DI container; it requires none

---

## Known limitations

These are honest limitations of the current release, not bugs that slipped through:

- **Streaming response download is not yet implemented.** `KtorTransport` reads each HTTP response into
  memory with `readRawBytes()` before returning. The `ResponseBody.Source` ABI type exists and is
  source-first, but the transport does not yet produce it. The 10 MiB `maxBodyDecodeBytes` guard
  applies after the read. For large file downloads, use Ktor directly until `0.2.0`.

- **`RequestBody.Multipart` and `RequestBody.Form` are not yet implemented.** The sealed
  `RequestBody` hierarchy has `Bytes`, `Text`, and `Source`. File upload and form encoding
  arrive in `0.2.0`.

- **`@ExperimentalCaterktor` is required on all public API surfaces.** This opt-in will be
  removed incrementally starting at `0.3.0` as surfaces prove stable under field use.

- **OTel and Ktorfit adapters are not yet released.** Both modules are reserved for future waves
  pending their upstream dependencies reaching stable KMP maturity.

---

## Contributing

Contributions are welcome. Before opening a pull request:

1. **Open an issue first** for any non-trivial change — a quick alignment on direction saves
   wasted effort on both sides.

2. **Run the full verification gate locally:**
   ```bash
   ./gradlew check apiCheck
   ```
   All tests must pass on all enabled targets.

3. **Public API changes require an API dump update:**
   ```bash
   ./gradlew apiDump
   ```
   Commit the updated `.api` files alongside the code change.

4. **Match the existing code style.** The codebase uses `explicitApi()`, KDoc on every public
   symbol, and `@ExperimentalCaterktor` on surfaces that may change.

5. **Tests are not optional.** New interceptors need unit tests against `FakeNetworkClient`.
   New transport behaviour needs tests against `CaterktorTestServer`.

See [CONTRIBUTING.md](CONTRIBUTING.md) for the full guide, and [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)
for community standards. Security issues should follow the process in [SECURITY.md](SECURITY.md).

---

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
