package me.kavishdevar.openrgb.network

import android.util.Log
import me.kavishdevar.openrgb.models.OpenRGBColor
import me.kavishdevar.openrgb.protocol.OpenRGBProtocol
import me.kavishdevar.openrgb.protocol.OpenRGBProtocol.Command
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import me.kavishdevar.openrgb.models.OpenRGBDevice
import me.kavishdevar.openrgb.models.OpenRGBMode
import me.kavishdevar.openrgb.protocol.ColorMode
import me.kavishdevar.openrgb.protocol.OpenRGBProtocol.PROTOCOL_VERSION_5
import java.nio.BufferUnderflowException
import java.util.concurrent.locks.ReentrantLock


class OpenRGBClient(private val host: String, private val port: Int, private val clientName: String) {
    private val socketLock = ReentrantLock()
    private var socket: Socket? = null
    private var inputStream: BufferedInputStream? = null
    private var outputStream: BufferedOutputStream? = null
    private var _protocolVersion = 0
    val protocolVersion: Int get() = _protocolVersion
    private var lastCommandTime: Long = 0

    companion object {
        private const val CONNECT_TIMEOUT = 5000 // 5 seconds
        private const val SOCKET_TIMEOUT = 10000 // 10 seconds
        private const val READ_TIMEOUT = 10000 // 10 seconds
    }

    init {
        Log.d("OpenRGB", "OpenRGBClient initialized with:")
        Log.d("OpenRGB", "  Host: $host")
        Log.d("OpenRGB", "  Port: $port")
        Log.d("OpenRGB", "  Client Name: $clientName")
    }



    @Throws(IOException::class)
    fun connect() {
        Log.d("OpenRGB", "connect() called")
        Log.d("OpenRGB", "Current connection details - Host: $host, Port: $port")

        disconnect()

        socketLock.lock()
        try {
            Log.d("OpenRGB", "Creating new socket...")
            socket = Socket().apply {
                // Set socket options exactly as in old Java client
                soTimeout = SOCKET_TIMEOUT
                keepAlive = true

                // Use the exact same connection approach as old Java client
                Log.d("OpenRGB", "Attempting connection to $host:$port")
                connect(createSocketAddress(), CONNECT_TIMEOUT)

                if (!isConnected) {
                    throw IOException("Socket not connected")
                }

                Log.d("OpenRGB", "Socket connected successfully")
                Log.d("OpenRGB", "Local port: ${localPort}, Remote address: ${inetAddress?.hostAddress ?: "null"}:$port")

                // Initialize streams exactly as in old Java client
                this@OpenRGBClient.inputStream = BufferedInputStream(getInputStream()).apply {
                    mark(READ_TIMEOUT)
                    Log.d("OpenRGB", "InputStream initialized")
                }
                this@OpenRGBClient.outputStream = BufferedOutputStream(getOutputStream()).also {
                    Log.d("OpenRGB", "OutputStream initialized")
                }
            }

            // Set client name first, then protocol version - exactly as in old Java client
            Log.d("OpenRGB", "Setting client name...")
            setClientName()

            Log.d("OpenRGB", "Fetching protocol version...")
            _protocolVersion = fetchProtocolVersion().coerceAtMost(OpenRGBProtocol.PROTOCOL_VERSION_5)
            Log.d("OpenRGB", "Protocol version negotiated: $_protocolVersion")

            Log.d("OpenRGB", "Connection established successfully to $host:$port")

        } catch (e: Exception) {
            Log.e("OpenRGB", "Connection failed to $host:$port", e)
            disconnect()
            throw when (e) {
                is IOException -> e
                else -> IOException("Failed to connect to $host:$port (${e.message})", e)
            }
        } finally {
            socketLock.unlock()
        }
    }
    fun isTrulyConnected(): Boolean {
        return try {
            socket?.isConnected == true && socket?.isClosed == false
        } catch (e: Exception) {
            false
        }
    }


    private fun setClientName() {
        val nameBytes = (clientName + '\u0000').toByteArray(Charsets.US_ASCII)
        sendCommand(Command.SET_CLIENT_NAME, 0, nameBytes)
    }

