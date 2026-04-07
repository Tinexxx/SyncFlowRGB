package me.kavishdevar.openrgb.network

import android.content.SharedPreferences
import me.kavishdevar.openrgb.models.OpenRGBDevice

fun connectToServerUnsafe(
    server: String,
    sharedPref: SharedPreferences
): Pair<OpenRGBClient, List<OpenRGBDevice>> {
    val parts = server.split(":")
    if (parts.size != 2) throw IllegalArgumentException("Invalid server format")

    val ip = parts[0]
    val port = parts[1].toIntOrNull() ?: 6742

    val newClient = OpenRGBClient(ip, port, "OpenRGB Android")
    newClient.connect()

    val count = newClient.getControllerCount()
    val devices = mutableListOf<OpenRGBDevice>()

    for (i in 0 until count) {
        devices.add(newClient.getDeviceController(i))
    }

    sharedPref.edit()
        .putBoolean("first_launch", false)
        .putString("last_connected_server", server)
        .apply()

    return newClient to devices
}