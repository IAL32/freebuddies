package com.freebuddies.app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
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

    private val frameReader = FrameReader { frame ->
        handleFrame(frame)
    }

    @SuppressLint("MissingPermission")
    fun connect() {
        Log.d("FreeBudsManager", "Attempting to connect to ${device.name}")
        scope.launch {
            try {
                _isConnected.value = false
                socket = device.createRfcommSocketToServiceRecord(sppUuid)
                socket?.connect()
                Log.d("FreeBudsManager", "Connected successfully")
                _isConnected.value = true
                
                // Request initial state with "ping" TLVs (00 00)
                val ping = listOf(Tlv(0x00, byteArrayOf()))
                sendFrame(Frame.build(0x01, 0x08, ping)) // Get battery
                sendFrame(Frame.build(0x2B, 0x2A, ping)) // Get ANC mode
                
                startReader()
            } catch (e: IOException) {
                Log.d("FreeBudsManager", "Connection failed: ${e.message}")
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
                    Log.e("FreeBudsManager", "Read error", e)
                    disconnect()
                }
            }
        }
    }

    private fun handleFrame(frame: Frame) {
        val frameId = "${frame.service.toString(16)}:${frame.command.toString(16)}"
        Log.d("FreeBudsManager", "RX: $frameId tlvs=${frame.tlvs.joinToString { "T${it.type.toString(16)}=${it.value.joinToString("") { b -> "%02X".format(b) }}" }}")

        when (frame.service to frame.command) {
            0x01 to 0x08, 0x01 to 0x27 -> {
                BatteryStatus.fromTlvs(frame.tlvs)?.let { _batteryStatus.value = it }
            }
            0x2B to 0x0A -> {
                DeviceInfo.fromTlvs(frame.tlvs)?.let { _deviceInfo.value = it }
            }
            0x2B to 0x5D, 0x2B to 0x5E, 0x2B to 0x2A, 0x2B to 0xAC -> {
                SoundControl.fromTlvs(frame.tlvs)?.let { _soundControl.value = it }
            }
            0x2B to 0xAC -> {
                // Pro 4 specific status broadcast?
                // T1=enabled, T2=mode?
                val t1 = frame.tlvs.find { it.type == 1 }?.value?.firstOrNull()?.toInt()
                val t2 = frame.tlvs.find { it.type == 2 }?.value?.firstOrNull()?.toInt()
                if (t1 != null && t2 != null) {
                    val b0 = if (t1 != 0) (if (t2 == 1) 0x02 else 0x03) else 0x00
                    val b1 = if (t1 != 0) (if (t2 == 1) 0x02 else 0x01) else 0x00
                    _soundControl.value = SoundControl(AncMode.fromBytes(b0, b1))
                }
            }
            0x2B to 0x25 -> {
                InEarState.fromTlvs(frame.tlvs)?.let { _inEarState.value = it }
            }
            else -> Log.d("FreeBudsManager", "Unhandled frame: $frameId")
        }
    }

    fun sendFrame(frame: ByteArray) {
        Log.d("FreeBudsManager", "sendFrame: ${frame.size} bytes")
        scope.launch {
            try {
                socket?.outputStream?.write(frame) ?: Log.w("FreeBudsManager", "socket is null!")
                Log.d("FreeBudsManager", "write complete")
            } catch (e: IOException) {
                Log.e("FreeBudsManager", "Write error", e)
                disconnect()
            }
        }
    }

    fun setAncMode(mode: AncMode) {
        Log.d("FreeBudsManager", "setAncMode($mode)")
        val tlv = try {
            SoundControl.toTlv(mode)
        } catch (e: Exception) {
            Log.e("FreeBudsManager", "Failed to encode AncMode: ${e.message}")
            return
        }
        val frame = Frame.build(0x2B, 0x04, listOf(tlv))
        Log.d("FreeBudsManager", "TX: ${frame.joinToString(" ") { "%02X".format(it) }}")
        sendFrame(frame)
    }

    fun disconnect() {
        _isConnected.value = false
        try {
            socket?.close()
        } catch (e: IOException) {
            Log.e("FreeBudsManager", "Error closing socket", e)
        }
        socket = null
    }
    
    fun release() {
        disconnect()
        scope.cancel()
    }
}
