package me.kavishdevar.openrgb.models

data class OpenRGBColor(
    val red: Int,
    val green: Int,
    val blue: Int
) {
    fun toInt(): Int = (red shl 16) or (green shl 8) or blue

    companion object {
        fun fromInt(value: Int): OpenRGBColor {
            return OpenRGBColor(
                red = (value shr 16) and 0xFF,
                green = (value shr 8) and 0xFF,
                blue = value and 0xFF
            )
        }
    }
}