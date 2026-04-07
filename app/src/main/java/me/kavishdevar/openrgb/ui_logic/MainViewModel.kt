package me.kavishdevar.openrgb.ui_logic

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.kavishdevar.openrgb.models.OpenRGBDevice
import me.kavishdevar.openrgb.network.*
import java.net.InetSocketAddress
import java.net.Socket
import androidx.core.content.edit

import me.kavishdevar.openrgb.models.OpenRGBColor
import me.kavishdevar.openrgb.models.OpenRGBLed
import me.kavishdevar.openrgb.models.OpenRGBMode
import me.kavishdevar.openrgb.network.OpenRGBClient

// One-off UI events from ViewModel -> UI (snackbar, dialogs)
sealed class UiEvent {
    data class ShowSnackbar(val message: String) : UiEvent()
    object ShowReconnectError : UiEvent()
    object ShowWifiWarning : UiEvent()
    object ShowServerShutdownWarning : UiEvent()
}

class MainViewModel(
    application: Application,
    private val sharedPref: SharedPreferences,
    private val isFirstLaunch: Boolean
) : AndroidViewModel(application) {

    // Exposed mutable states for Compose to observe (keeps calling code minimal)
    val availableServersState = mutableStateOf<List<String>>(emptyList())
    val devicesState = mutableStateOf<List<OpenRGBDevice>>(emptyList())
    val clientState = mutableStateOf<OpenRGBClient?>(null)
    val connectedState = mutableStateOf(false)
    val currentServerState = mutable_state_of_safe("")

    val showWifiWarningState = mutableStateOf(false)
    val showServerShutdownWarningState = mutableStateOf(false)
    val showReconnectErrorState = mutableStateOf(false)

    val isWifiConnectedState = mutableStateOf(true)
    val isServerReachableState = mutableStateOf(true)

    // Overlay states (kept here so overlay logic is part of app logic)
    val overlayDeviceState = mutableStateOf<OpenRGBDevice?>(null)
    val overlayClientState = mutableStateOf<OpenRGBClient?>(null)
    val overlayStartOffsetState = mutableStateOf<androidx.compose.ui.unit.IntOffset?>(null)
    val overlayStartSizeState = mutable_state_of_nullable_int_size()
    val overlayVisibleState = mutable_state_of(false)

    // scanning / refresh state
    val isRefreshingState = mutable_state_of(false)
    private var scanJob: Job? = null

    // events to be collected in the UI
    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    private var monitorJob: Job? = null

    init {
        incrementLaunchCount()
        tryAutoReconnectOnStart()
        startMonitoring()
    }

    private fun incrementLaunchCount() {
        val launchCount = sharedPref.getInt("launch_count", 0) + 1
        sharedPref.edit { putInt("launch_count", launchCount) }
    }

    /**
     * Start a scan for OpenRGB servers. Cancels prior scan if running.
     */
    fun scanForServers() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            try {
                isRefreshingState.value = true
                val list = withContext(Dispatchers.IO) { scanNetworkForOpenRGB() }
                availableServersState.value = list
            } catch (e: Exception) {
                Log.w("MainViewModel", "scan failed", e)
                _events.tryEmit(UiEvent.ShowSnackbar("Scan failed: ${e.message ?: "unknown"}"))
            } finally {
                isRefreshingState.value = false
            }
        }
    }

    /**
     * Refresh devices from the currently connected client (if any).
     */
    fun refreshDevicesFromClient() {
        val c = clientState.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val count = try { c.getControllerCount() } catch (e: Exception) { 0 }
                val refreshed = mutableListOf<OpenRGBDevice>()
                for (i in 0 until count) {
                    try {
                        val dev = c.getDeviceController(i)
                        refreshed.add(dev)
                    } catch (e: Exception) {
                        Log.w("MainViewModel", "Failed to fetch device $i", e)
                    }
                }
                devicesState.value = refreshed
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error refreshing devices", e)
            }
        }
    }

    /**
     * Connect to a server (unsafe fast connect helper is used to return client+devices).
     * Updates clientState, devicesState, connectedState and currentServerState on success.
     */
    fun connectTo(server: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val (newClient, devicesList) = connectToServerUnsafe(server, sharedPref)
                    // NOTE: connectToServerUnsafe is assumed to return Pair<OpenRGBClient, List<OpenRGBDevice>>
                    clientState.value = newClient
                    devicesState.value = devicesList
                    connectedState.value = true
                    currentServerState.value = server
                    sharedPref.edit { putString("last_connected_server", server) }
                    _events.tryEmit(UiEvent.ShowSnackbar("Connected to $server"))
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Connect failed: ${e.message}", e)
                showReconnectErrorState.value = true
                _events.tryEmit(UiEvent.ShowReconnectError)
            }
        }
    }

    /**
     * Disconnect current client (if any).
     */
    fun disconnect(userInitiated: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                disconnectFromServer(clientState, connectedState, devicesState, /*shouldReconnect placeholder*/ mutableStateOf(true), userInitiated)
            } catch (e: Exception) {
                Log.w("MainViewModel", "disconnect error", e)
            } finally {
                clientState.value = null
                connectedState.value = false
                devicesState.value = emptyList()
                currentServerState.value = ""
            }
        }
    }

    private fun tryAutoReconnectOnStart() {
        if (!isFirstLaunch) {
            val lastServer = sharedPref.getString("last_connected_server", "")
            if (!lastServer.isNullOrEmpty()) {
                viewModelScope.launch {
                    try {
                        withTimeout(5_000) {
                            val (newClient, devicesList) = withContext(Dispatchers.IO) {
                                connectToServerUnsafe(lastServer, sharedPref)
                            }
                            clientState.value = newClient
                            devicesState.value = devicesList
                            connectedState.value = true
                            currentServerState.value = lastServer
                        }
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "Reconnect failed: ${e.message}", e)
                        showReconnectErrorState.value = true
                        _events.tryEmit(UiEvent.ShowReconnectError)
                    }
                }
            }
        }
    }

    /**
     * Background monitoring for WiFi + server reachability.
     * Runs in viewModelScope and updates state accordingly.
     */
    private fun startMonitoring() {
        monitorJob?.cancel()
        monitorJob = viewModelScope.launch {
            while (isActive) {
                delay(3_000)
                try {
                    val appCtx = getApplication<Application>().applicationContext
                    val wifiStatus = isWifiConnected(appCtx)

                    if (isWifiConnectedState.value != wifiStatus) {
                        isWifiConnectedState.value = wifiStatus
                        if (!wifiStatus) {
                            showWifiWarningState.value = true
                            connectedState.value = false
                            showServerShutdownWarningState.value = false
                            _events.tryEmit(UiEvent.ShowWifiWarning)
                        } else {
                            showWifiWarningState.value = false
                            val lastServer = sharedPref.getString("last_connected_server", "")
                            if (!lastServer.isNullOrEmpty() && !connectedState.value) {
                                connectTo(lastServer)
                            }
                        }
                    }

                    // Check server reachability only if wifi connected && we think we're connected
                    if (wifiStatus && connectedState.value && currentServerState.value.isNotBlank()) {
                        try {
                            val parts = currentServerState.value.split(":")
                            val ip = parts[0]
                            val port = parts.getOrNull(1)?.toIntOrNull() ?: 6742
                            withContext(Dispatchers.IO) {
                                Socket().use { socket ->
                                    socket.connect(InetSocketAddress(ip, port), 1000)
                                }
                            }
                            isServerReachableState.value = true
                        } catch (e: Exception) {
                            Log.w("MainViewModel", "Server unreachable", e)
                            if (isWifiConnectedState.value) {
                                connectedState.value = false
                                showServerShutdownWarningState.value = true
                                _events.tryEmit(UiEvent.ShowServerShutdownWarning)
                            }
                            isServerReachableState.value = false
                        }
                    }
                } catch (e: Exception) {
                    Log.w("MainViewModel", "monitor exception", e)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        monitorJob?.cancel()
        scanJob?.cancel()
    }

    /**
     * Mapira lokalni index unutar zone -> globalni LED index u device.leds.
     * Ako zona sadrži eksplicitnu listu segmenata (segments) ili interni mapping,
     * pokušava koristiti onu mapu. Inače, koristi sumu prethodnih zona.
     */
    fun mapZoneLocalToGlobalLedIndex(device: OpenRGBDevice, zoneIndex: Int, localIndex: Int): Int {
        val zones = device.zones ?: throw IndexOutOfBoundsException("Device has no zones")
        if (zoneIndex !in zones.indices) throw IndexOutOfBoundsException("zoneIndex out of range")
        val zone = zones[zoneIndex]

        // Ako zona ima segmente, pokušaj koristiti startIdx iz segmenta (najjednostavnije: samo linearno kroz segmente)
        if (zone.segments.isNotEmpty()) {
            var remaining = localIndex
            for (seg in zone.segments) {
                if (remaining < seg.ledsCount) {
                    return seg.startIdx + remaining
                } else {
                    remaining -= seg.ledsCount
                }
            }
            throw IndexOutOfBoundsException("localIndex out of range for segments")
        }

        // fallback: sumiraj ledsCount od prethodnih zona
        var offset = 0
        for (i in 0 until zoneIndex) offset += zones[i].ledsCount
        return offset + localIndex
    }

    /* -------------------------------------------------------
       START: IMPORTANT - Serialisation & custom-mode guard
       ------------------------------------------------------- */

    // Mutex to ensure only one OpenRGB command-block runs at a time.
    // This avoids racing SET_CUSTOM_MODE vs UPDATE_* which caused broken pipe.
    private val openRgbMutex = Mutex()

    // Remember which devices already had SET_CUSTOM_MODE sent (to avoid spam)
    private val customModeSetForDevice = mutableSetOf<Int>()

    /**
     * Poziva setCustomMode na serveru za dati deviceIndex.
     * Ova funkcija sada radi sigurno (lock-ano) i neće uzrokovati race
     * ako se parallelno pozove iz UI-a ili update funkcija.
     *
     * Ako želiš eksplicitno zatražiti setCustomMode iz UI,
     * možeš pozvati ovu funkciju — ali nije neophodno jer applyColor... to radi automatski.
     */
    fun ensureDeviceCustomMode(client: OpenRGBClient?, deviceIndex: Int) {
        client ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                openRgbMutex.withLock {
                    if (!customModeSetForDevice.contains(deviceIndex)) {
                        client.setCustomMode(deviceIndex)
                        // store that we've set custom mode for this device
                        customModeSetForDevice.add(deviceIndex)
                        // small safety delay to give server a moment to process
                        delay(12)
                    }
                }
            } catch (e: Exception) {
                Log.w("MainViewModel", "setCustomMode failed for device $deviceIndex: ${e.message}", e)
            }
        }
    }

    /* -------------------------------------------------------
       END: Serialisation & custom-mode guard
       ------------------------------------------------------- */

    /**
     * Primijeni boju na cijelu zonu (batch updateZoneLeds).
     * SADA: koristi Mutex i osigurava da se SET_CUSTOM_MODE pošalje PRVO.
     */
    fun applyColorToZone(deviceIndex: Int, zoneIndex: Int, color: me.kavishdevar.openrgb.models.OpenRGBColor) {
        val client = clientState.value ?: return
        val device = devicesState.value.getOrNull(deviceIndex) ?: return
        val zone = device.zones.getOrNull(zoneIndex) ?: return

        val colors = List(zone.ledsCount) { color }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                openRgbMutex.withLock {
                    // ensure custom mode only if not already set for this device
                    if (!customModeSetForDevice.contains(deviceIndex)) {
                        try {
                            client.setCustomMode(deviceIndex)
                            customModeSetForDevice.add(deviceIndex)
                        } catch (e: Exception) {
                            Log.w("MainViewModel", "setCustomMode inside applyColorToZone failed: ${e.message}", e)
                            // still attempt update; but server may close socket -> handler below will catch
                        }
                        // brief pause to allow server to register
                        delay(12)
                    }
                    // now safe to update zone leds
                    client.updateZoneLeds(deviceIndex, zoneIndex, colors)
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "applyColorToZone failed: ${e.message}", e)
                viewModelScope.launch {
                    _events.tryEmit(UiEvent.ShowSnackbar("Failed to update zone: ${e.message}"))
                }
            }
        }
    }

    /**
     * Primijeni boju na pojedinačnu LED (mapa local->global).
     * SADA: koristi Mutex i osigurava SET_CUSTOM_MODE -> UPDATE_SINGLE_LED
     */
    fun applyColorToLed(deviceIndex: Int, zoneIndex: Int, localLedIndex: Int, color: me.kavishdevar.openrgb.models.OpenRGBColor) {
        val client = clientState.value ?: return
        val device = devicesState.value.getOrNull(deviceIndex) ?: return

        val globalIndex = try {
            mapZoneLocalToGlobalLedIndex(device, zoneIndex, localLedIndex)
        } catch (e: Exception) {
            Log.e("MainViewModel", "mapZoneLocalToGlobalLedIndex failed: ${e.message}", e)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                openRgbMutex.withLock {
                    // ensure custom mode
                    if (!customModeSetForDevice.contains(deviceIndex)) {
                        try {
                            client.setCustomMode(deviceIndex)
                            customModeSetForDevice.add(deviceIndex)
                        } catch (e: Exception) {
                            Log.w("MainViewModel", "setCustomMode inside applyColorToLed failed: ${e.message}", e)
                        }
                        delay(12)
                    }
                    client.updateLed(deviceIndex, globalIndex, color)
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "applyColorToLed failed: ${e.message}", e)
                _events.tryEmit(UiEvent.ShowSnackbar("Failed to update LED: ${e.message}"))
            }
        }
    }
    private var pendingZoneUpdateJob: Job? = null

    /**
     * Debounced wrapper za zone: odgađa slanje dok korisnik ne prestane mijenjati slider.
     * Debounce samo poziva applyColorToZone koja sada radi ispravno (lock + custom-mode).
     */
    fun debounceApplyColorToZone(deviceIndex: Int, zoneIndex: Int, color: me.kavishdevar.openrgb.models.OpenRGBColor, delayMs: Long = 120L) {
        pendingZoneUpdateJob?.cancel()
        pendingZoneUpdateJob = viewModelScope.launch(Dispatchers.IO) {
            delay(delayMs)
            applyColorToZone(deviceIndex, zoneIndex, color)
        }
    }

    /**
     * Refresh single device from server and update local devicesState.
     */
    fun refreshSingleDevice(deviceIndex: Int) {
        val client = clientState.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dev = client.getDeviceController(deviceIndex)
                withContext(Dispatchers.Main) {
                    val copy = devicesState.value.toMutableList()
                    if (deviceIndex in copy.indices) copy[deviceIndex] = dev
                    devicesState.value = copy
                }
            } catch (e: Exception) {
                Log.w("MainViewModel", "refreshSingleDevice failed: ${e.message}")
            }
        }
    }

    /**
     * Update mode safely (lock-ano). Accepts deviceIndex, modeIndex and the new mode object.
     * This ensures setCustomMode (if needed) + updateMode happen in same critical section.
     */
    fun updateMode(deviceIndex: Int, modeIndex: Int, updatedMode: OpenRGBMode) {
        val client = clientState.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                openRgbMutex.withLock {
                    if (!customModeSetForDevice.contains(deviceIndex)) {
                        try {
                            client.setCustomMode(deviceIndex)
                            customModeSetForDevice.add(deviceIndex)
                            delay(12)
                        } catch (e: Exception) {
                            // log but continue to attempt update
                            Log.w("MainViewModel", "setCustomMode for updateMode failed: ${e.message}", e)
                        }
                    }
                    client.updateMode(deviceIndex, modeIndex, updatedMode)
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "updateMode failed: ${e.message}", e)
                _events.tryEmit(UiEvent.ShowSnackbar("Failed to update mode: ${e.message}"))
            }
        }
    }

    /**
     * Convenience that finds modeIndex from mode object and calls updateMode.
     */
    fun updateModeIfSupported(deviceIndex: Int, mode: OpenRGBMode, speed: Int, brightness: Int, color: Color) {
        val device = devicesState.value.getOrNull(deviceIndex) ?: return
        val modeIndex = device.modes.indexOfFirst { it.name == mode.name && it.value == mode.value }.takeIf { it != -1 } ?: device.activeMode
        val rgb = OpenRGBColor((color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt())
        val updated = mode.copy(
            speed = speed,
            brightness = brightness,
            colors = if (mode.hasModeSpecificColor()) listOf(rgb) else mode.colors
        )
        updateMode(deviceIndex, modeIndex, updated)
    }






}

/**
 * Simple ViewModel factory so MainActivity can pass SharedPreferences + isFirstLaunch
 */
class MainViewModelFactory(
    private val application: Application,
    private val sharedPref: SharedPreferences,
    private val isFirstLaunch: Boolean
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(application, sharedPref, isFirstLaunch) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

/**
 * Helpers to avoid importing Compose runtime helpers repeatedly (small convenience functions).
 * If you prefer, import mutableStateOf directly where used — these are here to keep example code compact.
 */
private fun <T> mutable_state_of(initial: T) = mutableStateOf(initial)
private fun <T> mutable_state_of_nullable() = mutableStateOf<T?>(null)
private fun mutable_state_of(falseValue: Boolean) = mutableStateOf(falseValue)
private fun mutable_state_of_safe(initial: String) = mutableStateOf(initial)
private fun mutable_state_of_nullable_int_size() = mutableStateOf<androidx.compose.ui.unit.IntSize?>(null)
