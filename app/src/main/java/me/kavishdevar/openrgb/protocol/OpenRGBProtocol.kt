package me.kavishdevar.openrgb.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

object OpenRGBProtocol {
    const val DEFAULT_PORT = 6742
    const val HEADER_SIZE = 16
    val MAGIC_STRING = "ORGB".toByteArray(Charsets.US_ASCII)

    // Protocol versions
    const val PROTOCOL_VERSION_0 = 0
    const val PROTOCOL_VERSION_3 = 3
    const val PROTOCOL_VERSION_5 = 5

    // Command codes
    enum class Command(val value: Int) {
        REQUEST_CONTROLLER_COUNT(0),
        REQUEST_CONTROLLER_DATA(1),
        REQUEST_PROTOCOL_VERSION(40),
        SET_CLIENT_NAME(50),
        REQUEST_PROFILE_LIST(150),
        SAVE_PROFILE(151),
        LOAD_PROFILE(152),
        DELETE_PROFILE(153),
        RESIZE_ZONE(1000),
        UPDATE_LEDS(1050),
        UPDATE_ZONE_LEDS(1051),
        UPDATE_SINGLE_LED(1052),
        SET_CUSTOM_MODE(1100),
        UPDATE_MODE(1101),
        SAVE_MODE(1102),
        DEVICE_LIST_UPDATED(100),
        REQUEST_PLUGIN_LIST(200),
        PLUGIN_SPECIFIC(201)
    }

    // Mode flags
    object ModeFlags {
        const val HAS_SPEED = 1 shl 0
        const val HAS_DIRECTION_LR = 1 shl 1
        const val HAS_DIRECTION_UD = 1 shl 2
        const val HAS_DIRECTION_HV = 1 shl 3
        const val HAS_BRIGHTNESS = 1 shl 4
        const val HAS_PER_LED_COLOR = 1 shl 5
        const val HAS_MODE_SPECIFIC_COLOR = 1 shl 6
        const val HAS_RANDOM_COLOR = 1 shl 7
        const val MANUAL_SAVE = 1 shl 8
        const val AUTOMATIC_SAVE = 1 shl 9
    }

    // Helper functions
    fun createHeader(deviceId: Int, command: Command, dataLength: Int): ByteArray {
        return ByteBuffer.allocate(HEADER_SIZE)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(MAGIC_STRING)
            .putInt(deviceId)
            .putInt(command.value)
            .putInt(dataLength)
            .array()
    }

    fun intToBytes(value: Int): ByteArray {
        return ByteBuffer.allocate(4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(value)
            .array()
    }

    fun stringToBytes(str: String): ByteArray {
        val strBytes = str.toByteArray(Charsets.US_ASCII)
        val lengthBytes = shortToBytes(strBytes.size.toShort())
        return lengthBytes + strBytes + 0 // Null terminator
    }

    private fun shortToBytes(value: Short): ByteArray {
        return ByteBuffer.allocate(2)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putShort(value)
            .array()
    }
}