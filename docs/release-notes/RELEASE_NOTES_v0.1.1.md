# CaterKtor v0.1.1

Patch release focused on request ergonomics, Ktor patch alignment, lifecycle safety, and complete generated documentation.

---

### Installation

```kotlin
// build.gradle.kts
implementation("io.github.oyedsamu:caterktor-core:0.1.1")
implementation("io.github.oyedsamu:caterktor-ktor:0.1.1")
implementation("io.github.oyedsamu:caterktor-auth:0.1.1")
implementation("io.github.oyedsamu:caterktor-logging:0.1.1")
implementation("io.github.oyedsamu:caterktor-serialization-json:0.1.1")
```

> Public APIs remain annotated `@ExperimentalCaterktor` in the 0.1.x line.

---

### What's changed

**Typed query parameters**

Typed request helpers now accept `queryParams: QueryParameters`, preserving insertion order, repeated keys, null-skipping, and percent-encoding:

```kotlin
client.get<UserPage>(
    "/users/{tenant}",
    pathParams = mapOf("tenant" to "fairmoney"),
    queryParams = QueryParameters {
        add("limit", 20)
        add("tag", "kmp")
        add("tag", "networking")
    },
)
```

`queryParameters("limit" to 20)` and `queryParameters(mapOf(...))` helpers are also available for simple call sites.

**Ktor patch baseline**

Ktor dependencies now target `3.4.3`, keeping CaterKtor aligned with the latest 3.4.x patch line used by the transport and engine modules.

**Transport lifecycle safety**

`KtorTransport` now has regression coverage proving caller-owned `HttpClient` instances are not closed by CaterKtor. This keeps ownership explicit for applications that share clients across SDKs.

**Complete Dokka publication**

The root Dokka publication now aggregates all published modules, includes module/package landing documentation, and emits source links back to GitHub. Release publishing is guarded by `validateDokkaPublication` so an empty or partial aggregate cannot be published unnoticed.

**Documentation cleanup**

README examples now show query parameters and no longer reference stale timeout API names.

---

### Verification

This release candidate has been checked with:

```bash
./gradlew check apiCheck validateDokkaPublication --stacktrace
```

---

### Compatibility

This is a patch release on the experimental 0.1.x API line. The main API addition is `QueryParameters` and the new optional `queryParams` argument on typed request helpers. Existing call sites using positional arguments after `pathParams` should prefer named arguments when upgrading.

---

### Known limitations

- Streaming downloads are still planned for v0.2.0.
- Multipart/form uploads are still planned for v0.2.0.
- Public APIs still require `@OptIn(ExperimentalCaterktor::class)`.

---

**Full documentation:** https://github.com/oyedsamu/caterktor
**Group ID:** `io.github.oyedsamu`
