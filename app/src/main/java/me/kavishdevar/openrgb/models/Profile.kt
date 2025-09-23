// Create new file Profile.kt
package me.kavishdevar.openrgb.models

data class OpenRGBProfile(
    val name: String,
    val devices: List<String> = emptyList(), // Devices this profile affects
    val isActive: Boolean = false
)


