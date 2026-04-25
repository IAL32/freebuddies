package com.freebuddies.app

import android.content.Context
import androidx.lifecycle.ViewModel
import com.freebuddies.app.protocol.*

class FreeBudsViewModel : ViewModel() {
    val isConnected = FreeBudsConnectionManager.isConnected
    val batteryStatus = FreeBudsConnectionManager.batteryStatus
    val soundControl = FreeBudsConnectionManager.soundControl
    val deviceInfo = FreeBudsConnectionManager.deviceInfo
    val inEarState = FreeBudsConnectionManager.inEarState
    val ringingStatus = FreeBudsConnectionManager.ringingStatus
    val targetDevice = FreeBudsConnectionManager.targetDevice
    val preferredNcIntensity = FreeBudsConnectionManager.preferredNcIntensity
    val earTipType = FreeBudsConnectionManager.earTipType
    val lowLatency = FreeBudsConnectionManager.lowLatency
    val wearDetection = FreeBudsConnectionManager.wearDetection
    val caseTone = FreeBudsConnectionManager.caseTone
    val headControl = FreeBudsConnectionManager.headControl
    val nodAction = FreeBudsConnectionManager.nodAction
    val shakeAction = FreeBudsConnectionManager.shakeAction
    val pairedDevices = FreeBudsConnectionManager.pairedDevices
    val eqPreset = FreeBudsConnectionManager.eqPreset
    val eqPresetCode = FreeBudsConnectionManager.eqPresetCode
    val customEqProfiles = FreeBudsConnectionManager.customEqProfiles

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

    fun setEarTipType(type: EarTipType) = FreeBudsConnectionManager.setEarTipType(type)
    fun setLowLatency(enabled: Boolean) = FreeBudsConnectionManager.setLowLatency(enabled)
    fun setWearDetection(enabled: Boolean) = FreeBudsConnectionManager.setWearDetection(enabled)
    fun setCaseTone(enabled: Boolean) = FreeBudsConnectionManager.setCaseTone(enabled)
    fun setHeadControl(enabled: Boolean) = FreeBudsConnectionManager.setHeadControl(enabled)
    fun setNodAction(action: HeadGestureAction) = FreeBudsConnectionManager.setNodAction(action)
    fun setShakeAction(action: HeadGestureAction) = FreeBudsConnectionManager.setShakeAction(action)

    fun setEqPreset(preset: EqPreset) {
        FreeBudsConnectionManager.setEqPreset(preset)
    }

    fun applyCustomEqProfile(profile: CustomEqProfile) {
        FreeBudsConnectionManager.applyCustomEqProfile(profile)
    }

    fun saveCustomEqProfile(profile: CustomEqProfile) {
        FreeBudsConnectionManager.saveCustomEqProfile(profile)
    }

    fun deleteCustomEqProfile(profile: CustomEqProfile) {
        FreeBudsConnectionManager.deleteCustomEqProfile(profile)
    }
}
