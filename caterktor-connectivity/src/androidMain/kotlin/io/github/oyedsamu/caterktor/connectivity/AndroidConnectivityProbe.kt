package io.github.oyedsamu.caterktor.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import io.github.oyedsamu.caterktor.ExperimentalCaterktor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android implementation of [ConnectivityProbe] backed by
 * [ConnectivityManager.NetworkCallback].
 *
 * Call [register] after construction to start observing, and [unregister]
 * when the owning component is destroyed.
 *
 * @param context Any Android [Context]; the application context is recommended
 *   to avoid activity leaks.
 */
@ExperimentalCaterktor
public class AndroidConnectivityProbe(context: Context) : ConnectivityProbe {

    private val cm = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isOnline = MutableStateFlow(cm.isCurrentlyOnline())
    override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isOnline.value = true
        }
        override fun onLost(network: Network) {
            _isOnline.value = cm.isCurrentlyOnline()
        }
    }

    /** Start receiving connectivity updates. Idempotent. */
    public fun register(): AndroidConnectivityProbe = apply {
        val request = NetworkRequest.Builder().build()
        cm.registerNetworkCallback(request, callback)
    }

    /** Stop receiving connectivity updates. Idempotent. */
    public fun unregister(): AndroidConnectivityProbe = apply {
        runCatching { cm.unregisterNetworkCallback(callback) }
    }

    private fun ConnectivityManager.isCurrentlyOnline(): Boolean =
        activeNetwork != null
}