    // Helper class to match old Java behavior
    private class SocketAddress(private val host: String, private val port: Int) : java.net.SocketAddress() {
        fun getHost() = host
        fun getPort() = port
    }

    @Throws(IOException::class)
    fun disconnect() {
        socketLock.lock()
        try {
            inputStream?.close()
            outputStream?.close()
            socket?.close()
        } catch (e: IOException) {
            Log.e("OpenRGB", "Error during disconnect", e)
        } finally {
            inputStream = null
            outputStream = null
            socket = null
            socketLock.unlock()
        }
    }

    fun isConnected(): Boolean = socket?.isConnected ?: false


    @Throws(IOException::class)
    fun getControllerCount(): Int {
        sendCommand(Command.REQUEST_CONTROLLER_COUNT)
        return readResponse().getInt()
    }

    @Throws(IOException::class)
    fun getDeviceController(index: Int): OpenRGBDevice {
        val data = ByteBuffer.allocate(4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(protocolVersion)
            .array()

        sendCommand(Command.REQUEST_CONTROLLER_DATA, index, data)
        val response = readResponse()

        // Log raw response data for debugging
        Log.d("OpenRGB", "Device data received (${response.remaining()} bytes): " +
                response.array().sliceArray(response.position() until response.limit())
                    .joinToString("") { "%02x".format(it) })

        try {
            response.rewind() // Reset position for parsing
            return DeviceParser.parse(response, protocolVersion)
        } catch (e: Exception) {
            Log.e("OpenRGB", "Error parsing device data", e)
            throw IOException("Failed to parse device data", e)
        }
    }

    fun updateLeds(deviceIndex: Int, colors: Array<OpenRGBColor>) {
        try {
            // Create buffer with:
            // - 4 bytes: LED count
            // - For each LED: 4 bytes (R, G, B, 0 padding)
            val buffer = ByteBuffer.allocate(4 + colors.size * 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(colors.size)  // LED count (4 bytes)

            // Add each color with padding
            colors.forEach { color ->
                buffer.put(color.red.toByte())
                    .put(color.green.toByte())
                    .put(color.blue.toByte())

            }

            Log.d("OpenRGB", "UPDATE_LEDS payload: ${buffer.array().joinToString("") { "%02x".format(it) }}")
            sendCommand(Command.UPDATE_LEDS, deviceIndex, buffer.array())
        } catch (e: Exception) {
            Log.e("OpenRGB", "LED update failed", e)
            throw IOException("Failed to update LEDs", e)
        }
    }
    // Add to OpenRGBClient.kt
    @Throws(IOException::class)
    fun updateLed(deviceIndex: Int, ledIndex: Int, color: OpenRGBColor) {
        socketLock.lock()
        try {
            val data = ByteBuffer.allocate(8)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(ledIndex)
                .put(color.red.toByte())
                .put(color.green.toByte())
                .put(color.blue.toByte())
                .put(0) // padding
                .array()

            Log.d("OpenRGB", "UPDATE_SINGLE_LED payload: ${data.joinToString("") { "%02x".format(it) }}")
            sendCommand(Command.UPDATE_SINGLE_LED, deviceIndex, data)
        } finally {
            socketLock.unlock()
        }
    }



    // Helper to log byte arrays as hex
    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

    // Update in OpenRGBClient.kt
    @Throws(IOException::class)
    fun updateMode(deviceIndex: Int, modeIndex: Int, mode: OpenRGBMode) {
        socketLock.lock()
        try {
            val payload = packModePayload(modeIndex, mode)
            Log.d("OpenRGB", "UPDATE_MODE payload (${payload.size} bytes): " + payload.joinToString("") { "%02x".format(it) })
            Log.d("OpenRGB", "Mode details: $mode")
            sendCommand(Command.UPDATE_MODE, deviceIndex, payload)
        } catch (e: Exception) {
            Log.e("OpenRGB", "Mode update failed", e)
            throw IOException("Failed to update mode", e)
        } finally {
            socketLock.unlock()
        }
    }















    private fun fetchProtocolVersion(): Int {
        val data = ByteBuffer.allocate(4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(PROTOCOL_VERSION_5)  // Request protocol version 5
            .array()

        sendCommand(Command.REQUEST_PROTOCOL_VERSION, 0, data)
        return readResponse().getInt()
    }



    @Throws(IOException::class)
    private fun sendCommand(command: Command, deviceId: Int = 0, data: ByteArray? = null) {
        val header = OpenRGBProtocol.createHeader(deviceId, command, data?.size ?: 0)
        Log.d("OpenRGB", "Sending command: ${command.name}, header: ${header.toHexString()}")

        outputStream?.let { out ->
            try {
                out.write(header)
                data?.let {
                    Log.d("OpenRGB", "Command data: ${it.toHexString()}")
                    out.write(it)
                }
                out.flush()
                Log.d("OpenRGB", "Command sent successfully")
            } catch (e: IOException) {
                Log.e("OpenRGB", "Failed to send command", e)
                throw IOException("Failed to send command: ${e.message}", e)
            }
        } ?: throw IOException("Not connected")
    }



    @Throws(IOException::class)
    private fun readResponse(): ByteBuffer {
        val header = ByteArray(OpenRGBProtocol.HEADER_SIZE)
        readFully(inputStream, header)

        val headerBuffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val dataLength = headerBuffer.getInt(12)

        val data = ByteArray(dataLength)
        readFully(inputStream, data)

        return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
    }

    @Throws(IOException::class)
    private fun readFully(inputStream: BufferedInputStream?, buffer: ByteArray) {
        var offset = 0
        var bytesRead: Int

        while (offset < buffer.size) {
            bytesRead = inputStream?.read(buffer, offset, buffer.size - offset)
                ?: throw IOException("InputStream is null")
            if (bytesRead == -1) throw IOException("Unexpected end of stream")
            offset += bytesRead
        }
    }

    private fun createSocketAddress(): java.net.SocketAddress {
        return InetSocketAddress(host, port)
    }

    fun getProfileList(): List<String> {
        sendCommand(OpenRGBProtocol.Command.REQUEST_PROFILE_LIST)
        val buffer = readResponse()

        val rawBytes = buffer.array().joinToString(" ") { "%02x".format(it) }
        Log.d("OpenRGB", "Raw profile buffer: $rawBytes")

        buffer.int // discard it


        val profileCount = buffer.short.toInt()
        val profiles = mutableListOf<String>()

        for (i in 0 until profileCount) {
            if (buffer.remaining() < 2) {
                Log.e("OpenRGB", "Not enough bytes to read profile name length")
                break
            }

            val nameLength = buffer.short.toInt()
            if (nameLength <= 0 || buffer.remaining() < nameLength) {
                Log.w("OpenRGB", "Skipping profile $i due to empty or invalid name length: $nameLength")
                continue
            }

            val nameBytes = ByteArray(nameLength)
            buffer.get(nameBytes)
            val name = nameBytes.toString(Charsets.US_ASCII).trimEnd('\u0000')

            Log.d("OpenRGB", "Loaded profile[$i]: $name")
            profiles.add(name)
        }

        return profiles
    }





    @Throws(IOException::class)
    fun loadProfile(name: String) {
        // 1) Send the LOAD_PROFILE request (null-terminated ASCII name)
        val nameBytes = (name + '\u0000').toByteArray(Charsets.US_ASCII)
        sendCommand(Command.LOAD_PROFILE, 0, nameBytes)

        // 2) According to the SDK docs: after requesting a profile load the client
        //    should re-request all controller states and then call UpdateMode()
        //    on all controllers so the updated state is applied locally.
        try {
            // Re-request controller count (this will trigger the server to send the
            // controller count response which we read in getControllerCount()).
            val controllerCount = try {
                getControllerCount()
            } catch (e: Exception) {
                Log.w("OpenRGB", "Failed to get controller count after loading profile", e)
                return
            }

            // For each controller: re-request its data and then call updateMode()
            // with its active mode so the loaded profile is applied locally.
            for (i in 0 until controllerCount) {
                try {
                    // getDeviceController() sends REQUEST_CONTROLLER_DATA (with protocol
                    // version) and parses the response into an OpenRGBDevice.
                    val device = getDeviceController(i)

                    // If device has modes and an activeMode, call updateMode to apply state.
                    // Some devices may not have modes; guard accordingly.
                    val activeModeIndex = device.activeMode
                    val modes = device.modes
                    if (modes != null && activeModeIndex >= 0 && activeModeIndex < modes.size) {
                        // Send the UpdateMode for the active mode to ensure the controller
                        // applies the newly-loaded profile's mode settings.
                        updateMode(i, activeModeIndex, modes[activeModeIndex])
                    } else {
                        Log.d("OpenRGB", "Device $i has no modes or invalid activeMode; skipping UpdateMode")
                    }
                } catch (e: Exception) {
                    Log.w("OpenRGB", "Failed to refresh/apply mode for controller $i after loading profile", e)
                    // continue with other controllers
                }
            }
        } catch (e: Exception) {
            Log.e("OpenRGB", "Unexpected error while applying loaded profile", e)
            // don't rethrow; loading a profile succeeded on the server even if client-side
            // refresh had problems.
        }
    }


    @Throws(IOException::class)
    fun saveProfile(name: String) {
        val nameBytes = (name + '\u0000').toByteArray(Charsets.US_ASCII)
        sendCommand(Command.SAVE_PROFILE, 0, nameBytes)
    }

    @Throws(IOException::class)
    fun deleteProfile(name: String) {
        val nameBytes = (name + '\u0000').toByteArray(Charsets.US_ASCII)
        sendCommand(Command.DELETE_PROFILE, 0, nameBytes)
    }

// Add to OpenRGBClient.kt

    // Zone control functions
    @Throws(IOException::class)
    fun updateZoneLeds(deviceIndex: Int, zoneIndex: Int, colors: List<OpenRGBColor>) {
        // num_colors is unsigned short in protocol
        val numColors = colors.size.coerceAtMost(Short.MAX_VALUE.toInt())
        val colorsSize = numColors * 4 // each RGBColor is 4 bytes (R,G,B,pad)
        val innerSize = 4 + 2 + colorsSize // zone_idx (4) + num_colors (2) + colors

        val innerBuffer = ByteBuffer.allocate(4 + innerSize).order(ByteOrder.LITTLE_ENDIAN)
        innerBuffer.putInt(4 + innerSize) // data_size: total including this int
        innerBuffer.putInt(zoneIndex)
        innerBuffer.putShort(numColors.toShort())

        colors.take(numColors).forEach { color ->
            innerBuffer.put(color.red.toByte())
            innerBuffer.put(color.green.toByte())
            innerBuffer.put(color.blue.toByte())
            innerBuffer.put(0) // padding
        }

        sendCommand(Command.UPDATE_ZONE_LEDS, deviceIndex, innerBuffer.array())
    }


    @Throws(IOException::class)
    fun resizeZone(deviceIndex: Int, zoneIndex: Int, newSize: Int) {
        val buffer = ByteBuffer.allocate(8)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(zoneIndex)
            .putInt(newSize)

        sendCommand(Command.RESIZE_ZONE, deviceIndex, buffer.array())
    }

    // Mode control functions
    @Throws(IOException::class)
    fun setCustomMode(deviceIndex: Int) {
        sendCommand(Command.SET_CUSTOM_MODE, deviceIndex)
    }

    @Throws(IOException::class)
    fun saveMode(deviceIndex: Int, modeIndex: Int, mode: OpenRGBMode) {
        socketLock.lock()
        try {
            // Update the mode on the device (sends UPDATE_MODE with ModeData)
            val updatePayload = packModePayload(modeIndex, mode)
            sendCommand(Command.UPDATE_MODE, deviceIndex, updatePayload)

            // Save the mode on the device (SAVEMODE) — must include the same ModeData payload
            val savePayload = packModePayload(modeIndex, mode)
            sendCommand(Command.SAVE_MODE, deviceIndex, savePayload)
        } finally {
            socketLock.unlock()
        }
    }


    // Add inside class OpenRGBClient
    private fun packModePayload(modeIndex: Int, mode: OpenRGBMode): ByteArray {
        // Mode name (null-terminated)
        val nameBytes = (mode.name + '\u0000').toByteArray(Charsets.US_ASCII)

        // Pack colors (RGB triplets, no per-color padding) — follow your previous logic
        val (colorsCount, colorsData) = when (mode.colorMode) {
            ColorMode.MODE_COLORS_NONE -> 0 to byteArrayOf()
            ColorMode.MODE_COLORS_PER_LED -> 0 to byteArrayOf() // handled elsewhere in your code
            else -> {
                val count = mode.colors.size.coerceIn(mode.colorMin, mode.colorMax)
                val data = ByteArray(count * 3)
                mode.colors.take(count).forEachIndexed { i, color ->
                    data[i * 3] = color.red.toByte()
                    data[i * 3 + 1] = color.green.toByte()
                    data[i * 3 + 2] = color.blue.toByte()
                }
                count to data
            }
        }

        // Calculate inner ModeData size (fields and conditional protocol-version fields)
        val innerSize =
                    4 + // mode_idx
                    2 + nameBytes.size + // name length (short) + name bytes (includes null)
                    4 + // value
                    4 + // flags
                    4 + 4 + // speed_min, speed_max
                    (if (protocolVersion >= 3) 4 + 4 else 0) + // brightness_min, brightness_max (proto >= 3)
                    4 + 4 + // colors_min, colors_max
                    4 + // speed
                    (if (protocolVersion >= 3) 4 else 0) + // brightness (proto >= 3)
                    4 + // direction
                    4 + // color_mode
                    2 + // num_colors (short)
                    colorsData.size

        val innerBuffer = ByteBuffer.allocate(innerSize).order(ByteOrder.LITTLE_ENDIAN)

        innerBuffer.putInt(modeIndex)
        innerBuffer.putShort(nameBytes.size.toShort())
        innerBuffer.put(nameBytes)
        innerBuffer.putInt(mode.value)
        innerBuffer.putInt(mode.flags)
        innerBuffer.putInt(mode.speedMin)
        innerBuffer.putInt(mode.speedMax)

        if (protocolVersion >= 3) {
            innerBuffer.putInt(mode.brightnessMin)
            innerBuffer.putInt(mode.brightnessMax)
        }

        innerBuffer.putInt(mode.colorMin)
        innerBuffer.putInt(mode.colorMax)
        innerBuffer.putInt(mode.speed)

        if (protocolVersion >= 3) {
            innerBuffer.putInt(mode.brightness)
        }

        innerBuffer.putInt(mode.direction.ordinal)
        innerBuffer.putInt(mode.colorMode.ordinal)
        innerBuffer.putShort(colorsCount.toShort())
        innerBuffer.put(colorsData)

        // Prepend the 4-byte inner length prefix (data_size) — python client and docs do this.
        val rawInner = innerBuffer.array()
        val fullPayload = ByteBuffer.allocate(4 + rawInner.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(4 + rawInner.size) // total size including this 4 bytes (matches python pack)
            .put(rawInner)
            .array()

        return fullPayload
    }



    // Plugin functions
    @Throws(IOException::class)
    fun requestPluginList(): List<String> {
        sendCommand(Command.REQUEST_PLUGIN_LIST)
        val buffer = readResponse()

        // Parse plugin list (similar to profile list parsing)
        val pluginCount = buffer.short.toInt()
        val plugins = mutableListOf<String>()

        for (i in 0 until pluginCount) {
            if (buffer.remaining() < 2) break
            val nameLength = buffer.short.toInt()
            if (nameLength <= 0 || buffer.remaining() < nameLength) continue

            val nameBytes = ByteArray(nameLength)
            buffer.get(nameBytes)
            plugins.add(String(nameBytes, 0, nameLength - 1)) // Remove null terminator
        }

        return plugins
    }


}