package me.kavishdevar.openrgb.network

import android.content.SharedPreferences
import android.util.Log
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.MutableState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kavishdevar.openrgb.models.OpenRGBDevice
import java.io.IOException
import java.net.SocketTimeoutException

fun connectToServer(
    server: String,
    client: MutableState<OpenRGBClient?>,
    connected: MutableState<Boolean>,
    devices: MutableState<List<OpenRGBDevice>>,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
    sharedPref: SharedPreferences,
    shouldReconnect: MutableState<Boolean>
) {
    scope.launch {
        // Disconnect first if already connected
        if (connected.value && client.value != null) {
            client.value?.disconnect()
            connected.value = false
            devices.value = emptyList()
        }

        try {
            val parts = server.split(":")
            if (parts.size != 2) {
                throw IllegalArgumentException("Invalid server format - expected 'ip:port'")
            }

            val ip = parts[0]
            val port = parts[1].toIntOrNull() ?: 6742

            Log.d("OpenRGB", "Attempting to connect to $ip:$port")

            val newClient = OpenRGBClient(ip, port, "OpenRGB Android")

            withContext(Dispatchers.IO) {
                try {
                    newClient.connect()
                    val count = newClient.getControllerCount()
                    Log.d("OpenRGB", "Found $count controllers")

                    val deviceList = mutableListOf<OpenRGBDevice>()
                    for (i in 0 until count) {
                        try {
                            val device = newClient.getDeviceController(i)
                            deviceList.add(device)
                            Log.d("OpenRGB", "Loaded device: ${device.name}")
                        } catch (e: Exception) {
                            Log.e("OpenRGB", "Error loading device $i", e)
                            withContext(Dispatchers.Main) {
                                snackbarHostState.showSnackbar("Error loading device $i: ${e.message?.take(50)}...")
                            }
                        }
                    }

                    withContext(Dispatchers.Main) {
                        client.value = newClient
                        connected.value = true
                        devices.value = deviceList
                        shouldReconnect.value = true // Enable reconnection for this server
                        snackbarHostState.showSnackbar(
                            "Connected to $server (v${newClient.protocolVersion})"
                        )

                        sharedPref.edit()
                            .putBoolean("first_launch", false)
                            .putString("last_connected_server", server)
                            .apply()
                    }
                } catch (e: Exception) {
                    Log.e("OpenRGB", "Connection error", e)
                    withContext(Dispatchers.Main) {
                        connected.value = false
                        devices.value = emptyList()
                        shouldReconnect.value = false // Disable reconnection if failed
                        val errorMsg = when {
                            e is IOException && e.message?.contains("ECONNREFUSED") == true ->
                                "Connection refused. Is OpenRGB server running?"
                            e is SocketTimeoutException ->
                                "Connection timeout. Check server and network."
                            e is IllegalArgumentException ->
                                "Invalid server address: ${e.message}"
                            else -> "Connection failed: ${e.message?.take(50)}..."
                        }
                        snackbarHostState.showSnackbar(errorMsg)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("OpenRGB", "Initial setup error", e)
            withContext(Dispatchers.Main) {
                connected.value = false
                devices.value = emptyList()
                shouldReconnect.value = false
                snackbarHostState.showSnackbar("Setup error: ${e.message?.take(50)}...")
            }
        }
    }
}