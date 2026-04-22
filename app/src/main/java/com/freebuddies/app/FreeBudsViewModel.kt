package com.freebuddies.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freebuddies.app.bluetooth.FreeBudsManager
import com.freebuddies.app.protocol.AncMode
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

    private val _targetDevice = MutableStateFlow<BluetoothDevice?>(null)
    val targetDevice = _targetDevice.asStateFlow()

    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode = _isDarkMode.asStateFlow()

    fun toggleDarkMode() {
        _isDarkMode.value = !_isDarkMode.value
    }

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
        android.util.Log.d("FreeBudsViewModel", "Finding devices...")
        try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = bluetoothManager.adapter ?: return
            
            // Look for paired devices first
            val pairedDevices = adapter.bondedDevices
            android.util.Log.d("FreeBudsViewModel", "Found ${pairedDevices.size} paired devices")
            val buds = pairedDevices.find { it.name?.contains("FreeBuds", ignoreCase = true) == true }
            
            if (buds != null) {
                android.util.Log.d("FreeBudsViewModel", "Found target device: ${buds.name}")
                _targetDevice.value = buds
                connect(buds)
            } else {
                android.util.Log.d("FreeBudsViewModel", "Target device not found in paired devices")
                isConnecting = false
            }
        } catch (e: SecurityException) {
            android.util.Log.e("FreeBudsViewModel", "Permission error finding devices", e)
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
                android.util.Log.d("FreeBudsViewModel", "Connection timed out or failed: ${e.message}")
            } finally {
                isConnecting = false
            }
        }
        
        newManager.connect()
    }
    
    fun setAncMode(mode: AncMode) {
        _manager.value?.setAncMode(mode)
    }

    override fun onCleared() {
        _manager.value?.release()
        super.onCleared()
    }
}
