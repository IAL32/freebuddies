package com.freebuddies.app

import android.content.Context
import androidx.lifecycle.ViewModel
import com.freebuddies.app.protocol.AncMode
import com.freebuddies.app.protocol.NcIntensity

class FreeBudsViewModel : ViewModel() {
    val isConnected = FreeBudsConnectionManager.isConnected
    val batteryStatus = FreeBudsConnectionManager.batteryStatus
    val soundControl = FreeBudsConnectionManager.soundControl
    val deviceInfo = FreeBudsConnectionManager.deviceInfo
    val inEarState = FreeBudsConnectionManager.inEarState
    val ringingStatus = FreeBudsConnectionManager.ringingStatus
    val targetDevice = FreeBudsConnectionManager.targetDevice
    val preferredNcIntensity = FreeBudsConnectionManager.preferredNcIntensity

    fun setPreferredNcIntensity(intensity: NcIntensity) = FreeBudsConnectionManager.setPreferredNcIntensity(intensity)

    fun startAutoReconnect(context: Context) = FreeBudsConnectionManager.startAutoReconnect(context)
    fun findDevice(context: Context) = FreeBudsConnectionManager.findDevice(context)
    fun onResume(context: Context) = FreeBudsConnectionManager.onResume(context)

    fun setAncMode(mode: AncMode, intensity: NcIntensity = NcIntensity.GENERAL, voiceMode: Boolean = false) {
        FreeBudsConnectionManager.setAncMode(mode, intensity, voiceMode)
    }

    fun setRinging(side: Int, active: Boolean) {
        FreeBudsConnectionManager.setRinging(side, active)
    }
}
