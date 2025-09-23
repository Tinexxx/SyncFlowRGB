package me.kavishdevar.openrgb.models

import me.kavishdevar.openrgb.protocol.ZoneType


data class OpenRGBZone(
    val name: String,
    val type: ZoneType,
    val ledsMin: Int,
    val ledsMax: Int,
    val ledsCount: Int,
    val segments: List<OpenRGBZoneSegment> = emptyList(),
    val flags: Int = 0
)