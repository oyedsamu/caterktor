@file:OptIn(ExperimentalForeignApi::class)

package io.github.oyedsamu.caterktor.connectivity

import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import kotlinx.cinterop.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import platform.CoreFoundation.CFRunLoopGetCurrent
import platform.CoreFoundation.kCFRunLoopDefaultMode
import platform.SystemConfiguration.*
import platform.posix.AF_INET
import platform.posix.sockaddr_in

/**
 * iOS implementation of [ConnectivityProbe] backed by [SCNetworkReachabilityRef].
 *
 * Starts monitoring automatically on construction via `SCNetworkReachabilitySetCallback`
 * scheduled on the calling run loop. Call [cancel] when the owning component is destroyed
 * to unregister the callback and release resources.
 *
 * Must be constructed and cancelled on the same thread that owns the run loop.
 */
@ExperimentalCaterktor
public class IosConnectivityProbe : ConnectivityProbe {

    private val _isOnline = MutableStateFlow(false)
    override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private var reachability: SCNetworkReachabilityRef? = null
    private var selfRef: StableRef<IosConnectivityProbe>? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    init {
        start()
    }

    private fun start() {
        val ref = memScoped {
            val addr = alloc<sockaddr_in>().apply {
                sin_len = sizeOf<sockaddr_in>().toUByte()
                sin_family = AF_INET.convert()
                sin_port = 0u
                sin_addr.s_addr = 0u
            }
            SCNetworkReachabilityCreateWithAddress(null, addr.ptr.reinterpret())
        } ?: return

        reachability = ref
        selfRef = StableRef.create(this)

        val callback = staticCFunction<SCNetworkReachabilityRef?, SCNetworkReachabilityFlags, COpaquePointer?, Unit> { _, flags, info ->
            info?.asStableRef<IosConnectivityProbe>()?.get()?.update(flags)
        }

        val context = nativeHeap.alloc<SCNetworkReachabilityContext>().apply {
            version = 0
            info = selfRef!!.asCPointer()
            retain = null
            release = null
            copyDescription = null
        }

        if (SCNetworkReachabilitySetCallback(ref, callback, context.ptr)) {
            SCNetworkReachabilityScheduleWithRunLoop(ref, CFRunLoopGetCurrent(), kCFRunLoopDefaultMode)
        }

        // Emit initial state synchronously before any callback fires.
        memScoped {
            val flagsVar = alloc<SCNetworkReachabilityFlagsVar>()
            if (SCNetworkReachabilityGetFlags(ref, flagsVar.ptr)) update(flagsVar.value)
        }
    }

    internal fun update(flags: SCNetworkReachabilityFlags) {
        val reachable = flags.toInt() and kSCNetworkReachabilityFlagsReachable.toInt() != 0
        val needsConn = flags.toInt() and kSCNetworkReachabilityFlagsConnectionRequired.toInt() != 0
        scope.launch { _isOnline.value = reachable && !needsConn }
    }

    /** Unregister the reachability callback and release all native resources. Idempotent. */
    public fun cancel() {
        reachability?.let {
            SCNetworkReachabilityUnscheduleFromRunLoop(it, CFRunLoopGetCurrent(), kCFRunLoopDefaultMode)
        }
        reachability = null
        selfRef?.dispose()
        selfRef = null
    }
}
