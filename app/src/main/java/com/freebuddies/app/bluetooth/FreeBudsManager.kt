package com.freebuddies.app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.freebuddies.app.DebugLog
import com.freebuddies.app.LogTag
import com.freebuddies.app.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.util.*

class FreeBudsManager(private val device: BluetoothDevice) {
    private val sppUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var socket: BluetoothSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    private val _batteryStatus = MutableStateFlow<BatteryStatus?>(null)
    val batteryStatus = _batteryStatus.asStateFlow()

    private val _deviceInfo = MutableStateFlow<DeviceInfo?>(null)
    val deviceInfo = _deviceInfo.asStateFlow()

    private val _soundControl = MutableStateFlow<SoundControl?>(null)
    val soundControl = _soundControl.asStateFlow()

    private val _inEarState = MutableStateFlow<InEarState?>(null)
    val inEarState = _inEarState.asStateFlow()

    private val _ringingStatus = MutableStateFlow(RingingStatus(left = false, right = false))
    val ringingStatus = _ringingStatus.asStateFlow()

    private val frameReader = FrameReader { frame ->
        handleFrame(frame)
    }

    @SuppressLint("MissingPermission")
    fun connect() {
        DebugLog.d(LogTag.APP, "Connecting to ${device.name}")
        scope.launch {
            try {
                _isConnected.value = false
                socket = device.createRfcommSocketToServiceRecord(sppUuid)
                socket?.connect()
                DebugLog.d(LogTag.APP, "Connected")
                _isConnected.value = true

                // Request initial state with "ping" TLVs (00 00)
                val ping = listOf(Tlv(0x00, byteArrayOf()))
                sendFrame(Frame.build(0x01, 0x08, ping)) // Get battery
                sendFrame(Frame.build(0x2B, 0x2A, ping)) // Get ANC mode

                startReader()
            } catch (e: IOException) {
                DebugLog.e(LogTag.APP, "Connection failed: ${e.message}")
                _isConnected.value = false
            }
        }
    }

