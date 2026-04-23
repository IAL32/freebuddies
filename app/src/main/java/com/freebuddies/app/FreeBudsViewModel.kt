package com.freebuddies.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freebuddies.app.bluetooth.FreeBudsManager
import com.freebuddies.app.protocol.AncMode
import com.freebuddies.app.protocol.NcIntensity
import com.freebuddies.app.protocol.RingingStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

@OptIn(ExperimentalCoroutinesApi::class)
class FreeBudsViewModel : ViewModel() {
    private val _manager = MutableStateFlow<FreeBudsManager?>(null)
    
    val isConnected: Flow<Boolean> = _manager.flatMapLatest { it?.isConnected ?: flowOf(value = false) }
    val batteryStatus = _manager.flatMapLatest { it?.batteryStatus ?: flowOf(null) }
    val soundControl = _manager.flatMapLatest { it?.soundControl ?: flowOf(null) }
    val deviceInfo = _manager.flatMapLatest { it?.deviceInfo ?: flowOf(null) }
    val inEarState = _manager.flatMapLatest { it?.inEarState ?: flowOf(null) }
    val ringingStatus = _manager.flatMapLatest { it?.ringingStatus ?: flowOf(RingingStatus(false, false)) }

    private val _targetDevice = MutableStateFlow<BluetoothDevice?>(null)
    val targetDevice = _targetDevice.asStateFlow()

    private var autoReconnectJob: kotlinx.coroutines.Job? = null
    private var isConnecting = false

    fun startAutoReconnect(context: Context) {
        if (autoReconnectJob != null) return
        autoReconnectJob = viewModelScope.launch {
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
            val adapter = bluetoothManager.adapter ?: return

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
        
        viewModelScope.launch {
            // Wait for connection result or timeout
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

    override fun onCleared() {
        _manager.value?.release()
        super.onCleared()
    }
}
