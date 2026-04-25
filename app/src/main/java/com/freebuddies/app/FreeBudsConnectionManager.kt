package com.freebuddies.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.SharedPreferences
import com.freebuddies.app.bluetooth.FreeBudsManager
import com.freebuddies.app.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject

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
        _manager.flatMapLatest { it?.ringingStatus ?: flowOf(RingingStatus(false, false)) }
            .stateIn(scope, SharingStarted.Eagerly, RingingStatus(false, false))

    val eqPreset: StateFlow<EqPreset?> =
        _manager.flatMapLatest { it?.eqPreset ?: flowOf(null) }
            .stateIn(scope, SharingStarted.Eagerly, null)

    val eqPresetCode: StateFlow<Int> =
        _manager.flatMapLatest { it?.eqPresetCode ?: flowOf(-1) }
            .stateIn(scope, SharingStarted.Eagerly, -1)

    private val _customEqProfiles = MutableStateFlow<List<CustomEqProfile>>(emptyList())
    val customEqProfiles: StateFlow<List<CustomEqProfile>> = _customEqProfiles.asStateFlow()

    private val _targetDevice = MutableStateFlow<BluetoothDevice?>(null)
    val targetDevice: StateFlow<BluetoothDevice?> = _targetDevice.asStateFlow()

    private var autoReconnectJob: Job? = null
    private var isConnecting = false

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("freebuddies", Context.MODE_PRIVATE)
        _preferredNcIntensity.value = NcIntensity.fromCode(prefs.getInt("nc_intensity", NcIntensity.GENERAL.code))
        _customEqProfiles.value = loadCustomProfiles()
    }

    fun setPreferredNcIntensity(intensity: NcIntensity) {
        _preferredNcIntensity.value = intensity
        prefs.edit().putInt("nc_intensity", intensity.code).apply()
    }

    fun startAutoReconnect(context: Context) {
        if (autoReconnectJob != null) return
        autoReconnectJob = scope.launch {
            while (true) {
                val currentManager = _manager.value
                val connected = currentManager?.isConnected?.value ?: false

                if (!connected && !isConnecting) {
                    findDevice(context)
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
        _manager.value?.release()
        val newManager = FreeBudsManager(device)
        _manager.value = newManager

        scope.launch {
            try {
                withTimeout(5000) {
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
        } else if (!isConnecting) {
            findDevice(context)
        }
    }

    fun setAncMode(mode: AncMode, intensity: NcIntensity = NcIntensity.GENERAL, voiceMode: Boolean = false) {
        _manager.value?.setAncMode(mode, intensity, voiceMode)
    }

    fun setRinging(side: Int, active: Boolean) {
        _manager.value?.setRinging(side, active)
    }

    fun setEqPreset(preset: EqPreset) {
        _manager.value?.setEqPreset(preset)
    }

    fun applyCustomEqProfile(profile: CustomEqProfile) {
        _manager.value?.setCustomEqProfile(profile)
    }

    fun saveCustomEqProfile(profile: CustomEqProfile) {
        val list = _customEqProfiles.value.toMutableList()
        val existingIndex = list.indexOfFirst { it.name == profile.name }
        if (existingIndex >= 0) {
            list[existingIndex] = profile
        } else {
            list.add(profile)
        }
        _customEqProfiles.value = list
        persistCustomProfiles(list)
    }

    fun deleteCustomEqProfile(profile: CustomEqProfile) {
        val list = _customEqProfiles.value.filter { it.name != profile.name }
        _customEqProfiles.value = list
        persistCustomProfiles(list)
    }

    private fun loadCustomProfiles(): List<CustomEqProfile> {
        val json = prefs.getString("custom_eq_profiles", null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val bandsArr = obj.getJSONArray("bands")
                CustomEqProfile(
                    name = obj.getString("name"),
                    bands = (0 until bandsArr.length()).map { j -> bandsArr.getInt(j) }
                )
            }
        } catch (e: Exception) {
            DebugLog.e(LogTag.APP, "Failed to load custom EQ profiles: ${e.message}")
            emptyList()
        }
    }

    private fun persistCustomProfiles(profiles: List<CustomEqProfile>) {
        val arr = JSONArray()
        for (p in profiles) {
            val obj = JSONObject()
            obj.put("name", p.name)
            val bands = JSONArray()
            p.bands.forEach { bands.put(it) }
            obj.put("bands", bands)
            arr.put(obj)
        }
        prefs.edit().putString("custom_eq_profiles", arr.toString()).apply()
    }
}
