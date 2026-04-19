# CaterKtor

A small Kotlin networking abstraction backed by Ktor.

The library lets application modules depend on `NetworkClient` instead of Ktor's `HttpClient`. Ktor remains the implementation detail, while callers get typed request helpers and `Result` responses.

## Install

```kotlin
dependencies {
    implementation("com.byoyedele.caterktor:caterktor:0.1.0-SNAPSHOT")
}
```

## Usage

```kotlin
@Serializable
data class ProfileResponse(val name: String)

class ProfileService(
    private val networkClient: NetworkClient,
) {
    suspend fun profile(): Result<ProfileResponse> {
        return networkClient.get("/profile")
    }
}
```

For the default `data` envelope:

```kotlin
@Serializable
data class UpdateProfileRequest(val name: String)

suspend fun updateProfile(request: UpdateProfileRequest): Result<Unit> {
    return networkClient.post("/profile", request)
}
```

For raw JSON bodies:

```kotlin
suspend fun sendRaw(request: UpdateProfileRequest): Result<Unit> {
    return networkClient.plainJsonPost("/profile", request)
}
```

## Logging

Logging is opt-in and lazy. When no logger is supplied, CaterKtor does not build log messages.

```kotlin
val logger = caterKtorLogger { message ->
    println(message)
}

val client = KtorNetworkClient(
    httpClient = androidKtorHttpClient(),
    logger = logger,
)
```

Example output:

```text
CaterKtor -> GET /profile?country=NG headers={Authorization=<redacted>, X-Request-Id=request-id}
CaterKtor <- 200 OK for GET /profile
```

## Publish

Publish locally:

```bash
./gradlew publishToMavenLocal
```

Publish to a Maven repository:

```bash
PUBLISHING_URL=https://maven.example.com/releases \
PUBLISHING_USERNAME=... \
PUBLISHING_PASSWORD=... \
./gradlew publish
```
