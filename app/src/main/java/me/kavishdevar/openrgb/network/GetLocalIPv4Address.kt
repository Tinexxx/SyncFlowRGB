package me.kavishdevar.openrgb.network

import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

fun getLocalIPv4Address(): String? {
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        for (intf in Collections.list(interfaces)) {
            for (addr in Collections.list(intf.inetAddresses)) {
                if (!addr.isLoopbackAddress && addr is Inet4Address) {
                    return addr.hostAddress // e.g. "192.168.2.45"
                }
            }
        }
    } catch (e: Exception) {
        Log.w("OpenRGB", "Failed to enumerate network interfaces: ${e.message}")
    }
    return null
}
