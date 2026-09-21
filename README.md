# CaterKtor

**The application networking layer built on Ktor.**

[![Kotlin Multiplatform](https://img.shields.io/badge/Kotlin-Multiplatform-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/docs/multiplatform.html)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Version](https://img.shields.io/badge/version-0.4.0-brightgreen)](https://central.sonatype.com/search?q=io.github.oyedsamu)
[![API](https://img.shields.io/badge/API-BCV%20gated-7F52FF)](https://github.com/Kotlin/binary-compatibility-validator)

[Changelog](CHANGELOG.md) · [Releases](https://github.com/oyedsamu/caterktor/releases)

---

## Why CaterKtor instead of Ktor directly?

Ktor covers the transport layer and a fair amount above it. Current versions ship retry with exponential backoff, jitter and `Retry-After` handling, token refresh serialised behind a mutex in `AuthTokenHolder`, and header sanitising in the `Logging` plugin. If that covers what you need, use Ktor on its own.

CaterKtor sits one layer up, where a call becomes a typed result and where the policy for that call is written down in one place.

- Failure reaches you as an exception. A `ClientRequestException` says something went wrong; deciding whether it was DNS, a TLS handshake, a 401, or a connect timeout rather than a read timeout means inspecting exception types and messages. CaterKtor returns a `NetworkResult`, and failures are a sealed `NetworkError` you branch on in a `when`.
- Plugin order in Ktor comes from coroutine pipeline phases, which are set by each plugin rather than by you. CaterKtor runs interceptors in registration order, so the sequence of auth, retry and logging can be read off the builder.
- Concurrent refreshes are serialised, but nothing limits how many happen over time. `RefreshBudget` caps them to a count per window, one per minute by default, and raises `AuthRefreshBudgetExceededException` once the budget is spent, instead of refreshing on every 401 for as long as the process lives.
- Ktor has no circuit breaker. CaterKtor's opens after 10 consecutive failures, stays open for 30 seconds, then admits one trial call, which closes it on success and reopens it on failure.
- `Logging` sanitises the headers you name. `RedactionEngine` also covers query parameters, JSON body fields and regex rules, with defaults for the usual credential names.
- Retry treats only GET, HEAD, PUT, DELETE and OPTIONS as safe. POST and PATCH are retried only when you opt in and the request carries an `Idempotency-Key`, and a deadline bounds the whole operation across attempts rather than each attempt separately.
- Testing a repository means reaching for `MockEngine` or a live server. `caterktor-testing` fakes the transport instead, below your code and above the network.

CaterKtor runs on Ktor's engines and keeps them within reach: the `ktor { }` block configures the underlying client directly for anything CaterKtor does not surface.

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

---

## Installation

### Version catalog (recommended)

```toml
# gradle/libs.versions.toml
[versions]
caterktor = "0.4.0"

[libraries]
caterktor-core              = { module = "io.github.oyedsamu:caterktor-core",              version.ref = "caterktor" }
caterktor-ktor              = { module = "io.github.oyedsamu:caterktor-ktor",              version.ref = "caterktor" }
caterktor-auth              = { module = "io.github.oyedsamu:caterktor-auth",              version.ref = "caterktor" }
caterktor-logging           = { module = "io.github.oyedsamu:caterktor-logging",           version.ref = "caterktor" }
caterktor-serialization-json = { module = "io.github.oyedsamu:caterktor-serialization-json", version.ref = "caterktor" }
caterktor-engine-okhttp     = { module = "io.github.oyedsamu:caterktor-engine-okhttp",    version.ref = "caterktor" }
caterktor-engine-darwin     = { module = "io.github.oyedsamu:caterktor-engine-darwin",    version.ref = "caterktor" }
caterktor-engine-cio        = { module = "io.github.oyedsamu:caterktor-engine-cio",       version.ref = "caterktor" }
caterktor-connectivity      = { module = "io.github.oyedsamu:caterktor-connectivity",     version.ref = "caterktor" }
caterktor-websocket         = { module = "io.github.oyedsamu:caterktor-websocket",        version.ref = "caterktor" }
caterktor-sse               = { module = "io.github.oyedsamu:caterktor-sse",              version.ref = "caterktor" }
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
    implementation("io.github.oyedsamu:caterktor-core:0.4.0")
    implementation("io.github.oyedsamu:caterktor-ktor:0.4.0")
    implementation("io.github.oyedsamu:caterktor-engine-okhttp:0.4.0")
    implementation("io.github.oyedsamu:caterktor-auth:0.4.0")
    implementation("io.github.oyedsamu:caterktor-serialization-json:0.4.0")
    implementation("io.github.oyedsamu:caterktor-logging:0.4.0")
    implementation("io.github.oyedsamu:caterktor-connectivity:0.4.0")
    testImplementation("io.github.oyedsamu:caterktor-testing:0.4.0")
}
```

### JVM server / CLI

```kotlin
dependencies {
    implementation("io.github.oyedsamu:caterktor-core:0.4.0")
    implementation("io.github.oyedsamu:caterktor-ktor:0.4.0")
    implementation("io.github.oyedsamu:caterktor-engine-cio:0.4.0")
    implementation("io.github.oyedsamu:caterktor-serialization-json:0.4.0")
    implementation("io.github.oyedsamu:caterktor-websocket:0.4.0")
    implementation("io.github.oyedsamu:caterktor-sse:0.4.0")
    testImplementation("io.github.oyedsamu:caterktor-testing:0.4.0")
}
```

---

## Quick start

> A CI-compiled, runnable version of this exact sample lives in
> [`caterktor-sample/`](caterktor-sample/src/jvmMain/kotlin/io/github/oyedsamu/caterktor/sample/).
> Run it locally: `./gradlew :caterktor-sample:jvmRun`. The sample also prints
> source-backed upload and streaming download progress events.

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

Rules can also match path templates and expose path parameters:

```kotlin
val fake = FakeTransport {
    get("/users/{id}") { match ->
        jsonResponse("""{"id":"${match.pathParameters["id"]}"}""")
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
            is NetworkEvent.UploadProgress ->
                metrics.uploaded(event.requestId, event.bytesSent, event.totalBytes)
            is NetworkEvent.DownloadProgress ->
                metrics.downloaded(event.requestId, event.bytesRead, event.totalBytes)
            is NetworkEvent.ResponseReceived -> {}
            is NetworkEvent.CircuitBreakerTransition ->
                log.warn("Circuit ${event.name}: ${event.from} → ${event.to}")
        }
    }
}
```

Progress totals are nullable because chunked requests, generated streams, and some server
responses do not know their final length up front. Treat `null` as indeterminate progress:

```kotlin
fun percent(done: Long, total: Long?): Int? =
    total?.takeIf { it > 0L }?.let { ((done * 100) / it).toInt() }
```

`caterktor-logging` also includes an event-derived logger for the same flow:

```kotlin
val eventLogger = NetworkEventLogger { line -> println(line) }

scope.launch {
    client.events.collect(eventLogger::log)
}
```

`requestId` is consistent across all events for the same logical call — start, retries, refresh
waits, and final outcome all share one ID, making log correlation across interceptors exact.

---

## Streaming Downloads

For large responses, use the Ktor-backed block-scoped streaming API. The body source is one-shot
and must be consumed inside the block; Ktor releases the underlying response resources when the
block returns.

```kotlin
val bytesWritten = transport.download(
    NetworkRequest(HttpMethod.GET, "https://cdn.example.com/archive.zip"),
    requestId = "archive-download",
    onDownloadProgress = { progress ->
        val label = progress.totalBytes?.let { "${progress.bytesRead}/$it" }
            ?: "${progress.bytesRead} bytes"
        println("download ${progress.requestId}: $label")
    },
) { response ->
    val source = response.body.source()
    try {
        source.transferTo(fileSink)
    } finally {
        source.close()
    }
}
```

If you are already using `NetworkClient.events`, a streaming `ResponseBody.Source` returned by a
custom transport also emits `NetworkEvent.DownloadProgress` as the source is consumed. The
Ktor-specific callback above exists for the lower-level `KtorTransport.download(...)` escape hatch,
which runs outside the `NetworkClient.events` flow.

Typed helpers such as `client.get<T>()` still buffer up to `maxBodyDecodeBytes` before decoding,
which is the right behavior for JSON/protobuf models. Use `KtorTransport.download(...)` for file
or blob downloads.

---

## Form and Multipart Bodies

Use `RequestBody.Form` for `application/x-www-form-urlencoded` requests:

```kotlin
val body = RequestBody.Form(
    RequestBody.Form.Field("grant_type", "refresh_token"),
    RequestBody.Form.Field("refresh_token", refreshToken),
)
```

Use `RequestBody.Multipart` for form-data uploads:

```kotlin
val body = RequestBody.Multipart(
    RequestBody.Multipart.Part.field("title", "avatar"),
    RequestBody.Multipart.Part.formData(
        name = "file",
        filename = "avatar.png",
        body = RequestBody.Source(
            sourceFactory = { imageSource() },
            contentType = "image/png",
        ),
    ),
)
```

Source-backed multipart parts stream through `KtorTransport` without first materializing the file
body. When the request goes through `NetworkClient`, source-backed parts also emit
`NetworkEvent.UploadProgress`:

```kotlin
val uploadEvents = scope.launch {
    client.events.collect { event ->
        if (event is NetworkEvent.UploadProgress) {
            println("uploaded ${event.bytesSent} of ${event.totalBytes ?: "unknown"}")
        }
    }
}

client.execute(
    NetworkRequest(
        method = HttpMethod.POST,
        url = "https://api.example.com/profile/avatar",
        body = body,
    ),
)
```

Use `contentLength` when you know it. That gives progress UIs a determinate total; omit it for
chunked or generated streams:

```kotlin
RequestBody.Source(
    sourceFactory = { fileSystem.source(path) },
    contentType = "application/octet-stream",
    contentLength = fileSize,
)
```

---

## Timeouts

```kotlin
val client = CaterKtor {
    transport = OkHttpTransport()
    timeout {
        requestTimeoutMs = 30_000L   // per attempt
    }
}
```

`requestTimeoutMs` is enforced by `NetworkClient` around each attempt. `connectTimeoutMs` and
`socketTimeoutMs` are applied to the engine, which means the transport has to come from
`engine(...)` — a transport assigned to `transport` is already constructed by the time the
builder runs:

```kotlin
val client = CaterKtor {
    engine(OkHttp)
    timeout {
        connectTimeoutMs = 5_000L
        socketTimeoutMs  = 15_000L
        requestTimeoutMs = 30_000L
    }
}
```

| Engine | `connectTimeoutMs` | `socketTimeoutMs` |
|---|---|---|
| `Cio` | `endpoint.connectTimeout` | `endpoint.socketTimeout` |
| `OkHttp` | `connectTimeout` | `readTimeout` and `writeTimeout` |
| `Darwin` | not expressible | `timeoutIntervalForRequest` |

`NSURLSession` has no separate connect timeout, so on Darwin the connect phase is bounded by
the socket and request timeouts rather than independently.

Per-call deadlines are passed to typed helpers through the `deadline` parameter and propagate
through `Chain`, so retry delays and auth refresh waits can honor the same logical budget.

---

## Engine configuration

A proxy or a DNS resolver has to be set while the engine is being built. `HttpClient.config { }`
reuses the engine it already has, so these cannot be applied to a transport you constructed
yourself. Pass the engine to `engine(...)` instead of assigning `transport`, and the builder
constructs it once the `network { }` block has been read.

```kotlin
@OptIn(ExperimentalCaterktor::class)
val client = CaterKtor {
    engine(OkHttp)                                   // or Cio, Darwin
    network {
        proxy = ProxySpec.Http("http://proxy.corp:8080")
        dns = DnsResolver { hostname -> doh.lookup(hostname) }
    }
}
```

`ProxySpec.Http` takes an `http://` URL; use `ProxySpec.Socks(host, port)` for SOCKS. Other
schemes are rejected at construction because the engines disagree about them: Ktor's JVM builder
drops the scheme and proxies over plain HTTP, while its native builder rejects anything but
`http`.

Engines declare what they can honor, and a setting an engine cannot apply fails `build()` rather
than being dropped:

| | `proxy` | `dns` |
|---|---|---|
| `Cio` | yes | yes |
| `OkHttp` | yes | yes, adapted to `okhttp3.Dns` |
| `Darwin` | yes | no, `NSURLSession` has no DNS hook |

Assigning `transport` directly still works and remains the right choice for a pre-built
`HttpClient`. Setting both `engine(...)` and `transport` is an error.

---

## API stability

Making a request and decoding it is stable. Building a client, writing an interceptor,
implementing a transport, the three engine transports and the JSON, CBOR and protobuf
converters carry no opt-in requirement, and changes to them follow semantic versioning.

Policy and newer surfaces are still `@ExperimentalCaterktor` and need
`@OptIn(ExperimentalCaterktor::class)` at the call site:

| Stable | Experimental |
|---|---|
| `CaterKtor { }`, `CaterKtorBuilder`, `NetworkClient` | `auth { }` and `AuthRefreshInterceptor` |
| `NetworkRequest`, `NetworkResponse`, `NetworkResult`, `NetworkError` | `RetryInterceptor`, `RetryPolicy`, `CircuitBreaker` |
| `Headers`, `QueryParameters`, `HttpMethod`, `HttpStatus`, `Attributes` | `LoggerInterceptor` and `RedactionEngine` |
| `Interceptor`, `Chain`, `Transport`, `CloseableTransport` | `NetworkEvent` and `NetworkClient.events` |
| `TimeoutConfig`, `BodyConverter`, `ContentNegotiationRegistry` | `network { }`, `engine(...)`, `ProxySpec`, `DnsResolver` |
| `KtorTransport`, `CioTransport`, `OkHttpTransport`, `DarwinTransport` | `ResponseUnwrapper`, `RequestEnveloper` |
| `KotlinxJsonConverter`, `KotlinxCborConverter`, `KotlinxProtobufConverter` | `caterktor-testing`, `caterktor-websocket`, `caterktor-sse`, `caterktor-connectivity` |

An experimental API can change or be removed in any release without a deprecation cycle. A
stable one cannot, which is why the line sits where it does: the pipeline has been in use since
`0.1.0`, while proxy and DNS configuration landed in `0.4.0` and has had no field feedback yet.

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

**Scope `@OptIn(ExperimentalCaterktor::class)` to the file, not the module.** Only experimental
surfaces need it — see [API stability](#api-stability). Keeping it per file makes the remaining
call sites easy to find when those surfaces stabilise.

---

## What's next

CaterKtor is moving from `0.4.0` into `0.5.0`. The next release makes the request pipeline
stable, so building a client and writing an interceptor no longer require an opt-in at every
call site.

Every release so far has been additive: `apiCheck` gates each module, and no
declaration published in `0.2.0` or `0.3.0` has been removed.

### `0.2.0` — streaming, testing, observability, realtime
- Block-scoped streaming downloads via `KtorTransport.download(request) { response -> ... }`
- `RequestBody.Multipart` and `RequestBody.Form` for file upload and form submission
- Rule-based fake transport DSL with path-template matching such as `/users/{id}`
- JVM-only `CaterktorHttpServer` for real TCP integration tests
- Event-derived logging via `NetworkEventLogger`
- Fine-grained Ktor connection error mapping for DNS, refused, unreachable, and TLS handshake signals
- Android/iOS `ConnectivityProbe` support via `caterktor-connectivity`
- WebSocket support via `caterktor-websocket`
- Server-Sent Events support via `caterktor-sse`
- JS IR targets across the shared KMP modules

### `0.3.0` — progress, adapter decisions, platform polish
- Upload/download byte-level progress events via `NetworkEvent`
- `KtorTransport.download(...)` progress callback for lower-level streaming downloads
- OpenTelemetry tracing adapter go/no-go; no placeholder artifact will be published
- Ktorfit declarative adapter go/no-go; no forked annotation processor will be introduced
- wasmJs go/no-go based on deterministic local `wasmJsTest` and publication gates
- `@ExperimentalCaterktor` audit before any selective API graduation

### `0.4.0` — engine configuration
- `TransportFactory` builds the transport at `build()` time, after configuration is collected
- `network { }` block carrying `ProxySpec` and `DnsResolver`
- Engines declare `TransportCapability`; a setting an engine cannot honour fails the build
- `ProxySpec.Http` rejects schemes the engines disagree about
- Ktor 3.6.0

### `0.5.0` — a stable request pipeline
- The client builder, `NetworkRequest`, `Interceptor`, `Chain` and `Transport` leave `@ExperimentalCaterktor`
- The engine transports and the JSON, CBOR and protobuf converters leave it with them
- `Headers.toBuilder()`, so an interceptor can add a header without dropping the others
- Policy surfaces stay experimental — see [API stability](#api-stability)

### `1.0.0` — API stability
- Auth, retry and the circuit breaker graduate once their shape has field feedback
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

- **Regular `execute()` and typed helpers still buffer responses.** Use
  `KtorTransport.download(request) { ... }` for large file/blob downloads. Typed decoding remains
  bounded by `maxBodyDecodeBytes`.

- **Progress events are 0.3.0 scope.** They are additive to lifecycle events and report nullable
  totals because many streaming bodies do not know their length up front.

- **`CaterktorTestServer` is still in-memory by design.** Use JVM-only
  `CaterktorHttpServer` when tests need a real TCP socket and HTTP framing semantics.

- **Policy surfaces are still `@ExperimentalCaterktor`.** Auth, retry, the circuit breaker,
  logging, events and engine configuration need an opt-in at the call site. The request pipeline
  itself does not — see [API stability](#api-stability).

- **OTel and Ktorfit adapters are not yet released.** These modules remain reserved until local
  all-target release gates prove they can share CaterKtor's runtime semantics.

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
