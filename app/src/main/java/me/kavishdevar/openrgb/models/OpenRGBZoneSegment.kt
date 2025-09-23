package me.kavishdevar.openrgb.models

import me.kavishdevar.openrgb.protocol.ZoneType

data class OpenRGBZoneSegment(
    val name: String,
    val type: ZoneType,
    val startIdx: Int,
    val ledsCount: Int
)