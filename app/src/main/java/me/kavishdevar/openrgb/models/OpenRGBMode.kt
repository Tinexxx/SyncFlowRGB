package me.kavishdevar.openrgb.models

import me.kavishdevar.openrgb.protocol.ColorMode
import me.kavishdevar.openrgb.protocol.OpenRGBProtocol
import java.util.Objects

data class OpenRGBMode(
    val name: String = "",
    val value: Int = 0,
    val flags: Int = 0,
    val speedMin: Int = 0,
    val speedMax: Int = 0,
    val brightnessMin: Int = 0,
    val brightnessMax: Int = 100,
    val colorMin: Int = 0,
    val colorMax: Int = 0,
    val speed: Int = 0,
    val brightness: Int = 100,
    val direction: ModeDirection = ModeDirection.MODE_DIRECTION_LEFT,
    val colorMode: ColorMode = ColorMode.MODE_COLORS_NONE,
    val colors: List<OpenRGBColor> = emptyList()
) {
    fun hasSpeed(): Boolean = flags and OpenRGBProtocol.ModeFlags.HAS_SPEED != 0
    fun hasDirectionLR(): Boolean = flags and OpenRGBProtocol.ModeFlags.HAS_DIRECTION_LR != 0
    fun hasDirectionUD(): Boolean = flags and OpenRGBProtocol.ModeFlags.HAS_DIRECTION_UD != 0
    fun hasDirectionHV(): Boolean = flags and OpenRGBProtocol.ModeFlags.HAS_DIRECTION_HV != 0
    fun hasDirection(): Boolean = hasDirectionLR() || hasDirectionUD() || hasDirectionHV()
    fun hasBrightness(): Boolean = flags and OpenRGBProtocol.ModeFlags.HAS_BRIGHTNESS != 0
    fun hasPerLedColor(): Boolean = flags and OpenRGBProtocol.ModeFlags.HAS_PER_LED_COLOR != 0
    fun hasModeSpecificColor(): Boolean = flags and OpenRGBProtocol.ModeFlags.HAS_MODE_SPECIFIC_COLOR != 0
    fun hasRandomColor(): Boolean = flags and OpenRGBProtocol.ModeFlags.HAS_RANDOM_COLOR != 0
    fun hasManualSave(): Boolean = flags and OpenRGBProtocol.ModeFlags.MANUAL_SAVE != 0
    fun hasAutoSave(): Boolean = flags and OpenRGBProtocol.ModeFlags.AUTOMATIC_SAVE != 0

    // In OpenRGBMode.kt, update equals and hashCode methods:
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OpenRGBMode

        return name == other.name &&
                value == other.value &&
                flags == other.flags &&
                speedMin == other.speedMin &&
                speedMax == other.speedMax &&
                brightnessMin == other.brightnessMin &&
                brightnessMax == other.brightnessMax &&
                colorMin == other.colorMin &&
                colorMax == other.colorMax &&
                speed == other.speed &&
                brightness == other.brightness &&
                direction == other.direction && // Add direction comparison
                colorMode == other.colorMode &&
                colors == other.colors
    }

    override fun hashCode(): Int {
        return Objects.hash(
            name, value, flags, speedMin, speedMax,
            brightnessMin, brightnessMax, colorMin, colorMax,
            speed, brightness, direction, colorMode, colors // Include direction
        )
    }
}
