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
    val voiceLanguage = FreeBudsConnectionManager.voiceLanguage
    val voiceLanguages = FreeBudsConnectionManager.voiceLanguages
    val deviceEqPresets = FreeBudsConnectionManager.deviceEqPresets
    val ancVoiceAnnounce = FreeBudsConnectionManager.ancVoiceAnnounce
    val doubleTap = FreeBudsConnectionManager.doubleTap
    val tripleTap = FreeBudsConnectionManager.tripleTap
    val longTap = FreeBudsConnectionManager.longTap
    val swipe = FreeBudsConnectionManager.swipe

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

    fun refreshGestureConfig() = FreeBudsConnectionManager.refreshGestureConfig()
    fun setDoubleTap(side: Int, action: TapAction) = FreeBudsConnectionManager.setDoubleTap(side, action)
    fun setTripleTap(side: Int, action: TapAction) = FreeBudsConnectionManager.setTripleTap(side, action)
    fun setLongTap(side: Int, action: LongTapAction) = FreeBudsConnectionManager.setLongTap(side, action)
    fun setSwipe(enabled: Boolean) = FreeBudsConnectionManager.setSwipe(enabled)
    fun setVoiceLanguage(language: String) = FreeBudsConnectionManager.setVoiceLanguage(language)
    fun setAncVoiceAnnounce(enabled: Boolean) = FreeBudsConnectionManager.setAncVoiceAnnounce(enabled)
    fun deleteDeviceEqPreset(preset: DeviceEqPreset) = FreeBudsConnectionManager.deleteDeviceEqPreset(preset)
    fun saveDeviceEqPreset(slotId: Int, name: String, bands: List<Int>) = FreeBudsConnectionManager.saveDeviceEqPreset(slotId, name, bands)
}
