package com.freebuddies.app.protocol

/**
 * Battery status decoder for (0x01, 0x08) and (0x01, 0x27).
 */
data class BatteryStatus(
    val leftPercent: Int,
    val rightPercent: Int,
    val casePercent: Int,
    val leftCharging: Boolean,
    val rightCharging: Boolean,
    val caseCharging: Boolean,
    val leftInEar: Boolean,
    val rightInEar: Boolean,
) {
    companion object {
        fun fromTlvs(tlvs: List<Tlv>): BatteryStatus? {
            val levels = tlvs.find { it.type == 0x02 }?.value?.takeIf { it.size == 3 } ?: return null
            val charging = tlvs.find { it.type == 0x03 }?.value?.takeIf { it.size == 3 } ?: return null
            val wear = tlvs.find { it.type == 0x05 }?.value?.takeIf { it.size == 2 }
            
            return BatteryStatus(
                leftPercent = levels[0].toInt() and 0xFF,
                rightPercent = levels[1].toInt() and 0xFF,
                casePercent = levels[2].toInt() and 0xFF,
                leftCharging = charging[0].toInt() == 1,
                rightCharging = charging[1].toInt() == 1,
                caseCharging = charging[2].toInt() == 1,
                leftInEar = (wear?.get(0)?.toInt() ?: 0) == 1,
                rightInEar = (wear?.get(1)?.toInt() ?: 0) == 1,
            )
        }
    }
}

/**
 * Device information decoder for (0x2B, 0x0A).
 */
data class DeviceInfo(
    val serialNumber: String,
    val modelCode: String,
    val hardwareRevision: String,
    val firmwareVersion: String,
) {
    companion object {
        fun fromTlvs(tlvs: List<Tlv>): DeviceInfo? {
            val sn = tlvs.find { it.type == 0x01 }?.value?.toString(Charsets.US_ASCII) ?: ""
            val model = tlvs.find { it.type == 0x02 }?.value?.toString(Charsets.US_ASCII) ?: ""
            val hw = tlvs.find { it.type == 0x03 }?.value?.toString(Charsets.US_ASCII) ?: ""
            val fw = tlvs.find { it.type == 0x06 }?.value?.toString(Charsets.US_ASCII) ?: ""
            
            if (sn.isEmpty() && model.isEmpty()) return null
            return DeviceInfo(sn, model, hw, fw)
        }
    }
}

enum class WearState(val label: String) {
    IN_EAR("in"), OUT("out"), IN_CASE("case");
}

/**
 * In-ear state decoder for (0x2B, 0x25).
 *
 * Tag layout (observed on FreeBuds Pro 4):
 *   T1 = left in-ear,   T2 = right in-ear,
 *   T3 = left in-case,  T4 = right in-case
 */
data class InEarState(
    val left: WearState,
    val right: WearState,
) {
    val leftInEar: Boolean get() = left == WearState.IN_EAR
    val rightInEar: Boolean get() = right == WearState.IN_EAR

    companion object {
        fun fromTlvs(tlvs: List<Tlv>): InEarState? {
            val t1 = tlvs.find { it.type == 0x01 }?.value?.getOrNull(0)?.toInt() ?: 0
            val t2 = tlvs.find { it.type == 0x02 }?.value?.getOrNull(0)?.toInt() ?: 0
            val t3 = tlvs.find { it.type == 0x03 }?.value?.getOrNull(0)?.toInt() ?: 0
            val t4 = tlvs.find { it.type == 0x04 }?.value?.getOrNull(0)?.toInt() ?: 0

            if (tlvs.none { it.type in 1..4 }) return null

            return InEarState(
                left = when {
                    t1 == 1 -> WearState.IN_EAR
                    t3 == 1 -> WearState.IN_CASE
                    else -> WearState.OUT
                },
                right = when {
                    t2 == 1 -> WearState.IN_EAR
                    t4 == 1 -> WearState.IN_CASE
                    else -> WearState.OUT
                }
            )
        }
    }
}

/**
 * Find my buds state decoder for (0x2B, 0x5E).
 */
data class RingingStatus(
    val left: Boolean,
    val right: Boolean
) {
    companion object {
        fun fromTlv(tlv: Tlv, current: RingingStatus): RingingStatus {
            val data = tlv.value
            if (data.size < 2) return current
            val side = data[0].toInt() // 0 = Left, 1 = Right
            val action = data[1].toInt() // 0 = Ringing, 1 = Stopped
            val isActive = action == 0
            
            return if (side == 0) {
                current.copy(left = isActive)
            } else {
                current.copy(right = isActive)
            }
        }
    }
}

/**
 * Sound control (ANC / Awareness) for (0x2B, 0x04) write and (0x2B, 0x2A) read.
 *
 * Read encoding (0x2A):  Tag 01 = [intensity, mode]
 * Write encoding (0x04): Tag 01 = [mode, intensity]
 */
enum class AncMode(val code: Int) {
    OFF(0), NOISE_CANCELLING(1), AWARENESS(2), UNKNOWN(-1);

    companion object {
        fun fromCode(code: Int): AncMode = entries.find { it.code == code } ?: UNKNOWN
    }
}

enum class NcIntensity(val code: Int, val label: String) {
    GENERAL(0, "general"),
    COZY(1, "cozy"),
    ULTRA(2, "ultra"),
    DYNAMIC(3, "dynamic");

    companion object {
        fun fromCode(code: Int): NcIntensity = entries.find { it.code == code } ?: GENERAL
    }
}

data class SoundControl(
    val mode: AncMode,
    val ncIntensity: NcIntensity = NcIntensity.GENERAL,
    val voiceMode: Boolean = false,
) {
    companion object {
        fun fromTlvs(tlvs: List<Tlv>): SoundControl? {
            // Read encoding: Tag 01 = [intensity, mode]
            val data = tlvs.find { it.type == 0x01 }?.value?.takeIf { it.size >= 2 }
                ?: return null
            val intensity = data[0].toInt() and 0xFF
            val modeCode = data[1].toInt() and 0xFF
            val mode = AncMode.fromCode(modeCode)
            // For awareness: intensity 1 = voice mode, 2 = normal
            val voiceMode = (mode == AncMode.AWARENESS && intensity == 1)
            return SoundControl(mode, NcIntensity.fromCode(intensity), voiceMode)
        }

        fun toTlv(mode: AncMode, intensity: NcIntensity = NcIntensity.GENERAL, voiceMode: Boolean = false): Tlv {
            // Write encoding: Tag 01 = [mode, intensity]
            val (modeCode, intensityCode) = when (mode) {
                AncMode.OFF -> 0x00 to 0x00
                AncMode.NOISE_CANCELLING -> 0x01 to intensity.code
                AncMode.AWARENESS -> 0x02 to (if (voiceMode) 0x01 else 0x02)
                AncMode.UNKNOWN -> error("Cannot encode UNKNOWN")
            }
            return Tlv(0x01, byteArrayOf(modeCode.toByte(), intensityCode.toByte()))
        }
    }
}