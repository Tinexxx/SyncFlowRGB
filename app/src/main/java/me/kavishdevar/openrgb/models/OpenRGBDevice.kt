package me.kavishdevar.openrgb.models

data class OpenRGBDevice(
    val type: DeviceType = DeviceType.DEVICE_TYPE_UNKNOWN,
    val name: String = "",
    val vendor: String = "",
    val description: String = "",
    val version: String = "",
    val serial: String = "",
    val location: String = "",
    val activeMode: Int = 0,
    val modes: List<OpenRGBMode> = emptyList(),
    val zones: List<OpenRGBZone> = emptyList(),
    val leds: List<OpenRGBLed> = emptyList(),
    val colors: List<OpenRGBColor> = emptyList()
)