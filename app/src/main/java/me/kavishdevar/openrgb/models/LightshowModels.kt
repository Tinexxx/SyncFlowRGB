package me.kavishdevar.openrgb.models

import java.util.*

/**
 * Lightshow project data model
 */

data class Lightshow(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "New Lightshow",
    var tracks: List<Track> = mutableListOf(),
    var lengthSeconds: Long = 60L // convenience, computed from clips if needed
)

data class Track(
    val id: String = UUID.randomUUID().toString(),
    val deviceIndex: Int,
    var name: String,
    var clips: MutableList<Clip> = mutableListOf()
)

data class Clip(
    val id: String = UUID.randomUUID().toString(),
    val profileName: String,    // profile (server) name or local profile name
    val deviceIndex: Int,       // convenience to know which device this applies to
    var startSeconds: Double,   // start time in seconds (double for fractional)
    var durationSeconds: Double // duration in seconds
)
