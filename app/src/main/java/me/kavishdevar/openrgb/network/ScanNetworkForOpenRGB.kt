package me.kavishdevar.openrgb.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Collections

suspend fun scanNetworkForOpenRGB(
    port: Int = 6742,
    maxConcurrency: Int = 100,
    timeoutMs: Int = 500  // increase if you see false negatives on slow networks
): List<String> {
    val foundServers = Collections.synchronizedList(mutableListOf<String>())

    // Determine local IP and subnet prefix (xxx.xxx.xxx)
    val localIp = getLocalIPv4Address()
    val subnetPrefix = localIp?.substringBeforeLast(".") ?: run {
        Log.w("OpenRGB", "Could not determine local IP, falling back to 192.168.1")
        "192.168.1"
    }

    // scan hosts 1..254 (skip .0 and .255)
    val hosts = (1..254).toList()

    // semaphore to limit concurrency
    val semaphore = Semaphore(maxConcurrency)

    // coroutineScope ensures cancellation propagates when scanJob is cancelled elsewhere
    coroutineScope {
        val jobs = hosts.map { h ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val ip = "$subnetPrefix.$h"
                    try {
                        Socket().use { sock ->
                            sock.soTimeout = timeoutMs
                            sock.connect(InetSocketAddress(ip, port), timeoutMs)
                            if (sock.isConnected) {
                                Log.d("OpenRGB", "Found server at $ip:$port")
                                foundServers.add("$ip:$port")
                                sock.close()
                            }
                        }
                    } catch (e: Exception) {
                        // silent fail - logging for debug
                        Log.d("OpenRGB", "Connection test failed for $ip:$port: ${e.message}")
                    }
                }
            }
        }
        // wait for all attempts to finish (or job cancellation)
        jobs.awaitAll()
    }

    // optionally sort/unique
    return foundServers.toSet().sorted()
}