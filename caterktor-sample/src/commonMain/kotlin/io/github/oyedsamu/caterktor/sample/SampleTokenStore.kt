package io.github.oyedsamu.caterktor.sample

public class SampleTokenStore(
    initialAccessToken: String = "expired-token",
    private val refreshedToken: String = "fresh-token",
) {
    private var currentAccessToken: String = initialAccessToken

    public var refreshCalls: Int = 0
        private set

    public suspend fun accessToken(): String = currentAccessToken

    public suspend fun refreshAccessToken(): String {
        refreshCalls += 1
        currentAccessToken = refreshedToken
        return currentAccessToken
    }
}
