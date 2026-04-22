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

/**
 * In-ear state decoder for (0x2B, 0x25).
 */
data class InEarState(
    val leftInEar: Boolean,
    val rightInEar: Boolean,
) {
    companion object {
        fun fromTlvs(tlvs: List<Tlv>): InEarState? {
            // Observed on FreeBuds Pro 4:
            // Left can be Tag 01 or Tag 03
            // Right can be Tag 02 or Tag 04
            val t1 = tlvs.find { it.type == 0x01 }?.value?.getOrNull(0)?.toInt() ?: 0
            val t2 = tlvs.find { it.type == 0x02 }?.value?.getOrNull(0)?.toInt() ?: 0
            val t3 = tlvs.find { it.type == 0x03 }?.value?.getOrNull(0)?.toInt() ?: 0
            val t4 = tlvs.find { it.type == 0x04 }?.value?.getOrNull(0)?.toInt() ?: 0

            // If no relevant tags found, return null to avoid overriding with false
            if (tlvs.none { it.type in 1..4 }) return null

            return InEarState(
                leftInEar = (t1 == 1 || t3 == 1),
                rightInEar = (t2 == 1 || t4 == 1)
            )
        }
    }
}

/**
 * Sound control (ANC / Awareness) for (0x2B, 0x5D) and (0x2B, 0x5E).
 */
enum class AncMode(val rawBytes: Pair<Int, Int>?) {
    OFF(0x00 to 0x00),
    // Labels are provisional until empirically confirmed. Cycle ANC in AI Life
    // and check which value arrives — then rename.
    AWARENESS(0x02 to 0x02),
    NOISE_CANCELLING(0x03 to 0x01),
    UNKNOWN(null);

    companion object {
        fun fromBytes(b0: Int, b1: Int): AncMode =
            entries.firstOrNull { it.rawBytes == (b0 to b1) } ?: UNKNOWN
    }
}

data class SoundControl(val mode: AncMode) {
    companion object {
        fun fromTlvs(tlvs: List<Tlv>): SoundControl? {
            // Log TLVs for debugging
            android.util.Log.d("SoundControl", "Parsing TLVs: ${tlvs.joinToString { "T${it.type}=${it.value.joinToString("") { b -> "%02X".format(b) }}" }}")

            // Pro 4 uses 2b:2a with Tag 01 containing two bytes [mode_group, intensity]
            // and 2b:ac with Tag 01 (enabled) and Tag 02 (mode)
            
            // First check for 2-byte tag 01 (Broadcast style)
            val data = tlvs.find { it.type == 0x01 }?.value?.takeIf { it.size >= 2 }
            if (data != null) {
                val b0 = data[0].toInt() and 0xFF
                val b1 = data[1].toInt() and 0xFF
                return SoundControl(AncMode.fromBytes(b0, b1))
            }

            // Then check for discrete tags (AC style / Command style)
            val t1 = tlvs.find { it.type == 0x01 }?.value?.firstOrNull()?.toInt()
            val t2 = tlvs.find { it.type == 0x02 }?.value?.firstOrNull()?.toInt()
            
            if (t1 != null && t2 != null) {
                // Heuristic: If enabled=1, map mode based on T2
                val b0 = if (t1 != 0) (if (t2 == 1) 0x02 else 0x03) else 0x00
                val b1 = if (t1 != 0) (if (t2 == 1) 0x02 else 0x01) else 0x00
                return SoundControl(AncMode.fromBytes(b0, b1))
            }

            return null
        }
        fun toTlv(mode: AncMode): Tlv {
            val bytes = mode.rawBytes
                ?: error("Cannot encode ${mode.name} — no known byte pair")
            return Tlv(0x01, byteArrayOf(bytes.first.toByte(), bytes.second.toByte()))
        }
    }
}