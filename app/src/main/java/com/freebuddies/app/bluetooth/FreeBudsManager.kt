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

    private val _eqPreset = MutableStateFlow<EqPreset?>(null)
    val eqPreset = _eqPreset.asStateFlow()

    private val _earTipType = MutableStateFlow<EarTipType?>(null)
    val earTipType = _earTipType.asStateFlow()

    /** Raw EQ preset code from the buds (includes custom IDs like 0x64). */
    private val _eqPresetCode = MutableStateFlow(-1)
    val eqPresetCode = _eqPresetCode.asStateFlow()

    private val _lowLatency = MutableStateFlow<Boolean?>(null)
    val lowLatency = _lowLatency.asStateFlow()

    private val _wearDetection = MutableStateFlow<Boolean?>(null)
    val wearDetection = _wearDetection.asStateFlow()

    private val _caseTone = MutableStateFlow<Boolean?>(null)
    val caseTone = _caseTone.asStateFlow()

    private val _headControl = MutableStateFlow<Boolean?>(null)
    val headControl = _headControl.asStateFlow()

    private val _nodAction = MutableStateFlow<HeadGestureAction?>(null)
    val nodAction = _nodAction.asStateFlow()

    private val _shakeAction = MutableStateFlow<HeadGestureAction?>(null)
    val shakeAction = _shakeAction.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<PairedDevice>>(emptyList())
    val pairedDevices = _pairedDevices.asStateFlow()

    private val _voiceLanguage = MutableStateFlow<String?>(null)
    val voiceLanguage = _voiceLanguage.asStateFlow()

    private val _voiceLanguages = MutableStateFlow<List<String>>(emptyList())
    val voiceLanguages = _voiceLanguages.asStateFlow()

    private val _deviceEqPresets = MutableStateFlow<List<DeviceEqPreset>>(emptyList())
    val deviceEqPresets = _deviceEqPresets.asStateFlow()

    private val _doubleTap = MutableStateFlow<TapGestureConfig?>(null)
    val doubleTap = _doubleTap.asStateFlow()

    private val _tripleTap = MutableStateFlow<TapGestureConfig?>(null)
    val tripleTap = _tripleTap.asStateFlow()

    private val _longTap = MutableStateFlow<LongTapConfig?>(null)
    val longTap = _longTap.asStateFlow()

    private val _swipe = MutableStateFlow<SwipeConfig?>(null)
    val swipe = _swipe.asStateFlow()

    /** When true, send 0xFF intensity on mode switches so the buds play a voice announcement. */
    var ancVoiceAnnounce: Boolean = true

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
                sendFrame(Frame.build(0x01, 0x07, ping)) // Get system info
                sendFrame(Frame.build(0x2B, 0x0A, ping)) // Get device info
                sendFrame(Frame.build(0x2B, 0x2A, ping)) // Get ANC mode
                sendFrame(Frame.build(0x2B, 0x4A, (1..8).map { Tlv(it, byteArrayOf()) })) // Get EQ state + device presets
                sendFrame(Frame.build(0x2B, 0xB4, listOf(Tlv(0x01, byteArrayOf(0x08)), Tlv(0x02, byteArrayOf())))) // Get ear tips
                sendFrame(Frame.build(0x2B, 0xA3, emptyList())) // Get low latency
                sendFrame(Frame.build(0x2B, 0x11, listOf(Tlv(0x01, byteArrayOf())))) // Get wear detection
                sendFrame(Frame.build(0x2B, 0xB4, listOf(Tlv(0x01, byteArrayOf(0x0B)), Tlv(0x02, byteArrayOf())))) // Get case tone + head gestures
                sendFrame(Frame.build(0x2B, 0x31, listOf(Tlv(0x01, byteArrayOf())))) // Get paired devices
                sendFrame(Frame.build(0x0C, 0x02, listOf(Tlv(0x01, byteArrayOf()), Tlv(0x02, byteArrayOf())))) // Get voice languages

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
            0x01 to 0x07 -> {
                DeviceInfo.mergeFromSystemInfo(_deviceInfo.value, frame.tlvs)?.let {
                    _deviceInfo.value = it
                    DebugLog.d(LogTag.BUDS, "System info fw=${it.fullFirmware}")
                }
            }
            0x2B to 0x0A -> {
                DeviceInfo.fromDeviceInfoTlvs(frame.tlvs)?.let {
                    _deviceInfo.value = it.copy(
                        fullFirmware = _deviceInfo.value?.fullFirmware ?: "",
                        bluetoothFirmwareId = _deviceInfo.value?.bluetoothFirmwareId ?: "",
                        budSerials = _deviceInfo.value?.budSerials ?: "",
                    )
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
                PairedDevice.fromTlvs(frame.tlvs)?.let { device ->
                    val current = _pairedDevices.value.toMutableList()
                    val idx = current.indexOfFirst { it.address.contentEquals(device.address) }
                    if (idx >= 0) current[idx] = device else current.add(device)
                    _pairedDevices.value = current
                    val state = when (device.playbackState) {
                        0x09 -> "playing"; 0x03 -> "paused"; else -> "stopped"
                    }
                    DebugLog.d(LogTag.BUDS, "Audio source \"${device.name}\" ${if (device.connected) "connected" else "disconnected"} $state")
                }
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
            0x2B to 0x03 -> {
                // Legacy ANC mode change from physical button press
                val modeCode = frame.tlvs.find { it.type == 0x01 }?.value?.getOrNull(0)?.toInt()?.and(0xFF) ?: -1
                val mode = AncMode.fromCode(modeCode)
                if (mode != AncMode.UNKNOWN) {
                    _soundControl.value = _soundControl.value?.copy(mode = mode) ?: SoundControl(mode)
                    DebugLog.d(LogTag.BUDS, "ANC legacy event: $mode")
                }
                if (ancVoiceAnnounce) {
                    // Ack with (0x2B, 0x2A) + empty T01 to trigger voice announcement
                    sendFrame(Frame.build(0x2B, 0x2A, listOf(Tlv(0x01, byteArrayOf()))))
                    DebugLog.d(LogTag.TX, "ANC voice ack sent")
                }
            }
            0x2B to 0x04 -> {
                val status = frame.tlvs.firstOrNull { it.type == 0x02 }
                    ?.value?.getOrNull(0)?.toInt()?.and(0xFF) ?: -1
                if (status != 0) {
                    DebugLog.w(LogTag.BUDS, "ANC write rejected, status=$status")
                }
            }
            0x2B to 0xB4 -> {
                val subCmd = frame.tlvs.find { it.type == 0x01 }?.value?.getOrNull(0)?.toInt()?.and(0xFF)
                when (subCmd) {
                    0x08 -> {
                        // Ear tips — T02 = tip type
                        val tipCode = frame.tlvs.find { it.type == 0x02 }?.value?.getOrNull(0)?.toInt()?.and(0xFF)
                        if (tipCode != null) {
                            EarTipType.fromCode(tipCode)?.let {
                                _earTipType.value = it
                                DebugLog.d(LogTag.BUDS, "Ear tips ${it.label}")
                            }
                        }
                    }
                    0x0B -> {
                        // Case tone (T02), nod action (T03), shake action (T04)
                        frame.tlvs.find { it.type == 0x02 }?.value?.getOrNull(0)?.let { b ->
                            _caseTone.value = b.toInt() == 1
                            DebugLog.d(LogTag.BUDS, "Case tone ${if (b.toInt() == 1) "on" else "off"}")
                        }
                        frame.tlvs.find { it.type == 0x03 }?.value?.getOrNull(0)?.let { b ->
                            HeadGestureAction.fromCode(b.toInt() and 0xFF)?.let { _nodAction.value = it }
                        }
                        frame.tlvs.find { it.type == 0x04 }?.value?.getOrNull(0)?.let { b ->
                            HeadGestureAction.fromCode(b.toInt() and 0xFF)?.let { _shakeAction.value = it }
                        }
                    }
                }
            }
            0x2B to 0xA3 -> {
                // Low audio latency — T02 = latency on/off
                frame.tlvs.find { it.type == 0x02 }?.value?.getOrNull(0)?.let { b ->
                    _lowLatency.value = b.toInt() != 0
                    DebugLog.d(LogTag.BUDS, "Low latency ${if (b.toInt() != 0) "on" else "off"}")
                }
            }
            0x2B to 0x11 -> {
                // Wear detection read — T01 = on/off
                frame.tlvs.find { it.type == 0x01 }?.value?.getOrNull(0)?.let { b ->
                    _wearDetection.value = b.toInt() == 1
                    DebugLog.d(LogTag.BUDS, "Wear detection ${if (b.toInt() == 1) "on" else "off"}")
                }
            }
            0x2B to 0x6C -> {
                // Head control read — T02 = on/off
                frame.tlvs.find { it.type == 0x02 }?.value?.getOrNull(0)?.let { b ->
                    _headControl.value = b.toInt() != 0
                    DebugLog.d(LogTag.BUDS, "Head control ${if (b.toInt() != 0) "on" else "off"}")
                }
            }
            0x2B to 0x4A -> {
                // EQ capabilities — Tag 02 = current preset id, Tag 08 = device-stored custom presets
                val currentCode = frame.tlvs.find { it.type == 0x02 }
                    ?.value?.getOrNull(0)?.toInt()?.and(0xFF)
                if (currentCode != null) {
                    _eqPresetCode.value = currentCode
                    val preset = EqPreset.fromCode(currentCode)
                    _eqPreset.value = preset
                    DebugLog.d(LogTag.BUDS, "EQ preset ${preset?.label ?: "custom(${"%02X".format(currentCode)})"}")
                }
                frame.tlvs.find { it.type == 0x08 }?.value?.let { data ->
                    val presets = DeviceEqPreset.parseAll(data)
                    _deviceEqPresets.value = presets
                    presets.forEach { p ->
                        DebugLog.d(LogTag.BUDS, "Device EQ preset id=${"%02X".format(p.id)} \"${p.name}\"")
                    }
                }
            }
            0x01 to 0x20 -> {
                // Double-tap gesture config
                _doubleTap.value = TapGestureConfig.fromTlvs(frame.tlvs)
                DebugLog.d(LogTag.BUDS, "Double-tap L=${_doubleTap.value?.left?.label} R=${_doubleTap.value?.right?.label}")
            }
            0x01 to 0x26 -> {
                // Triple-tap gesture config
                _tripleTap.value = TapGestureConfig.fromTlvs(frame.tlvs)
                DebugLog.d(LogTag.BUDS, "Triple-tap L=${_tripleTap.value?.left?.label} R=${_tripleTap.value?.right?.label}")
            }
            0x2B to 0x17 -> {
                // Long-tap gesture config
                _longTap.value = LongTapConfig.fromTlvs(frame.tlvs)
                DebugLog.d(LogTag.BUDS, "Long-tap L=${_longTap.value?.left?.label} R=${_longTap.value?.right?.label}")
            }
            0x2B to 0x1F -> {
                // Swipe gesture config
                _swipe.value = SwipeConfig.fromTlvs(frame.tlvs)
                DebugLog.d(LogTag.BUDS, "Swipe ${if (_swipe.value?.enabled == true) "on" else "off"}")
            }
            0x0C to 0x02 -> {
                // Voice language list — T01 = current language, T03 = available languages (comma-separated)
                frame.tlvs.find { it.type == 0x01 }?.value?.let { v ->
                    if (v.isNotEmpty()) {
                        val lang = v.toString(Charsets.UTF_8)
                        _voiceLanguage.value = lang
                        DebugLog.d(LogTag.BUDS, "Voice language: $lang")
                    }
                }
                frame.tlvs.find { it.type == 0x03 }?.value?.let { v ->
                    if (v.isNotEmpty()) {
                        val raw = v.toString(Charsets.UTF_8)
                        val languages = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        _voiceLanguages.value = languages
                        DebugLog.d(LogTag.BUDS, "Available languages: $languages")
                    }
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
            sendFrame(Frame.build(0x2B, 0x4A, listOf(Tlv(0x02, byteArrayOf()))))
            sendFrame(Frame.build(0x2B, 0xB4, listOf(Tlv(0x01, byteArrayOf(0x08)), Tlv(0x02, byteArrayOf()))))
            sendFrame(Frame.build(0x2B, 0xA3, emptyList()))
            sendFrame(Frame.build(0x2B, 0x11, listOf(Tlv(0x01, byteArrayOf()))))
            sendFrame(Frame.build(0x2B, 0xB4, listOf(Tlv(0x01, byteArrayOf(0x0B)), Tlv(0x02, byteArrayOf()))))
        }
    }

    fun setEarTipType(type: EarTipType) {
        DebugLog.d(LogTag.APP, "Set ear tips ${type.label}")
        sendFrame(Frame.build(0x2B, 0xB4, listOf(
            Tlv(0x01, byteArrayOf(0x08)),
            Tlv(0x02, byteArrayOf(type.code.toByte()))
        )))
    }

    fun setEqPreset(preset: EqPreset) {
        DebugLog.d(LogTag.APP, "Set EQ ${preset.label}")
        _eqPreset.value = preset
        _eqPresetCode.value = preset.code
        sendFrame(Frame.build(0x2B, 0x49, listOf(Tlv(0x01, byteArrayOf(preset.code.toByte())))))
    }

    fun setCustomEqProfile(profile: CustomEqProfile) {
        DebugLog.d(LogTag.APP, "Set custom EQ \"${profile.name}\"")
        _eqPreset.value = null
        _eqPresetCode.value = profile.slotCode
        // Each UI step (±1) maps to ±10 in the internal byte scale
        val curve = ByteArray(10) { i -> ((profile.bands.getOrElse(i) { 0 }) * 10).toByte() }
        val nameBytes = profile.name.toByteArray(Charsets.US_ASCII)
        val tlvs = listOf(
            Tlv(0x01, byteArrayOf(profile.slotCode.toByte())),
            Tlv(0x02, byteArrayOf(0x0A)),           // 10 bands
            Tlv(0x05, byteArrayOf(0x01)),            // active
            Tlv(0x03, curve),                         // EQ curve
            Tlv(0x04, nameBytes),                     // name
        )
        sendFrame(Frame.build(0x2B, 0x49, tlvs))
    }

    fun setLowLatency(enabled: Boolean) {
        DebugLog.d(LogTag.APP, "Set low latency ${if (enabled) "on" else "off"}")
        sendFrame(Frame.build(0x2B, 0xA2, listOf(Tlv(0x01, byteArrayOf(if (enabled) 0x01 else 0x00)))))
    }

    fun setWearDetection(enabled: Boolean) {
        DebugLog.d(LogTag.APP, "Set wear detection ${if (enabled) "on" else "off"}")
        sendFrame(Frame.build(0x2B, 0x10, listOf(Tlv(0x01, byteArrayOf(if (enabled) 0x01 else 0x00)))))
        _wearDetection.value = enabled
    }

    fun setCaseTone(enabled: Boolean) {
        DebugLog.d(LogTag.APP, "Set case tone ${if (enabled) "on" else "off"}")
        sendFrame(Frame.build(0x2B, 0xB4, listOf(
            Tlv(0x01, byteArrayOf(0x0B)),
            Tlv(0x02, byteArrayOf(if (enabled) 0x01 else 0x00))
        )))
    }

    fun setHeadControl(enabled: Boolean) {
        DebugLog.d(LogTag.APP, "Set head control ${if (enabled) "on" else "off"}")
        sendFrame(Frame.build(0x2B, 0x6C, listOf(Tlv(0x01, byteArrayOf(if (enabled) 0x01 else 0x00)))))
        _headControl.value = enabled
    }

    fun setNodAction(action: HeadGestureAction) {
        DebugLog.d(LogTag.APP, "Set nod ${action.label}")
        sendFrame(Frame.build(0x2B, 0xB4, listOf(
            Tlv(0x01, byteArrayOf(0x0B)),
            Tlv(0x03, byteArrayOf(action.code.toByte()))
        )))
    }

    fun setShakeAction(action: HeadGestureAction) {
        DebugLog.d(LogTag.APP, "Set shake ${action.label}")
        sendFrame(Frame.build(0x2B, 0xB4, listOf(
            Tlv(0x01, byteArrayOf(0x0B)),
            Tlv(0x04, byteArrayOf(action.code.toByte()))
        )))
    }

    fun refreshGestureConfig() {
        val params = listOf(Tlv(0x01, byteArrayOf()), Tlv(0x02, byteArrayOf()))
        sendFrame(Frame.build(0x2B, 0x6C, listOf(Tlv(0x02, byteArrayOf())))) // head control
        sendFrame(Frame.build(0x01, 0x20, params)) // double-tap
        sendFrame(Frame.build(0x01, 0x26, params)) // triple-tap
        sendFrame(Frame.build(0x2B, 0x17, params)) // long-tap
        sendFrame(Frame.build(0x2B, 0x1F, params)) // swipe
    }

    fun setDoubleTap(side: Int, action: TapAction) {
        DebugLog.d(LogTag.APP, "Set double-tap ${if (side == 1) "left" else "right"} ${action.label}")
        sendFrame(Frame.build(0x01, 0x1F, listOf(Tlv(side, byteArrayOf(action.code.toByte())))))
        _doubleTap.value = _doubleTap.value?.let {
            if (side == 1) it.copy(left = action) else it.copy(right = action)
        }
    }

    fun setTripleTap(side: Int, action: TapAction) {
        DebugLog.d(LogTag.APP, "Set triple-tap ${if (side == 1) "left" else "right"} ${action.label}")
        sendFrame(Frame.build(0x01, 0x25, listOf(Tlv(side, byteArrayOf(action.code.toByte())))))
        _tripleTap.value = _tripleTap.value?.let {
            if (side == 1) it.copy(left = action) else it.copy(right = action)
        }
    }

    fun setLongTap(side: Int, action: LongTapAction) {
        DebugLog.d(LogTag.APP, "Set long-tap ${if (side == 1) "left" else "right"} ${action.label}")
        sendFrame(Frame.build(0x2B, 0x16, listOf(Tlv(side, byteArrayOf(action.code.toByte())))))
        _longTap.value = _longTap.value?.let {
            if (side == 1) it.copy(left = action) else it.copy(right = action)
        }
    }

    fun setSwipe(enabled: Boolean) {
        val code = if (enabled) 0x00 else 0xFF.toByte().toInt()
        DebugLog.d(LogTag.APP, "Set swipe ${if (enabled) "on" else "off"}")
        sendFrame(Frame.build(0x2B, 0x1E, listOf(
            Tlv(0x01, byteArrayOf(code.toByte())),
            Tlv(0x02, byteArrayOf(code.toByte())),
        )))
        _swipe.value = SwipeConfig(enabled)
    }

    fun setAncMode(mode: AncMode, intensity: NcIntensity = NcIntensity.GENERAL, voiceMode: Boolean = false) {
        val label = when (mode) {
            AncMode.NOISE_CANCELLING -> "NC ${intensity.label}"
            AncMode.AWARENESS -> "Awareness" + if (voiceMode) " (voice)" else ""
            else -> "$mode"
        }
        DebugLog.d(LogTag.APP, "Set ANC $label")

        val currentMode = _soundControl.value?.mode
        val isModeSwitch = currentMode == null || currentMode != mode

        val tlv = try {
            SoundControl.toTlv(mode, intensity, voiceMode, modeSwitch = isModeSwitch && ancVoiceAnnounce)
        } catch (e: Exception) {
            DebugLog.e(LogTag.APP, "Failed to encode ANC: ${e.message}")
            return
        }
        sendFrame(Frame.build(0x2B, 0x04, listOf(tlv)))
    }

    fun setVoiceLanguage(language: String) {
        DebugLog.d(LogTag.APP, "Set voice language $language")
        sendFrame(Frame.build(0x0C, 0x01, listOf(
            Tlv(0x01, language.toByteArray(Charsets.UTF_8)),
            Tlv(0x02, byteArrayOf(0x01)),
        )))
        _voiceLanguage.value = language
    }

    fun deleteDeviceEqPreset(preset: DeviceEqPreset) {
        DebugLog.d(LogTag.APP, "Delete device EQ preset \"${preset.name}\" id=${"%02X".format(preset.id)}")
        val curve = ByteArray(10) { i -> (preset.bands.getOrElse(i) { 0 }).toByte() }
        val nameBytes = preset.name.toByteArray(Charsets.UTF_8)
        sendFrame(Frame.build(0x2B, 0x49, listOf(
            Tlv(0x01, byteArrayOf(preset.id.toByte())),
            Tlv(0x02, byteArrayOf(0x0A)),
            Tlv(0x03, curve),
            Tlv(0x04, nameBytes),
            Tlv(0x05, byteArrayOf(0x02)), // action = delete
        )))
        _deviceEqPresets.value = _deviceEqPresets.value.filter { it.id != preset.id }
    }

    fun saveDeviceEqPreset(slotId: Int, name: String, bands: List<Int>) {
        DebugLog.d(LogTag.APP, "Save device EQ preset \"$name\" id=${"%02X".format(slotId)}")
        val curve = ByteArray(10) { i -> ((bands.getOrElse(i) { 0 }) * 10).toByte() }
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        sendFrame(Frame.build(0x2B, 0x49, listOf(
            Tlv(0x01, byteArrayOf(slotId.toByte())),
            Tlv(0x02, byteArrayOf(0x0A)),
            Tlv(0x05, byteArrayOf(0x01)), // action = save & apply
            Tlv(0x03, curve),
            Tlv(0x04, nameBytes),
        )))
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
