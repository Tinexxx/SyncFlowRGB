package me.kavishdevar.openrgb.logic

import kotlinx.coroutines.*
import me.kavishdevar.openrgb.models.Lightshow
import me.kavishdevar.openrgb.network.OpenRGBClient
import android.util.Log
import me.kavishdevar.openrgb.models.OpenRGBDevice

class LightshowPlayer(
    private val client: OpenRGBClient?,
    private val devices: List<OpenRGBDevice>
) {
    private var job: Job? = null
    private var elapsed = 0.0

    // deviceIndex -> last profile name applied
    private val lastAppliedProfile = mutableMapOf<Int, String?>()
    // deviceIndex -> exclusive end seconds of the last-applied block
    private val lastAppliedUntil = mutableMapOf<Int, Double>()

    fun play(show: Lightshow, onTick: (Double) -> Unit) {
        stop()
        job = CoroutineScope(Dispatchers.IO).launch {
            val start = System.currentTimeMillis()
            while (isActive) {
                elapsed = (System.currentTimeMillis() - start) / 1000.0
                onTick(elapsed)

                show.tracks.forEach { track ->
                    val deviceIndex = track.deviceIndex

                    // find the clip active right now for this track (if any)
                    val activeClip = track.clips.find { clip ->
                        elapsed >= clip.startSeconds && elapsed < (clip.startSeconds + clip.durationSeconds)
                    }

                    if (activeClip != null) {
                        val lastProfile = lastAppliedProfile[deviceIndex]
                        val lastUntil = lastAppliedUntil[deviceIndex] ?: -1.0

                        // Only apply if:
                        //  - profile changed since last applied, OR
                        //  - this clip starts after the last applied block ended (so it's a new block)
                        //
                        // Important: if the new clip starts exactly at previous end and uses the same profile,
                        // we treat that as continuous and DO NOT reapply (avoids the constant reload/glitch).
                        val needApply = if (lastProfile == activeClip.profileName) {
                            activeClip.startSeconds > lastUntil
                        } else {
                            true
                        }

                        if (needApply) {
                            // record that we applied this profile until the clip's end
                            lastAppliedProfile[deviceIndex] = activeClip.profileName
                            lastAppliedUntil[deviceIndex] = activeClip.startSeconds + activeClip.durationSeconds

                            kotlin.runCatching {
                                client?.loadProfile(activeClip.profileName)
                                Log.d(
                                    "Lightshow",
                                    "Sent ${activeClip.profileName} to device $deviceIndex @ ${"%.2f".format(elapsed)}s"
                                )
                            }.onFailure {
                                Log.d("Lightshow", "Failed to load profile '${activeClip.profileName}': ${it.message}")
                            }
                        }
                        // else: already applied and still within block (or contiguous same profile) -> do nothing
                    } else {
                        // no clip active right now for this device: clear applied state so future clips get applied
                        lastAppliedProfile.remove(deviceIndex)
                        lastAppliedUntil.remove(deviceIndex)
                    }
                }

                delay(100L) // tick rate — keep reasonable to avoid flooding the OpenRGB server
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        // clear state so next play starts fresh
        lastAppliedProfile.clear()
        lastAppliedUntil.clear()
    }
}
