package io.github.oyedsamu.caterktor.connectivity

import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import kotlinx.coroutines.flow.StateFlow

/**
 * Observes device network reachability.
 *
 * Implement this interface to feed the [ConnectivityInterceptor] with
 * real-time connectivity state. Platform-provided implementations:
 * - Android: [AndroidConnectivityProbe] (requires a `Context`)
 * - iOS: [IosConnectivityProbe] (uses `NWPathMonitor`)
 *
 * JVM and Linux callers may supply their own implementation or use an
 * always-online stub.
 */
@ExperimentalCaterktor
public interface ConnectivityProbe {
    /**
     * Hot `StateFlow` that emits `true` when the device has an active
     * network path, and `false` when it does not.
     */
    public val isOnline: StateFlow<Boolean>
}
