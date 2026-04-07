package me.kavishdevar.openrgb.logic

import androidx.compose.runtime.Composable
import me.kavishdevar.openrgb.models.OpenRGBDevice
import me.kavishdevar.openrgb.network.OpenRGBClient
import me.kavishdevar.openrgb.screens.LightshowScreen

@Composable
fun LightshowCaller(
    client: OpenRGBClient?,
    devices: List<OpenRGBDevice>,
    connected: Boolean
) {


    LightshowScreen(
        client = client,
        devices = devices,
        connected = connected
    )
}