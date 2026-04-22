package com.freebuddies.app.protocol

import java.io.ByteArrayOutputStream

/**
 * Standard CRC16-XModem implementation as specified in the README.
 */
object Crc16Xmodem {
    fun compute(data: ByteArray, offset: Int = 0, length: Int = data.size): Int {
        var crc = 0x0000
        for (i in offset until offset + length) {
            crc = crc xor ((data[i].toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if ((crc and 0x8000) != 0) {
                    ((crc shl 1) xor 0x1021) and 0xFFFF
                } else {
                    (crc shl 1) and 0xFFFF
                }
            }
        }
        return crc and 0xFFFF
    }
}

/**
 * Type-Length-Value record for the Huawei MDN protocol.
 */
data class Tlv(val type: Int, val value: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Tlv) return false
        if (type != other.type) return false
        return value.contentEquals(other.value)
    }

    override fun hashCode(): Int {
        var result = type
        result = 31 * result + value.contentHashCode()
        return result
    }
}

object TlvParser {
    fun parseTlvs(payload: ByteArray): List<Tlv> {
        val out = mutableListOf<Tlv>()
        var i = 0
        while (i + 2 <= payload.size) {
            val type = payload[i].toInt() and 0xFF
            val len = payload[i + 1].toInt() and 0xFF
            if (i + 2 + len > payload.size) break // truncated; drop
            out += Tlv(type, payload.copyOfRange(i + 2, i + 2 + len))
            i += 2 + len
        }
        return out
    }

    fun encodeTlvs(tlvs: List<Tlv>): ByteArray {
        val buf = ByteArrayOutputStream()
        for (tlv in tlvs) {
            buf.write(tlv.type)
            buf.write(tlv.value.size)
            buf.write(tlv.value)
        }
        return buf.toByteArray()
    }
}

/**
 * Represents a full Huawei MDN Frame.
 */
data class Frame(val service: Int, val command: Int, val tlvs: List<Tlv>) {
    companion object {
        private const val MAGIC = 0x5A.toByte()

        fun parse(data: ByteArray, offset: Int, size: Int): Frame? {
            // Min size: magic(1) + len(2) + const0(1) + svc(1) + cmd(1) + crc(2) = 8 bytes
            // The README says 9, but let's be careful with empty TLVs. 
            // Frame: 5A | LL LL | 00 | SS | CC | [TLVs] | CR CR
            if (size < 8) return null
            if (data[offset] != MAGIC) return null

            // Verify CRC
            val expected = ((data[offset + size - 2].toInt() and 0xFF) shl 8) or
                    (data[offset + size - 1].toInt() and 0xFF)
            val computed = Crc16Xmodem.compute(data, offset, size - 2)
            if (expected != computed) return null

            val svc = data[offset + 4].toInt() and 0xFF
            val cmd = data[offset + 5].toInt() and 0xFF
            val tlvBytes = data.copyOfRange(offset + 6, offset + size - 2)
            return Frame(svc, cmd, TlvParser.parseTlvs(tlvBytes))
        }

        fun build(service: Int, command: Int, tlvs: List<Tlv> = emptyList()): ByteArray {
            val tlvBytes = TlvParser.encodeTlvs(tlvs)
            val length = 3 + tlvBytes.size // const(1) + svc(1) + cmd(1) + tlvBytes
            val body = ByteArray(6 + tlvBytes.size).apply {
                this[0] = MAGIC
                this[1] = ((length ushr 8) and 0xFF).toByte()
                this[2] = (length and 0xFF).toByte()
                this[3] = 0x00
                this[4] = service.toByte()
                this[5] = command.toByte()
                tlvBytes.copyInto(this, 6)
            }
            val crc = Crc16Xmodem.compute(body)
            return body + byteArrayOf(((crc ushr 8) and 0xFF).toByte(), (crc and 0xFF).toByte())
        }
    }
}

/**
 * Stateful byte-stream parser to extract frames from SPP traffic.
 */
class FrameReader(private val onFrame: (Frame) -> Unit) {
    private val buf = ByteArrayOutputStream()

    fun feed(data: ByteArray, offset: Int = 0, length: Int = data.size) {
        buf.write(data, offset, length)
        var bytes = buf.toByteArray()
        var i = 0
        
        while (i < bytes.size) {
            // Need at least magic + length (3 bytes)
            if (bytes.size - i < 3) break
            
            if (bytes[i] != 0x5A.toByte()) {
                // Resync: scan for next magic
                i++
                continue
            }
            
            val payloadLen = ((bytes[i + 1].toInt() and 0xFF) shl 8) or (bytes[i + 2].toInt() and 0xFF)
            val totalFrameSize = 1 + 2 + payloadLen + 2 // magic + len field + payload + CRC
            
            if (bytes.size - i < totalFrameSize) break // incomplete, wait for more
            
            val frame = Frame.parse(bytes, i, totalFrameSize)
            if (frame != null) {
                onFrame(frame)
            }
            i += totalFrameSize
        }
        
        // Re-buffer remaining tail
        buf.reset()
        if (i < bytes.size) {
            buf.write(bytes, i, bytes.size - i)
        }
    }
}
