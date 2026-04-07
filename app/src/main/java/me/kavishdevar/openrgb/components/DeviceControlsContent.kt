package me.kavishdevar.openrgb.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.kavishdevar.openrgb.models.OpenRGBColor
import me.kavishdevar.openrgb.models.OpenRGBDevice
import me.kavishdevar.openrgb.models.OpenRGBMode
import me.kavishdevar.openrgb.ui_logic.MainViewModel

/**
 * DeviceControlsContent - updated UI:
 *  - Replaced the MatrixEditorVertical with simple Zone -> LED dropdowns
 *  - Color picker applies to the selected LED
 */

@Composable
fun DeviceControlsContent(
    device: OpenRGBDevice,
    client: Any?, // kept for parity in signature; not used directly here
    deviceIndex: Int,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(8.dp)) {

        Text(text = device.name ?: "Device", color = Color.White)

        Spacer(modifier = Modifier.height(8.dp))

        // Use first available mode as current or fallback
        var currentMode by remember { mutableStateOf<OpenRGBMode?>(device.modes.getOrNull(device.activeMode) ?: device.modes.firstOrNull()) }

        // Keep pending numeric state (use simple remember; we convert UI -> device values when sending)
        var pendingBrightness by rememberSaveable { mutableStateOf(currentMode?.brightness?.toFloat() ?: 100f) }

        // UI speed IS a float in device range (we show higher = FASTER visually by inverting mapping functions).
        var pendingSpeedUi by rememberSaveable { mutableStateOf(currentMode?.let { deviceSpeedToUi(it.speed, it.speedMin, it.speedMax).toFloat() } ?: (currentMode?.speed?.toFloat() ?: 0f)) }

        Spacer(modifier = Modifier.height(8.dp))

        // Mode selector
        DropdownMenu(
            modes = device.modes,
            selectedMode = currentMode,
            onModeSelected = { mode ->
                // update current mode in UI
                currentMode = mode
                // reset ui pending values to mode defaults
                pendingBrightness = mode.brightness.toFloat()
                pendingSpeedUi = deviceSpeedToUi(mode.speed, mode.speedMin, mode.speedMax).toFloat()

                // Build updated mode with current numeric fields & preserve/attach colors if mode supports them
                val colorForMode = mode.colors.firstOrNull()
                    ?: device.colors.firstOrNull()
                    ?: OpenRGBColor(255, 255, 255)

                // Convert to Compose Color to reuse helper when needed
                val composeColor = Color(colorForMode.red / 255f, colorForMode.green / 255f, colorForMode.blue / 255f)

                val modeIndex =
                    device.modes.indexOfFirst { it.name == mode.name && it.value == mode.value }
                        .takeIf { it != -1 } ?: device.activeMode

                // update remote/server mode via ViewModel
                val updated = mode.copy(
                    speed = uiToDeviceSpeed(pendingSpeedUi.toInt(), mode.speedMin, mode.speedMax),
                    brightness = pendingBrightness.toInt(),
                    colors = if (mode.hasModeSpecificColor()) listOf(colorForMode) else mode.colors
                )
                viewModel.updateMode(deviceIndex, modeIndex, updated)
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // If mode supports mode-specific color -> show color picker
        currentMode?.let { mode ->
            if (mode.hasModeSpecificColor()) {
                Text("Mode color", color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))

                // determine initial color from mode or device colors
                val initialModeColor = mode.colors.firstOrNull()
                    ?: device.colors.firstOrNull()
                    ?: OpenRGBColor(255, 255, 255)

                var currentModeColor by remember { mutableStateOf(Color(initialModeColor.red / 255f, initialModeColor.green / 255f, initialModeColor.blue / 255f)) }

                CompactHsvColorPicker(
                    initialColor = currentModeColor,
                    onColorChange = { newColor ->
                        currentModeColor = newColor
                        val rgb = OpenRGBColor(
                            (newColor.red * 255).toInt(),
                            (newColor.green * 255).toInt(),
                            (newColor.blue * 255).toInt()
                        )
                        val modeIdx = device.modes.indexOfFirst { it.name == mode.name && it.value == mode.value }
                            .takeIf { it != -1 } ?: device.activeMode
                        val updatedMode = mode.copy(
                            colors = listOf(rgb),
                            brightness = pendingBrightness.toInt(),
                            speed = uiToDeviceSpeed(pendingSpeedUi.toInt(), mode.speedMin, mode.speedMax)
                        )
                        viewModel.updateMode(deviceIndex, modeIdx, updatedMode)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                )

                Spacer(Modifier.height(12.dp))
            } else if (mode.hasPerLedColor()) {
                // ---- NEW: Replace Matrix Editor with Zone + LED dropdowns + color picker ----
                Text("Per-LED Control", color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))

                val zones = device.zones
                var selectedZoneIndex by remember { mutableStateOf(0) }
                val selectedZone = zones.getOrNull(selectedZoneIndex)

                var selectedLedIndex by remember { mutableStateOf(0) }

                // ZONE DROPDOWN
                DropdownSelector(
                    label = "Zone",
                    items = zones.map { it.name },
                    selectedIndex = selectedZoneIndex,
                    onSelected = {
                        selectedZoneIndex = it
                        selectedLedIndex = 0 // reset LED index when changing zone
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // LED DROPDOWN
                selectedZone?.let { zone ->
                    DropdownSelector(
                        label = "LED",
                        items = (0 until zone.ledsCount).map { "LED $it" },
                        selectedIndex = selectedLedIndex,
                        onSelected = { selectedLedIndex = it }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Color picker applies to the selected LED
                var ledColor by remember { mutableStateOf(Color.White) }

                // Try to read the current LED color from device when zone/led changes
                LaunchedEffect(selectedZoneIndex, selectedLedIndex, device.leds) {
                    try {
                        val globalIdx = viewModel.mapZoneLocalToGlobalLedIndex(device, selectedZoneIndex, selectedLedIndex)
                        if (globalIdx >= 0) {
                            val led = device.leds.getOrNull(globalIdx)
                            if (led != null) {
                                val c = me.kavishdevar.openrgb.models.OpenRGBColor.fromInt(led.value)
                                ledColor = Color(c.red / 255f, c.green / 255f, c.blue / 255f)
                            }
                        }
                    } catch (_: Exception) {
                        // ignore and keep default
                    }
                }

                CompactHsvColorPicker(
                    initialColor = ledColor,
                    onColorChange = { newColor ->
                        ledColor = newColor

                        val rgb = OpenRGBColor(
                            (newColor.red * 255).toInt(),
                            (newColor.green * 255).toInt(),
                            (newColor.blue * 255).toInt()
                        )

                        try {
                            viewModel.applyColorToLed(
                                deviceIndex = deviceIndex,
                                zoneIndex = selectedZoneIndex,
                                localLedIndex = selectedLedIndex,
                                color = rgb
                            )
                        } catch (_: Exception) {
                            // ignore errors in mapping/applying
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))
                // -----------------------------------------------------------------------
            } else {
                Text("This mode doesn't support color editing.", color = Color.LightGray)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Brightness slider (if supported)
        currentMode?.takeIf { it.hasBrightness() }?.let { mode ->
            val isValidBrightnessRange = mode.brightnessMax > mode.brightnessMin

            Text("Brightness: ${pendingBrightness.toInt()}%", color = Color.White)
            Spacer(modifier = Modifier.height(6.dp))

            if (isValidBrightnessRange) {
                Slider(
                    value = pendingBrightness,
                    onValueChange = { v ->
                        pendingBrightness = v.coerceIn(mode.brightnessMin.toFloat(), mode.brightnessMax.toFloat())
                        val modeIndex = device.modes.indexOfFirst { it.name == mode.name && it.value == mode.value }
                            .takeIf { it != -1 } ?: device.activeMode
                        val updatedMode = mode.copy(
                            brightness = pendingBrightness.toInt(),
                            speed = uiToDeviceSpeed(pendingSpeedUi.toInt(), mode.speedMin, mode.speedMax),
                            colors = if (mode.hasModeSpecificColor()) {
                                mode.colors.ifEmpty { device.colors }
                            } else mode.colors
                        )
                        viewModel.updateMode(deviceIndex, modeIndex, updatedMode)
                    },
                    valueRange = mode.brightnessMin.toFloat()..mode.brightnessMax.toFloat(),
                    steps = (mode.brightnessMax - mode.brightnessMin - 1).coerceAtLeast(0),
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.Yellow,
                        activeTrackColor = Color.Yellow
                    )
                )
            } else {
                Text("Brightness control not available", color = Color.Gray)
            }
            Spacer(Modifier.height(12.dp))
        }

        // Speed slider (if supported) — REWRITTEN: UI uses device range (min.max) and helper functions invert mapping.
        currentMode?.takeIf { it.hasSpeed() }?.let { mode ->
            val (sMinInt, sMaxInt) = orderedInts(mode.speedMin, mode.speedMax)
            val isValidSpeedRange = sMaxInt > sMinInt

            val currentSpeedDisplay = if (isValidSpeedRange) {
                // pendingSpeedUi is already in device-range (inverted UI value). Show as device-format string
                deviceSpeedDisplay(pendingSpeedUi.toInt())
            } else {
                "N/A"
            }

            Text("Speed: $currentSpeedDisplay", color = Color.White)
            Spacer(modifier = Modifier.height(6.dp))

            if (isValidSpeedRange) {
                val minF = sMinInt.toFloat()
                val maxF = sMaxInt.toFloat()
                val steps = (sMaxInt - sMinInt - 1).coerceAtLeast(0)

                // ensure pendingSpeed is valid in device-range
                pendingSpeedUi = pendingSpeedUi.coerceIn(minF, maxF)

                Slider(
                    value = pendingSpeedUi,
                    onValueChange = { v ->
                        // UI value is in device-range (inverted semantics)
                        val clamped = v.coerceIn(minF, maxF)
                        pendingSpeedUi = clamped

                        // convert UI value back to device speed
                        val speedDevice = uiToDeviceSpeed(clamped.toInt(), sMinInt, sMaxInt)

                        val modeIndex = device.modes.indexOfFirst { it.name == mode.name && it.value == mode.value }
                            .takeIf { it != -1 } ?: device.activeMode
                        val updatedMode = mode.copy(
                            speed = speedDevice,
                            brightness = pendingBrightness.toInt(),
                            colors = if (mode.hasModeSpecificColor()) {
                                mode.colors.ifEmpty { device.colors }
                            } else mode.colors
                        )
                        viewModel.updateMode(deviceIndex, modeIndex, updatedMode)
                    },
                    valueRange = minF..maxF,
                    steps = steps,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.Cyan,
                        activeTrackColor = Color.Cyan
                    )
                )
            } else {
                Text("Speed control not available", color = Color.Gray)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/* ------------------------
   DropdownSelector - simple reusable dropdown for Zone & LED selection
   ------------------------ */
@Composable
private fun DropdownSelector(
    label: String,
    items: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(label, color = Color.White)
        Spacer(Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A1A), RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(12.dp)
        ) {
            Text(
                text = items.getOrNull(selectedIndex) ?: "Select",
                color = Color.White
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            items.forEachIndexed { index, item ->
                DropdownMenuItem(
                    text = { Text(item) },
                    onClick = {
                        expanded = false
                        onSelected(index)
                    }
                )
            }
        }
    }
}

/* ------------------------
   Helpers & small utilities (reused / slightly modified)
   ------------------------ */

private fun orderedInts(a: Int, b: Int): Pair<Int, Int> =
    if (a <= b) a to b else b to a

private fun deviceSpeedToUi(deviceSpeed: Int, sMin: Int, sMax: Int): Int {
    val (min, max) = orderedInts(sMin, sMax)
    if (max <= min) return deviceSpeed.coerceIn(min, max)

    val clamped = deviceSpeed.coerceIn(min, max)
    return max - (clamped - min)
}

private fun uiToDeviceSpeed(uiValue: Int, sMin: Int, sMax: Int): Int {
    val (min, max) = orderedInts(sMin, sMax)
    if (max <= min) return uiValue.coerceIn(min, max)

    val clampedUi = uiValue.coerceIn(min, max)
    return max - (clampedUi - min)
}

private fun deviceSpeedDisplay(deviceSpeed: Int): String = deviceSpeed.toString()
