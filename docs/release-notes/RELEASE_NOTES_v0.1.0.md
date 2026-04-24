# CaterKtor v0.1.0

Initial public release of CaterKtor — a Kotlin Multiplatform networking layer built on top of
Ktor 3.x that adds production-grade utilities Ktor deliberately leaves to the application:
typed results, structured errors, a controlled interceptor pipeline, single-flight auth refresh,
exponential-backoff retry, circuit breaker, structured logging with redaction, content
negotiation, and first-class test support.

---

### Installation

```kotlin
// build.gradle.kts
implementation("io.github.oyedsamu:caterktor-core:0.1.0")
implementation("io.github.oyedsamu:caterktor-ktor:0.1.0")
implementation("io.github.oyedsamu:caterktor-auth:0.1.0")
implementation("io.github.oyedsamu:caterktor-logging:0.1.0")
implementation("io.github.oyedsamu:caterktor-serialization-json:0.1.0")
```

> All public surfaces are annotated `@ExperimentalCaterktor` in v0.1.0. Stability graduation
> begins in v0.3.0.

---

### Modules

| Artifact | Description |
|---|---|
| `caterktor-core` | `NetworkClient`, `NetworkResult<T>`, `NetworkError`, interceptor pipeline |
| `caterktor-ktor` | Ktor transport (`KtorTransport`) |
| `caterktor-engine-okhttp` | OkHttp transport for Android & JVM |
| `caterktor-engine-darwin` | Darwin native transport for iOS & macOS |
| `caterktor-engine-cio` | Ktor CIO transport for JVM servers |
| `caterktor-auth` | Bearer token injection + single-flight 401 refresh |
| `caterktor-logging` | Structured logging with automatic header/field redaction |
| `caterktor-serialization-json` | Kotlinx.serialization JSON converter |
| `caterktor-serialization-protobuf` | Kotlinx.serialization Protobuf converter |
| `caterktor-serialization-cbor` | Kotlinx.serialization CBOR converter |
| `caterktor-testing` | `FakeNetworkClient`, `CaterktorTestServer`, test helpers |

---

### What's included

**Typed results — no thrown exceptions**
`NetworkResult<T>` is a sealed type. Every call returns either `Success` or `Failure`; the
compiler enforces exhaustive handling. `CancellationException` propagates unchanged; nothing
else escapes as an exception.

**Structured error hierarchy**
`NetworkError` — `Http`, `ConnectionFailed`, `Timeout`, `Serialization`, `Protocol`,
`CircuitOpen`, `Unknown`. Branch on kind, not message string parsing.

**Controlled interceptor pipeline**
`addInterceptor()` registers in execution order. `describePipeline()` returns the full
ordered list including the transport for logging and diagnostics.

**Single-flight 401 refresh**
N concurrent requests all receiving 401 share exactly one refresh call. Waiting requests
resume with the shared token. A per-window `RefreshBudget` prevents runaway refresh loops.
`onRefreshFailed` fires once per budget window.

**Retry with exponential backoff**
Default policy: 3 attempts, exponential backoff, full jitter (AWS-style). `Retry-After`
header honoured. Idempotent methods retried automatically; POST/PATCH require
`retryNonIdempotent = true` and an `Idempotency-Key` header. Deadline-aware: delays respect
the overall call deadline.

**Circuit breaker**
Opens after N consecutive failures; half-open probes retest before closing. State transitions
surface as `NetworkEvent.CircuitBreakerTransition`.

**Structured logging with redaction**
Four levels: `None`, `Basic`, `Headers`, `Body`. `Authorization`, `Cookie`, `Set-Cookie`,
and common API key headers redacted by default. Custom query-param and JSON-field redaction
lists configurable.

**Content negotiation**
Register multiple formats with quality weights. `Accept` built automatically. Response
`Content-Type` drives decode dispatch.

**Observability**
`NetworkClient.events` is a hot `SharedFlow<NetworkEvent>` — `CallStart`,
`ResponseReceived`, `CallSuccess`, `CallFailure`, `CircuitBreakerTransition` — with a
consistent `requestId` across all events for a logical call.

**Testing support**
`FakeNetworkClient` for fast unit tests without a network. `CaterktorTestServer` for
route-scripted tests with real TCP semantics. Both work on all KMP targets.

---

### Supported platforms

Android · iOS · macOS · JVM / server

---

### Known limitations

- **No streaming download.** The Ktor transport reads responses fully into memory (10 MiB
  guard). Streaming arrives in v0.2.0.
- **No multipart / form upload.** Arrives in v0.2.0.
- **`@ExperimentalCaterktor` required on all call sites.** Opt-in removed incrementally
  starting v0.3.0.

---

### Roadmap

- **v0.2.0** — Streaming download, multipart/form upload, WebSocket support, rule-based test DSL
- **v0.3.0** — Connectivity probes, OpenTelemetry adapter, Ktorfit declarative adapter, JS/wasmJs targets, SSE
- **v1.0.0** — Full API stability, `@ExperimentalCaterktor` retired, semver guarantee

---

**Full documentation:** https://github.com/oyedsamu/caterktor
**Group ID:** `io.github.oyedsamu`

