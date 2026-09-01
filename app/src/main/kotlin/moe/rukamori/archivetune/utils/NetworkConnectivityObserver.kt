/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Simple NetworkConnectivityObserver based on OuterTune's implementation
 * Provides network connectivity monitoring for auto-play functionality
 */
class NetworkConnectivityObserver(
    context: Context,
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _networkStatus = MutableStateFlow(isCurrentlyConnected())
    val networkStatus = _networkStatus.asStateFlow()

    private val _networkChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val networkChanges = _networkChanges.asSharedFlow()

    private val activeNetworkTracker = ActiveNetworkTracker(connectivityManager.activeNetwork)

    private val networkCallback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (activeNetworkTracker.onAvailable(network)) {
                    _networkChanges.tryEmit(Unit)
                }
                updateNetworkStatus()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                if (activeNetworkTracker.isActive(network)) {
                    _networkStatus.value = networkCapabilities.hasValidatedInternet()
                }
            }

            override fun onLost(network: Network) {
                activeNetworkTracker.onLost(network)
                updateNetworkStatus()
            }
        }

    init {
        try {
            // A default-network callback represents the route used by new HTTP connections.
            // Listening to every capable network would produce false hand-offs when, for
            // example, Wi-Fi and cellular are both available at the same time.
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
        } catch (e: Exception) {
            // Fallback: assume connected if registration fails
            _networkStatus.value = true
        }
    }

    fun unregister() {
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    /**
     * Check current connectivity state synchronously
     */
    fun isCurrentlyConnected(): Boolean =
        try {
            val activeNetwork = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)

            // Check if we have internet capability
            val hasInternet = networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

            // For API 23+, also check if connection is validated
            val isValidated =
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
                } else {
                    true // For older versions, assume validated if we have internet capability
                }

            hasInternet && isValidated
        } catch (e: Exception) {
            false
        }

    private fun updateNetworkStatus() {
        _networkStatus.value = isCurrentlyConnected()
    }
}

internal class ActiveNetworkTracker<T>(initialNetwork: T?) {
    private var lastNetwork = initialNetwork
    private var hasActiveNetwork = initialNetwork != null

    /** Returns true when Android replaces the previously known default route. */
    fun onAvailable(network: T): Boolean {
        val changed = lastNetwork != null && lastNetwork != network
        lastNetwork = network
        hasActiveNetwork = true
        return changed
    }

    fun onLost(network: T) {
        if (lastNetwork == network) hasActiveNetwork = false
    }

    fun isActive(network: T): Boolean = hasActiveNetwork && lastNetwork == network
}

private fun NetworkCapabilities.hasValidatedInternet(): Boolean =
    hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