    private fun startReader() {
        scope.launch {
            val buffer = ByteArray(512)
            val inputStream = socket?.inputStream ?: return@launch
            while (isActive && _isConnected.value) {
                try {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead > 0) {
                        frameReader.feed(buffer, 0, bytesRead)
                    } else {
                        disconnect()
                    }
                } catch (e: IOException) {
                    DebugLog.e(LogTag.RX, "Read error: ${e.message}", e)
                    disconnect()
                }
            }
        }
    }

    private fun handleFrame(frame: Frame) {
        val frameId = "${frame.service.toString(16)}:${frame.command.toString(16)}"
        DebugLog.d(LogTag.RX, "$frameId ${frame.tlvs.joinToString(" ") { "T${it.type.toString(16)}=${it.value.joinToString("") { b -> "%02X".format(b) }}" }}")

        when (frame.service to frame.command) {
            0x01 to 0x08, 0x01 to 0x27 -> {
                BatteryStatus.fromTlvs(frame.tlvs)?.let {
                    _batteryStatus.value = it
                    DebugLog.d(LogTag.BUDS, "Battery L=${it.leftPercent}% R=${it.rightPercent}% C=${it.casePercent}%")
                }
            }
            0x2B to 0x0A -> {
                DeviceInfo.fromTlvs(frame.tlvs)?.let {
                    _deviceInfo.value = it
                    DebugLog.d(LogTag.BUDS, "Device ${it.modelCode} fw=${it.firmwareVersion}")
                }
            }
            0x2B to 0x2A -> {
                SoundControl.fromTlvs(frame.tlvs)?.let {
                    _soundControl.value = it
                    val label = when (it.mode) {
                        AncMode.NOISE_CANCELLING -> "NC ${it.ncIntensity.label}"
                        AncMode.AWARENESS -> "Awareness" + if (it.voiceMode) " (voice)" else ""
                        else -> "${it.mode}"
                    }
                    DebugLog.d(LogTag.BUDS, "ANC $label")
                }
            }
            0x2B to 0x5D, 0x2B to 0x5E -> {
                // Ringing status in Tag 02
                frame.tlvs.find { it.type == 0x02 }?.let { tlv ->
                    _ringingStatus.value = RingingStatus.fromTlv(tlv, _ringingStatus.value)
                    val data = tlv.value
                    if (data.size >= 2) {
                        val side = if (data[0].toInt() == 0) "left" else "right"
                        val state = if (data[1].toInt() == 0) "ringing" else "stopped"
                        DebugLog.d(LogTag.BUDS, "Ring $side $state")
                    }
                }
            }
            0x2B to 0x25 -> {
                InEarState.fromTlvs(frame.tlvs)?.let {
                    _inEarState.value = it
                    DebugLog.d(LogTag.BUDS, "In-ear L=${it.left.label} R=${it.right.label}")
                }
            }
            0x2B to 0x31 -> {
                val name = frame.tlvs.find { it.type == 0x09 }?.value
                    ?.toString(Charsets.US_ASCII) ?: ""
                val state = frame.tlvs.find { it.type == 0x05 }?.value
                    ?.firstOrNull()?.toInt()?.and(0xFF)
                val label = when (state) {
                    0x01 -> "stopped"
                    0x03 -> "paused"
                    0x09 -> "playing"
                    else -> state?.let { "0x${"%02X".format(it)}" } ?: "?"
                }
                DebugLog.d(LogTag.BUDS, "Audio source \"$name\" $label")
            }
            0x2B to 0x36 -> {
                val data = frame.tlvs.find { it.type == 0x05 }?.value
                if (data != null && data.isNotEmpty()) {
                    val state = data.last().toInt() and 0xFF
                    val label = when (state) {
                        0x01 -> "stopped"
                        0x03 -> "paused"
                        0x09 -> "playing"
                        else -> "unknown (${"%02X".format(state)})"
                    }
                    DebugLog.d(LogTag.BUDS, "Playback $label")
                }
            }
            0x2B to 0x4B -> {
                val data = frame.tlvs.find { it.type == 0x02 }?.value
                if (data != null && data.size >= 3) {
                    val dir = if (data[0].toInt() == 0) "up" else "down"
                    val from = data[1].toInt() and 0xFF
                    val to = data[2].toInt() and 0xFF
                    DebugLog.d(LogTag.BUDS, "Volume $dir $from → $to")
                }
            }
            0x2B to 0x04 -> {
                val status = frame.tlvs.firstOrNull { it.type == 0x02 }
                    ?.value?.getOrNull(0)?.toInt()?.and(0xFF) ?: -1
                if (status != 0) {
                    DebugLog.w(LogTag.BUDS, "ANC write rejected, status=$status")
                }
            }
            else -> DebugLog.d(LogTag.RX, "Unhandled $frameId")
        }
    }

    fun sendFrame(frame: ByteArray) {
        DebugLog.d(LogTag.TX, frame.joinToString(" ") { "%02X".format(it) })
        scope.launch {
            try {
                socket?.outputStream?.write(frame) ?: DebugLog.w(LogTag.TX, "Socket is null")
            } catch (e: IOException) {
                DebugLog.e(LogTag.TX, "Write error: ${e.message}", e)
                disconnect()
            }
        }
    }

    fun refreshState() {
        if (_isConnected.value) {
            val ping = listOf(Tlv(0x00, byteArrayOf()))
            sendFrame(Frame.build(0x01, 0x08, ping))
            sendFrame(Frame.build(0x2B, 0x2A, ping))
        }
    }

    fun setAncMode(mode: AncMode, intensity: NcIntensity = NcIntensity.GENERAL, voiceMode: Boolean = false) {
        val label = when (mode) {
            AncMode.NOISE_CANCELLING -> "NC ${intensity.label}"
            AncMode.AWARENESS -> "Awareness" + if (voiceMode) " (voice)" else ""
            else -> "$mode"
        }
        DebugLog.d(LogTag.APP, "Set ANC $label")
        val tlv = try {
            SoundControl.toTlv(mode, intensity, voiceMode)
        } catch (e: Exception) {
            DebugLog.e(LogTag.APP, "Failed to encode ANC: ${e.message}")
            return
        }
        sendFrame(Frame.build(0x2B, 0x04, listOf(tlv)))
    }

    fun setRinging(side: Int, active: Boolean) {
        DebugLog.d(LogTag.APP, "Ring ${if (side == 0) "left" else "right"} ${if (active) "start" else "stop"}")
        val tlv = Tlv(0x01, byteArrayOf(
            side.toByte(),
            if (active) 0 else 1 // 0=Ring, 1=Stop
        ))
        sendFrame(Frame.build(0x2B, 0x5D, listOf(tlv)))
    }

    fun disconnect() {
        _isConnected.value = false
        try {
            socket?.close()
        } catch (e: IOException) {
            DebugLog.e(LogTag.APP, "Socket close error: ${e.message}", e)
        }
        socket = null
    }

    fun release() {
        disconnect()
        scope.cancel()
    }
}
