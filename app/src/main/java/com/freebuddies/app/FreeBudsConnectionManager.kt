package com.freebuddies.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.freebuddies.app.bluetooth.FreeBudsManager
import com.freebuddies.app.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalCoroutinesApi::class)
object FreeBudsConnectionManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var prefs: SharedPreferences

    private val _manager = MutableStateFlow<FreeBudsManager?>(null)

    private val _preferredNcIntensity = MutableStateFlow(NcIntensity.GENERAL)
    val preferredNcIntensity: StateFlow<NcIntensity> = _preferredNcIntensity.asStateFlow()

    val isConnected: StateFlow<Boolean> =
        _manager.flatMapLatest { it?.isConnected ?: flowOf(false) }
            .stateIn(scope, SharingStarted.Eagerly, false)

    val batteryStatus: StateFlow<BatteryStatus?> =
        _manager.flatMapLatest { it?.batteryStatus ?: flowOf(null) }
            .stateIn(scope, SharingStarted.Eagerly, null)

    val soundControl: StateFlow<SoundControl?> =
        _manager.flatMapLatest { it?.soundControl ?: flowOf(null) }
            .stateIn(scope, SharingStarted.Eagerly, null)

    val deviceInfo: StateFlow<DeviceInfo?> =
        _manager.flatMapLatest { it?.deviceInfo ?: flowOf(null) }
            .stateIn(scope, SharingStarted.Eagerly, null)

    val inEarState: StateFlow<InEarState?> =
        _manager.flatMapLatest { it?.inEarState ?: flowOf(null) }
            .stateIn(scope, SharingStarted.Eagerly, null)

    val ringingStatus: StateFlow<RingingStatus> =
        _manager.flatMapLatest { it?.ringingStatus ?: flowOf(RingingStatus(left = false, right = false)) }
            .stateIn(scope, SharingStarted.Eagerly, RingingStatus(left = false, right = false))

    val earTipType: StateFlow<EarTipType?> =
        _manager.flatMapLatest { it?.earTipType ?: flowOf(null) }
            .stateIn(scope, SharingStarted.Eagerly, null)

    val lowLatency: StateFlow<Boolean?> =
        _manager.flatMapLatest { it?.lowLatency ?: flowOf(null) }
            .stateIn(scope, SharingStarted.Eagerly, null)

    val wearDetection: StateFlow<Boolean?> =
        _manager.flatMapLatest { it?.wearDetection ?: flowOf(null) }
            .stateIn(scope, SharingStarted.Eagerly, null)

    val caseTone: StateFlow<Boolean?> =
        _manager.flatMapLatest { it?.caseTone ?: flowOf(null) }
            .stateIn(scope, SharingStarted.Eagerly, null)

    val headControl: StateFlow<Boolean?> =
        _manager.flatMapLatest { it?.headControl ?: flowOf(null) }
            .stateIn(scope, SharingStarted.Eagerly, null)

    val nodAction: StateFlow<HeadGestureAction?> =
        _manager.flatMapLatest { it?.nodAction ?: flowOf(null) }
            .stateIn(scope, SharingStarted.Eagerly, null)

    val shakeAction: StateFlow<HeadGestureAction?> =
        _manager.flatMapLatest { it?.shakeAction ?: flowOf(null) }
            .stateIn(scope, SharingStarted.Eagerly, null)

    val pairedDevices: StateFlow<List<PairedDevice>> =
        _manager.flatMapLatest { it?.pairedDevices ?: flowOf(emptyList()) }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val eqPreset: StateFlow<EqPreset?> =
        _manager.flatMapLatest { it?.eqPreset ?: flowOf(null) }
            .stateIn(scope, SharingStarted.Eagerly, null)

    val eqPresetCode: StateFlow<Int> =
        _manager.flatMapLatest { it?.eqPresetCode ?: flowOf(-1) }
            .stateIn(scope, SharingStarted.Eagerly, -1)

    val voiceLanguage: StateFlow<String?> =
        _manager.flatMapLatest { it?.voiceLanguage ?: flowOf(null) }
            .stateIn(scope, SharingStarted.Eagerly, null)

    val voiceLanguages: StateFlow<List<String>> =
        _manager.flatMapLatest { it?.voiceLanguages ?: flowOf(emptyList()) }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val deviceEqPresets: StateFlow<List<DeviceEqPreset>> =
        _manager.flatMapLatest { it?.deviceEqPresets ?: flowOf(emptyList()) }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val doubleTap: StateFlow<TapGestureConfig?> =
        _manager.flatMapLatest { it?.doubleTap ?: flowOf(null) }
            .stateIn(scope, SharingStarted.Eagerly, null)

    val tripleTap: StateFlow<TapGestureConfig?> =
        _manager.flatMapLatest { it?.tripleTap ?: flowOf(null) }
            .stateIn(scope, SharingStarted.Eagerly, null)

    val longTap: StateFlow<LongTapConfig?> =
        _manager.flatMapLatest { it?.longTap ?: flowOf(null) }
            .stateIn(scope, SharingStarted.Eagerly, null)

    val swipe: StateFlow<SwipeConfig?> =
        _manager.flatMapLatest { it?.swipe ?: flowOf(null) }
            .stateIn(scope, SharingStarted.Eagerly, null)

    private val _ancVoiceAnnounce = MutableStateFlow(true)
    val ancVoiceAnnounce: StateFlow<Boolean> = _ancVoiceAnnounce.asStateFlow()

    private val _targetDevice = MutableStateFlow<BluetoothDevice?>(null)
    val targetDevice: StateFlow<BluetoothDevice?> = _targetDevice.asStateFlow()

    private var autoReconnectJob: Job? = null
    private var isConnecting = false

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("freebuddies", Context.MODE_PRIVATE)
        _preferredNcIntensity.value = NcIntensity.fromCode(prefs.getInt("nc_intensity", NcIntensity.GENERAL.code))
        _ancVoiceAnnounce.value = prefs.getBoolean("anc_voice_announce", true)
    }

    fun setPreferredNcIntensity(intensity: NcIntensity) {
        _preferredNcIntensity.value = intensity
        prefs.edit { putInt("nc_intensity", intensity.code) }
    }

    fun startAutoReconnect(context: Context) {
        if (autoReconnectJob != null) return
        autoReconnectJob = scope.launch {
            while (true) {
                val connected = _manager.value?.isConnected?.value ?: false
                if (!connected && !isConnecting) {
                    val known = _targetDevice.value
                    if (known != null) {
                        connect(known)
                    } else {
                        findDevice(context)
                    }
                }
                delay(1000)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun findDevice(context: Context) {
        if (isConnecting) return
        isConnecting = true
        DebugLog.d(LogTag.APP, "Scanning for devices")
        try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = bluetoothManager.adapter ?: run {
                isConnecting = false
                return
            }

            val pairedDevices = adapter.bondedDevices
            DebugLog.d(LogTag.APP, "Found ${pairedDevices.size} paired devices")
            val buds = pairedDevices.find { it.name?.contains("FreeBuds", ignoreCase = true) == true }

            if (buds != null) {
                DebugLog.d(LogTag.APP, "Found ${buds.name}")
                _targetDevice.value = buds
                connect(buds)
            } else {
                DebugLog.d(LogTag.APP, "Target device not found")
                isConnecting = false
            }
        } catch (e: SecurityException) {
            DebugLog.e(LogTag.APP, "Permission error: ${e.message}", e)
            isConnecting = false
        }
    }

    private fun connect(device: BluetoothDevice) {
        isConnecting = true
        _manager.value?.release()
        val newManager = FreeBudsManager(device)
        newManager.ancVoiceAnnounce = _ancVoiceAnnounce.value
        _manager.value = newManager

        scope.launch {
            try {
                withTimeout(3000) {
                    newManager.isConnected.first { it }
                }
            } catch (e: Exception) {
                DebugLog.d(LogTag.APP, "Connection timeout: ${e.message}")
            } finally {
                isConnecting = false
            }
        }

        newManager.connect()
    }

    fun onResume(context: Context) {
        val manager = _manager.value
        if (manager != null && manager.isConnected.value) {
            manager.refreshState()
            return
        }
        if (isConnecting) return
        // Fast path: reconnect to the known device without re-scanning
        val known = _targetDevice.value
        if (known != null) {
            connect(known)
        } else {
            findDevice(context)
        }
    }

    fun setAncMode(mode: AncMode, intensity: NcIntensity = NcIntensity.GENERAL, voiceMode: Boolean = false) {
        _manager.value?.setAncMode(mode, intensity, voiceMode)
    }

    fun setRinging(side: Int, active: Boolean) {
        _manager.value?.setRinging(side, active)
    }

    fun setEarTipType(type: EarTipType) {
        _manager.value?.setEarTipType(type)
    }

    fun setLowLatency(enabled: Boolean) {
        _manager.value?.setLowLatency(enabled)
    }

    fun setWearDetection(enabled: Boolean) {
        _manager.value?.setWearDetection(enabled)
    }

    fun setCaseTone(enabled: Boolean) {
        _manager.value?.setCaseTone(enabled)
    }

    fun setHeadControl(enabled: Boolean) {
        _manager.value?.setHeadControl(enabled)
    }

    fun setNodAction(action: HeadGestureAction) {
        _manager.value?.setNodAction(action)
    }

    fun setShakeAction(action: HeadGestureAction) {
        _manager.value?.setShakeAction(action)
    }

    fun refreshGestureConfig() { _manager.value?.refreshGestureConfig() }
    fun setDoubleTap(side: Int, action: TapAction) { _manager.value?.setDoubleTap(side, action) }
    fun setTripleTap(side: Int, action: TapAction) { _manager.value?.setTripleTap(side, action) }
    fun setLongTap(side: Int, action: LongTapAction) { _manager.value?.setLongTap(side, action) }
    fun setSwipe(enabled: Boolean) { _manager.value?.setSwipe(enabled) }

    fun setAncVoiceAnnounce(enabled: Boolean) {
        _ancVoiceAnnounce.value = enabled
        _manager.value?.ancVoiceAnnounce = enabled
        prefs.edit { putBoolean("anc_voice_announce", enabled) }
    }

    fun setVoiceLanguage(language: String) {
        _manager.value?.setVoiceLanguage(language)
    }

    fun setEqPreset(preset: EqPreset) {
        _manager.value?.setEqPreset(preset)
    }

    fun applyCustomEqProfile(profile: CustomEqProfile) {
        _manager.value?.setCustomEqProfile(profile)
    }

    fun deleteDeviceEqPreset(preset: DeviceEqPreset) {
        _manager.value?.deleteDeviceEqPreset(preset)
    }

    fun saveDeviceEqPreset(slotId: Int, name: String, bands: List<Int>) {
        _manager.value?.saveDeviceEqPreset(slotId, name, bands)
    }

}
