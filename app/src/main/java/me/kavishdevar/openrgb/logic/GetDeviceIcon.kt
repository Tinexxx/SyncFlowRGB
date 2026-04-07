package me.kavishdevar.openrgb.logic

import me.kavishdevar.openrgb.R
import me.kavishdevar.openrgb.models.DeviceType

fun getDeviceIcon(type: DeviceType): Int {
    return when {

        type.name.contains("MOTHERBOARD") -> R.drawable.motherboard
        type.name.contains("GAMEPAD") -> R.drawable.gamepad
        type.name.contains("KEYBOARD") -> R.drawable.keyboard
        type.name.contains("MOUSE") -> R.drawable.mouse
        else -> R.drawable.device
    }
}