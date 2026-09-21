# Changelog

All notable changes to CaterKtor are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project uses
[semantic versioning](https://semver.org/spec/v2.0.0.html) — while the version is below `1.0.0`,
a minor bump may carry breaking changes to APIs marked `@ExperimentalCaterktor`.

Each release also has a [GitHub release page](https://github.com/oyedsamu/caterktor/releases)
with the full notes.

## [Unreleased]

### Changed

- The core pipeline is no longer `@ExperimentalCaterktor`. Building a client, writing an
  interceptor and implementing a transport no longer require an opt-in at the call site.
  Newer surfaces stay experimental — see [API stability](README.md#api-stability).

## [0.4.0] — 2026-09-20

### Added

- `network { }` block on the builder, carrying `ProxySpec` and `DnsResolver`.
- `TransportFactory`, invoked at `build()` time so engine settings apply while the engine is
  still being constructed. `HttpClient.config { }` reuses an existing engine, so a proxy cannot
  be retrofitted onto a transport the caller already built.
- `TransportCapability`. Engines declare what they can honour, and a setting an engine cannot
  apply fails the build instead of being silently dropped — configuring `dns` against `Darwin`
  is an error, because `NSURLSession` exposes no DNS hook.
- `Cio`, `OkHttp` and `Darwin` transport factories.

### Changed

- Ktor 3.6.0.
- Kotlin 2.3.21. Ktor 3.5.0 onwards makes the Kotlin 2.3.0 JS linker fail while linking test
  executables.
- `compileSdk` 37, required by OkHttp 5.x, which Ktor 3.5.0 pulls in. Uses
  `android.suppressUnsupportedCompileSdk` until the AGP 9.4 and Gradle 9.6 upgrade lands.
- The README comparison against Ktor now reflects what Ktor currently does. Several claims had
  been overtaken by Ktor releases.

### Fixed

- `ProxySpec.Http` rejects any scheme other than `http://` at construction. Ktor's JVM proxy
  builder discards the scheme while its native builder rejects anything but `http`, so an
  `https://` proxy URL worked on Android and threw on iOS.

## [0.3.0] — 2026-05-29

### Added

- Byte-level upload and download progress via `NetworkEvent.UploadProgress` and
  `NetworkEvent.DownloadProgress`.
- `requestId` and `onDownloadProgress` on `KtorTransport.download`, as a binary-compatible
  overload.

### Fixed

- Multipart upload progress is monotonic and safe across retries.
- Ten correctness, thread-safety and resource fixes from a full codebase audit.
- `ProgressSource` cross-module classpath collision.

## [0.2.0] — 2026-05-29

### Added

- Block-scoped streaming downloads through `KtorTransport.download(request) { response -> ... }`.
- `RequestBody.Multipart` and `RequestBody.Form` for file upload and form submission.
- Rule-based fake transport DSL with path-template matching such as `/users/{id}`.
- `CaterktorHttpServer`, a JVM-only server for integration tests over real TCP.
- `NetworkEventLogger`, deriving logs from pipeline events.
- `caterktor-connectivity`, with `ConnectivityProbe` on Android and iOS.
- `caterktor-websocket` and `caterktor-sse`.
- JS IR targets across the shared modules.

### Changed

- Ktor connection errors map to distinct `NetworkError` kinds for DNS failure, connection
  refused, host unreachable and TLS handshake failure.

## [0.1.1] — 2026-05-29

### Added

- `QueryParameters` on the typed request helpers, preserving insertion order and repeated keys.

### Changed

- Aligned with the current Ktor patch release.

### Fixed

- Client lifecycle safety.

## [0.1.0] — 2026-04-23

Initial release: typed results, structured errors, a controlled interceptor pipeline,
single-flight auth refresh, exponential-backoff retry, a circuit breaker, structured logging
with redaction, content negotiation, and test support.

[Unreleased]: https://github.com/oyedsamu/caterktor/compare/v0.4.0...HEAD
[0.4.0]: https://github.com/oyedsamu/caterktor/releases/tag/v0.4.0
[0.3.0]: https://github.com/oyedsamu/caterktor/releases/tag/v0.3.0
[0.2.0]: https://github.com/oyedsamu/caterktor/releases/tag/v0.2.0
[0.1.1]: https://github.com/oyedsamu/caterktor/releases/tag/v0.1.1
[0.1.0]: https://github.com/oyedsamu/caterktor/releases/tag/v0.1.0
