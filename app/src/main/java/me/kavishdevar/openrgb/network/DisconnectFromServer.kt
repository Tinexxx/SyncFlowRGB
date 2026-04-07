package me.kavishdevar.openrgb.network

import androidx.compose.runtime.MutableState
import me.kavishdevar.openrgb.models.OpenRGBDevice

fun disconnectFromServer(
    client: MutableState<OpenRGBClient?>,
    connected: MutableState<Boolean>,
    devices: MutableState<List<OpenRGBDevice>>,
    shouldReconnect: MutableState<Boolean>,
    userInitiated: Boolean = false
) {
    client.value?.disconnect()
    client.value = null
    connected.value = false
    devices.value = emptyList()

    if (userInitiated) {
        shouldReconnect.value = false
    }
}