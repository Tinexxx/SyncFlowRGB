package me.kavishdevar.openrgb.network

import android.util.Log
import me.kavishdevar.openrgb.models.*
import me.kavishdevar.openrgb.protocol.ColorMode
import me.kavishdevar.openrgb.protocol.ZoneType
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder



object DeviceParser {
    //Parsiranje raw podataka sa servera:
    fun parse(buffer: ByteBuffer, protocolVersion: Int): OpenRGBDevice {
        try {
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            buffer.int // Skip duplicate length

            val type = DeviceType.values().getOrNull(buffer.int) ?: DeviceType.DEVICE_TYPE_UNKNOWN
            val name = readString(buffer)
            val vendor = if (protocolVersion >= 1) readString(buffer) else ""
            val description = readString(buffer)
            val version = readString(buffer)
            val serial = readString(buffer)
            val location = readString(buffer)

            val modeCount = buffer.short.toInt() and 0xFFFF
            val activeMode = buffer.int
            val modes = parseModes(buffer, modeCount, protocolVersion)

            val zoneCount = buffer.short.toInt() and 0xFFFF
            val zones = parseZones(buffer, zoneCount, protocolVersion)

            val ledCount = buffer.short.toInt() and 0xFFFF
            val leds = parseLeds(buffer, ledCount)

            val colorCount = buffer.short.toInt() and 0xFFFF
            val colors = parseColors(buffer, colorCount)

            return OpenRGBDevice(
                type = type,
                name = name,
                vendor = vendor,
                description = description,
                version = version,
                serial = serial,
                location = location,
                activeMode = activeMode,
                modes = modes,
                zones = zones,
                leds = leds,
                colors = colors
            )
        } catch (e: Exception) {
            Log.e("DeviceParser", "Error parsing device data", e)
            throw IOException("Failed to parse device data", e)
        }
    }

    private fun parseModes(buffer: ByteBuffer, modeCount: Int, protocolVersion: Int): List<OpenRGBMode> {
        return List(modeCount) {
            val name = readString(buffer)
            val value = buffer.int
            val flags = buffer.int

            val speedMin = buffer.int
            val speedMax = buffer.int

            val brightnessMin = if (protocolVersion >= 3) buffer.int else 0
            val brightnessMax = if (protocolVersion >= 3) buffer.int else 100

            val colorMin = buffer.int
            val colorMax = buffer.int

            val speed = buffer.int
            val brightness = if (protocolVersion >= 3) buffer.int else 100

            val directionRaw = buffer.int
            val direction = ModeDirection.entries.getOrNull(directionRaw) ?: ModeDirection.MODE_DIRECTION_LEFT

            val colorMode = ColorMode.entries.getOrNull(buffer.int) ?: ColorMode.MODE_COLORS_NONE

            val colorCount = buffer.short.toInt() and 0xFFFF
            val colors = List(colorCount) { readColor(buffer) }

            OpenRGBMode(
                name = name,
                value = value,
                flags = flags,
                speedMin = speedMin,
                speedMax = speedMax,
                brightnessMin = brightnessMin,
                brightnessMax = brightnessMax,
                colorMin = colorMin,
                colorMax = colorMax,
                speed = speed,
                brightness = brightness,
                direction = direction,
                colorMode = colorMode,
                colors = colors
            )
        }
    }

    private fun parseZones(buffer: ByteBuffer, zoneCount: Int, protocolVersion: Int): List<OpenRGBZone> {
        return List(zoneCount) {
            val name = readString(buffer)
            val type = ZoneType.values().getOrNull(buffer.int) ?: ZoneType.ZONE_TYPE_SINGLE
            val ledsMin = buffer.int
            val ledsMax = buffer.int
            val ledsCount = buffer.int

            val matrixSize = buffer.short.toInt() and 0xFFFF
            if (matrixSize > 0) {
                val height = buffer.int
                val width = buffer.int
                repeat(height * width) { buffer.int }
            }

            val segments = if (protocolVersion >= 4 && buffer.remaining() >= 2) {
                val segmentCount = buffer.short.toInt() and 0xFFFF
                List(segmentCount) {
                    OpenRGBZoneSegment(
                        name = readString(buffer),
                        type = ZoneType.values().getOrNull(buffer.int) ?: ZoneType.ZONE_TYPE_SINGLE,
                        startIdx = buffer.int,
                        ledsCount = buffer.int
                    )
                }
            } else emptyList()

            val flags = if (protocolVersion >= 5 && buffer.remaining() >= 4) buffer.int else 0

            OpenRGBZone(
                name = name,
                type = type,
                ledsMin = ledsMin,
                ledsMax = ledsMax,
                ledsCount = ledsCount,
                segments = segments,
                flags = flags
            )
        }
    }

    private fun parseLeds(buffer: ByteBuffer, ledCount: Int): List<OpenRGBLed> {
        Log.d("DeviceParser", "Reading $ledCount LEDs from ${buffer.remaining()} bytes")
        val startPos = buffer.position()
        return List(ledCount) {
            val name = readString(buffer)
            val value = buffer.int
            Log.d("DeviceParser",
                "LED $it: $name (value=0x${value.toString(16).padStart(8, '0')}) " +
                        "at pos ${buffer.position() - startPos}"
            )
            OpenRGBLed(name, value)
        }
    }

    private fun parseColors(buffer: ByteBuffer, colorCount: Int): List<OpenRGBColor> {
        return List(colorCount) { readColor(buffer) }
    }

    private fun readColor(buffer: ByteBuffer): OpenRGBColor {
        if (buffer.remaining() < 4) throw IOException("Not enough bytes to read color")
        return OpenRGBColor(
            red = buffer.get().toInt() and 0xFF,
            green = buffer.get().toInt() and 0xFF,
            blue = buffer.get().toInt() and 0xFF
        ).also { buffer.get() /* padding */ }
    }

    private fun readString(buffer: ByteBuffer): String {
        val length = buffer.short.toInt() and 0xFFFF
        if (length <= 0) return ""
        val bytes = ByteArray(length)
        buffer.get(bytes)
        return String(bytes, 0, length - 1)
    }
}

