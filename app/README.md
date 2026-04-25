# FreeBuddies — SPP Protocol Reference

Reverse-engineered protocol documentation for the Huawei FreeBuds 4 Pro (model `T0022` / `T0022C`). Derived from Wireshark captures of AI Life ↔ buds traffic, cross-referenced with MelianMiko's FreeBuds 4i research and Gadgetbridge issue #4241.

Every byte layout, TLV tag, and state transition described here was observed on the wire.

---

## Table of contents

1. [Transport](#1-transport)
2. [Frame format](#2-frame-format)
3. [CRC16-XModem](#3-crc16-xmodem)
4. [TLV encoding](#4-tlv-encoding)
5. [Command reference](#5-command-reference)
6. [Android implementation guide](#6-android-implementation-guide)
7. [Minimum viable feature set](#7-minimum-viable-feature-set)
8. [Known unknowns](#8-known-unknowns)
9. [References](#9-references)

---

## 1. Transport

The buds expose a standard Bluetooth **RFCOMM Serial Port Profile** channel.

- **SPP service UUID:** `00001101-0000-1000-8000-00805F9B34FB`
- **RFCOMM channel:** discovered via SDP (channel 1 in the capture, but should not be hard-coded — always resolve via SDP)
- **Direction:** full duplex, byte stream
- **Framing:** Huawei MDN protocol (see §2)

The buds must already be paired and connected (A2DP/HFP established) before the SPP channel becomes reachable. On Android, connections are made using `BluetoothDevice.createRfcommSocketToServiceRecord(UUID)`.

Battery information is **not** available via the HFP vendor AT channel. The buds reply `ERROR` to `AT+HUAWEIBATTERY=?`, `AT+XHUAWEISF=?`, and `AT+TBSF=?`. All diagnostics flow over SPP.

---

## 2. Frame format

Every frame — in both directions — has this layout:

```
┌──────┬────────────┬──────┬─────────┬─────────┬──────────────┬──────────┐
│ 0x5A │ len (BE16) │ 0x00 │ svc_id  │ cmd_id  │ TLV payload  │ CRC16-XM │
│  1 B │    2 B     │  1 B │   1 B   │   1 B   │   variable   │   2 B    │
└──────┴────────────┴──────┴─────────┴─────────┴──────────────┴──────────┘
```

| Field | Size | Notes |
|-------|------|-------|
| Magic | 1 | Always `0x5A` (ASCII `'Z'`). First byte of every frame. |
| Length | 2 | Big-endian. Counts bytes from the constant `0x00` through the end of the TLV payload. Equals `tlv_bytes_len + 3`. Does **not** include magic, length itself, or CRC. |
| Constant | 1 | Always `0x00`. |
| Service ID | 1 | Command group. Observed: `0x01` (system / battery), `0x2B` (device config / ANC). |
| Command ID | 1 | Specific command within the service. |
| TLV payload | variable | Zero or more TLV records (see §4). |
| CRC16 | 2 | CRC16-XModem over everything from magic through end of TLV payload. Big-endian on the wire. |

### Byte-order rules

- The 2-byte **length** field is **big-endian**.
- The 2-byte **CRC** is **big-endian** on the wire.
- Multi-byte values inside TLVs (rare) are big-endian unless noted otherwise.

### Worked example — request battery

```
5A 00 06 00 01 08 00 00 XX XX
│  └───┬──┘ │  └─┬─┘ └─┬─┘ └─┬─┘
│      │    │    │     │     └─ CRC16-XM (computed over everything before)
│      │    │    │     └─────── (no TLVs — single empty TLV would be 00 00,
│      │    │    │               here literally two zero bytes as seen in the wild)
│      │    │    └───────────── cmd_id = 0x08 (GET_BATTERY)
│      │    │                   svc_id = 0x01 (SYSTEM)
│      │    └────────────────── constant 0x00
│      └─────────────────────── length = 6 (covers 00 | 01 08 | 00 00)
└────────────────────────────── magic 0x5A
```

### Worked example — set ANC to "Awareness"

```
5A 00 07 00 2B 04 01 02 02 00 CRC_HI CRC_LO
             └─┬─┘ └─┬─┘ └─┬─┘
               │     │     └──────── TLV value: mode=2 (awareness), intensity=0
               │     └────────────── TLV tag 01, length 02
               └──────────────────── svc 2B, cmd 04 (SET_ANC_MODE)
```

Length = 7: covers `00 | 2B 04 | 01 02 01 01` = const(1) + svc(1) + cmd(1) + TLV(4). **Canonical formula:** `length = 3 + len(TLV bytes)`.

### Worked example — ring left earbud

```
5A 00 07 00 2B 5D 01 02 00 00 CRC_HI CRC_LO
             └─┬─┘ └─┬─┘ └─┬─┘
               │     │     └──────── TLV value: side=0 (left), action=0 (ring)
               │     └────────────── TLV tag 01, length 02
               └──────────────────── svc 2B, cmd 5D (FIND_MY_BUDS)
```

---

## 3. CRC16-XModem

Standard CRC16-XModem:

- **Polynomial:** `0x1021`
- **Initial value:** `0x0000`
- **Reflect input:** no
- **Reflect output:** no
- **XOR out:** `0x0000`

**Input:** all bytes from the `0x5A` magic through the last TLV byte (i.e. everything except the CRC itself).

**Output byte order on the wire:** big-endian (high byte first).

### Kotlin reference implementation

```kotlin
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
```

Verification against a known-good frame from the capture:

```
5A 00 05 00 2B 2A 01 00  →  CRC must be 0x427E
```

---

## 4. TLV encoding

The payload section consists of zero or more Type-Length-Value records, concatenated without any separator or count:

```
┌──────┬──────┬─────────────┐
│ type │ len  │ value bytes │
│ 1 B  │ 1 B  │  len bytes  │
└──────┴──────┴─────────────┘
```

- **Type** (tag): 1 byte, identifies the field. Tag values are scoped to the specific `(svc, cmd)` they appear in — tag 01 in a battery response is not the same field as tag 01 in a device-info response.
- **Length:** 1 byte, unsigned. Max 255. A length of 0 is legal (empty value, used by the phone as a "ping" payload for get requests).
- **Value:** `len` raw bytes. Interpretation depends on the tag.

### Reading TLVs

```kotlin
data class Tlv(val type: Int, val value: ByteArray)

fun parseTlvs(payload: ByteArray): List<Tlv> {
    val out = mutableListOf<Tlv>()
    var i = 0
    while (i + 2 <= payload.size) {
        val type = payload[i].toInt() and 0xFF
        val len = payload[i + 1].toInt() and 0xFF
        if (i + 2 + len > payload.size) break  // truncated; drop
        out += Tlv(type, payload.copyOfRange(i + 2, i + 2 + len))
        i += 2 + len
    }
    return out
}
```

### Writing TLVs

```kotlin
fun encodeTlvs(tlvs: List<Tlv>): ByteArray {
    val buf = java.io.ByteArrayOutputStream()
    for (tlv in tlvs) {
        buf.write(tlv.type)
        buf.write(tlv.value.size)
        buf.write(tlv.value)
    }
    return buf.toByteArray()
}
```

---

## 5. Command reference

Commands are identified by the `(svc_id, cmd_id)` pair. Observed service IDs:

- `0x01` — SYSTEM (device info, battery, language, gesture actions)
- `0x2B` — DEVICE (ANC, in-ear state, per-device config)

### 5.1 Device info

Two commands provide device information with different detail levels.

#### Response — `(0x2B, 0x0A)` (basic)

Broadcast shortly after RFCOMM connect, or request with empty TLVs.

| Tag | Type | Description | Example |
|-----|------|-------------|---------|
| 01 | ASCII string | Serial number | `3RRXC25408034501` |
| 02 | ASCII string | Model code | `T0022/T0022C` |
| 03 | ASCII string | Hardware revision | `082` |
| 04 | ASCII string | Region / HW variant | `001` |
| 05 | ASCII string | Color / SKU code | `ZAAM` |
| 06 | ASCII string | Firmware version (short) | `1.0.0` |
| 07 | ASCII string | Bluetooth chip version | `HL1SAKM2_Ver.A` |

#### Response — `(0x01, 0x07)` (extended)

Returns richer system information. Request with empty TLVs.

| Tag | Type | Description | Example |
|-----|------|-------------|---------|
| 02 | 2 bytes | Protocol version | `01 57` |
| 03 | ASCII string | Bluetooth chip version | `HL1SAKM2_Ver.A` |
| 07 | UTF-8 string | **Full firmware string** (with build code) | `HarmonyOS 6.0.0.272(F003H003C90)` |
| 09 | ASCII string | Serial number | `3RRXC25408034501` |
| 0A | ASCII string | Bluetooth firmware ID | `BTFT0022-000157` |
| 0F | ASCII string | Bluetooth model code | `BTFT0022` |
| 18 | ASCII string | **Individual bud serial numbers** | `L-XC5908253U006995,R-XC5911253R001361` |

Model code `T0022/T0022C` is the canonical identifier for FreeBuds 4 Pro. It can be used to reject frames from other Huawei audio devices if the app targets FreeBuds 4 Pro specifically.

### 5.2 Battery status

The most important command for a companion app.

#### Request — `(0x01, 0x08)`

**Payload:** empty.

In practice, polling is rarely needed. The buds push updates autonomously via `(0x01, 0x27)` whenever battery, charging, or wear state changes.

#### Response — `(0x01, 0x08)` (reply) / `(0x01, 0x27)` (push notification)

Both messages share the exact same TLV layout. The only difference is that `0x27` is unsolicited.

| Tag | Type | Description |
|-----|------|-------------|
| 01 | uint8 | Aggregate/lowest battery level (legacy field). `0–100`. |
| 02 | 3 × uint8 | **Per-component battery:** `[left%, right%, case%]`. Each byte 0–100. |
| 03 | 3 × uint8 | **Charging state:** `[left, right, case]`. Each byte: `0` = not charging, `1` = charging. |
| 04 | 2 × uint8 | Unknown, constant in capture. Observed `0x0A 0x14` (10, 20). Likely low-battery warning thresholds. Safe to ignore. |
| 05 | 2 × uint8 | **In-ear state:** `[left, right]`. `0` = out, `1` = in. (Duplicated in §5.3 with more granularity.) |
| 06 | uint8 | Unknown, always `0x0A` in capture. Safe to ignore. |

#### Kotlin decoder

```kotlin
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
```

### 5.3 In-ear / wear state

#### Notification — `(0x2B, 0x25)`

Pushed by the buds whenever a bud is inserted, removed, or placed in the case. No request needed.

| Tag | Type | Description |
|-----|------|-------------|
| 01 | uint8 | Left bud **in-ear**. `0` = not worn, `1` = worn. |
| 02 | uint8 | Right bud **in-ear**. `0` = not worn, `1` = worn. |
| 03 | uint8 | Left bud **in-case**. `0` = not in case, `1` = in case. |
| 04 | uint8 | Right bud **in-case**. `0` = not in case, `1` = in case. |

Each bud has three possible states derived from these two flags:

| State | in-ear tag | in-case tag |
|-------|-----------|-------------|
| In ear (worn) | `1` | `0` |
| Out (not worn, not in case) | `0` | `0` |
| In case | `0` | `1` |

### 5.4 Sound control (ANC / Awareness)

FreeBuds 4 Pro uses a different command pair than the 4i for *reading* ANC state. However, writing ANC mode via `(0x2B, 0x04)` — the same command used on the 4i — works on FreeBuds 4 Pro. An earlier assumption that `0x04` was only an echo/ack was incorrect; it accepts writes with TLV Tag 01.

**Note:** `(0x2B, 0x5D)` is used for **Find My Buds** ringing (see §5.6), not ANC writes. ANC writes go to `0x04`.

#### Write — `(0x2B, 0x04)`

| Tag | Type | Description |
|-----|------|-------------|
| 01 | 2 × uint8 | `[mode, intensity]`. `mode`: 0 = off, 1 = noise cancellation, 2 = awareness. `intensity`: NC sub-mode (0–3), ignored for off/awareness. |

Mode values:

| mode | intensity | Meaning |
|------|-----------|---------|
| 0 | 0 | Off (normal passthrough) |
| 1 | 0 | NC — General |
| 1 | 1 | NC — Cozy |
| 1 | 2 | NC — Ultra |
| 1 | 3 | NC — Dynamic |
| 2 | 1 | Awareness — Voice mode |
| 2 | 2 | Awareness — Normal |

#### Notification / echo — `(0x2B, 0x5E)`

Pushed on ringing state changes (see §5.6). May also appear near ANC changes. The authoritative source for current ANC mode is `(0x2B, 0x2A)`, not this notification.

| Tag | Type | Description |
|-----|------|-------------|
| 02 | 2 × uint8 | Ringing status `[side, action]` — see §5.6. |

#### Read — `(0x2B, 0x2A)`

Returns the current mode. The response carries tag `01` with 2 bytes: `[intensity, mode]` — **note the byte order is swapped compared to the write command**. The second byte is the ANC mode (`0` = off, `1` = NC, `2` = awareness). The first byte is the sub-mode: for NC it's the intensity (`0` = general, `1` = cozy, `2` = ultra, `3` = dynamic); for awareness it's the voice toggle (`1` = voice mode, `2` = normal). Observed combinations: `00 00` (off), `00 01` (NC general), `01 01` (NC cozy), `01 02` (awareness voice), `02 01` (NC ultra), `02 02` (awareness normal), `03 01` (NC dynamic).

### 5.5 Preferred ANC cycle list

Controls which ANC modes the long-press gesture cycles through.

#### Read — `(0x2B, 0x19)`

Response TLVs (inferred from FreeBuds 4i docs, observed raw on the wire):

| Tag | Type | Description |
|-----|------|-------------|
| 01 | int8 | Selected cycle option for left bud. See table below. |
| 02 | int8 | Selected cycle option for right bud. |
| 03 | 10 × uint8 | Ordered list of mode IDs to cycle through. Values 1–10. |

Cycle-option values (from 4i reference, may extend on FreeBuds 4 Pro):

| Value | Meaning |
|-------|---------|
| 1 | Off + noise cancellation only |
| 2 | All modes |
| 3 | Noise cancellation + awareness |
| 4 | Off + awareness |

#### Write — `(0x2B, 0x18)`

Same TLV layout as the read. Note: writing tag 01 or tag 02 may copy to the other bud — the FreeBuds 4i docs flag this behavior and it likely applies here as well. Independent per-bud cycles should be verified before relying on them.

### 5.6 Find My Buds (ringing)

Triggers an audible alert on an individual earbud to help locate it. The write command uses the `(0x2B, 0x5D)` service/command pair with a TLV encoding of side + action. The notification channel `(0x2B, 0x5E)` is shared with sound control — ringing status arrives in **Tag 02**, while sound-control status arrives in **Tag 01**.

#### Write — `(0x2B, 0x5D)`

| Tag | Type | Description |
|-----|------|-------------|
| 01 | 2 × uint8 | `[side, action]`. `side`: `0` = left, `1` = right. `action`: `0` = start ringing, `1` = stop ringing. |

The four combinations:

| side | action | Meaning |
|------|--------|---------|
| 0 | 0 | Ring left earbud |
| 0 | 1 | Stop ringing left earbud |
| 1 | 0 | Ring right earbud |
| 1 | 1 | Stop ringing right earbud |

Each side is controlled independently — ringing one earbud does not affect the other. Ringing both requires two separate commands.

#### Notification — `(0x2B, 0x5E)` Tag 02

Pushed whenever ringing state changes on either bud, including as an echo of a write.

| Tag | Type | Description |
|-----|------|-------------|
| 02 | 2 × uint8 | `[side, action]`. Same encoding as the write: `side` 0=left / 1=right, `action` 0=ringing / 1=stopped. |

**Important:** The `(0x2B, 0x5E)` notification can carry **both** Tag 01 (sound control) and Tag 02 (ringing) in the same frame. Both tags should be parsed independently.

#### Kotlin decoder

```kotlin
data class RingingStatus(
    val left: Boolean,   // true = currently ringing
    val right: Boolean
) {
    companion object {
        fun fromTlv(tlv: Tlv, current: RingingStatus): RingingStatus {
            val data = tlv.value
            if (data.size < 2) return current
            val side = data[0].toInt()   // 0 = Left, 1 = Right
            val action = data[1].toInt() // 0 = Ringing, 1 = Stopped
            val isActive = action == 0
            return if (side == 0) current.copy(left = isActive)
                   else current.copy(right = isActive)
        }
    }
}
```

**Note:** The ringing status is updated incrementally — each notification reports one side at a time, so it must be merged with the current state rather than replaced.

### 5.7 Playback state (double-tap gesture)

#### Notification — `(0x2B, 0x36)`

Pushed when the user double-taps either earbud stem to play or pause media. Both buds produce identical output — the notification does not distinguish which bud was tapped.

| Tag | Type | Description |
|-----|------|-------------|
| 05 | 7 bytes | First 6 bytes are a constant device/session identifier (e.g. `77 1B 87 B9 75 A4`). Last byte is the playback state. |

Playback state (last byte of Tag 05):

| Value | Meaning |
|-------|---------|
| `0x01` | Stopped (idle, no active audio session) |
| `0x03` | Paused (session active but paused) |
| `0x09` | Playing (media resumed) |

### 5.8 Audio source info

#### Notification — `(0x2B, 0x31)`

Pushed when the buds connect to an audio source or when playback state changes. Carries richer information than `(0x2B, 0x36)`, including the connected device name.

| Tag | Type | Description |
|-----|------|-------------|
| 02 | uint8 | Unknown, observed `0x01`. |
| 03 | uint8 | Unknown, observed `0x00`. |
| 04 | 6 bytes | Device identifier (e.g. `77 1B 87 B9 75 A4`). Same ID as in `(0x2B, 0x36)` Tag 05. |
| 05 | uint8 | Playback state: `0x01` = stopped, `0x03` = paused, `0x09` = playing. |
| 06 | uint8 | Unknown, observed `0x01`. |
| 07 | uint8 | Unknown, observed `0x00`. |
| 08 | uint8 | Unknown, observed `0x01`. |
| 09 | ASCII string | Connected device name (e.g. `"Adrian's S22"`). |
| 0A | uint8 | Unknown, observed `0x01`. |

### 5.9 Volume gesture

#### Notification — `(0x2B, 0x4B)`

Pushed when the user swipes up or down on either earbud stem to change volume. Both buds produce identical output — volume is a shared device setting, not per-bud.

| Tag | Type | Description |
|-----|------|-------------|
| 01 | uint8 | Always `0x00`. Likely an ack/status byte. |
| 02 | 3 × uint8 | `[direction, level_before, level_after]`. See below. |

Tag 02 encoding:

| Byte | Values | Description |
|------|--------|-------------|
| 0 | `0x00` / `0x01` | Direction: `0` = swipe up (volume increase), `1` = swipe down (volume decrease). |
| 1 | uint8 | Volume level before the gesture (system media volume step). |
| 2 | uint8 | Volume level after the gesture. |

Observed examples:

| Gesture | Tag 02 | Decoded |
|---------|--------|---------|
| Swipe up | `00 0A 0B` | Up, 10 → 11 |
| Swipe down | `01 0B 0A` | Down, 11 → 10 |

The volume step values correspond to Android system media volume levels. The range depends on the connected device's volume steps (typically 0–15).

### 5.10 EQ / Sound presets

Discovered via btsnoop binary analysis of AI Life traffic. Uses two command pairs: `(0x2B, 0x4A)` for reading capabilities and current preset, `(0x2B, 0x49)` for setting a preset.

#### Read — `(0x2B, 0x4A)`

**Request payload:** `TLV 02 = (empty)`.

#### Response — EQ capabilities

| Tag | Type | Description |
|-----|------|-------------|
| 01 | uint8 | Status / version. Always `0x01`. |
| 02 | uint8 | **Currently selected preset ID.** |
| 03 | N × uint8 | **Available preset IDs** (list). Observed: `02 03 05 09 0B 0C`. |
| 04 | uint8 | Default preset ID. Observed: `0x05`. |
| 08 | empty | Reserved. |

#### Write — `(0x2B, 0x49)`

For built-in presets, Tag 01 is a single byte (the preset ID). For custom EQ profiles, a multi-tag format is used.

**Built-in preset (single byte):**

| Tag | Type | Description |
|-----|------|-------------|
| 01 | uint8 | Preset ID. |

**Custom EQ profile (multi-tag):**

| Tag | Type | Description |
|-----|------|-------------|
| 01 | uint8 | Preset slot ID. `0x64` (100) = custom slot 1. |
| 02 | uint8 | Number of EQ bands. `0x0A` (10). |
| 03 | 10 × int8 | **EQ curve.** One signed byte per band, internal scale (approx ±50). Band order: 60 Hz, 125 Hz, 250 Hz, 500 Hz, 1 kHz, 2 kHz, 4 kHz, 8 kHz, 12 kHz, 16 kHz. |
| 04 | ASCII string | User-visible name (e.g. `"My sound effect 1"`). |
| 05 | uint8 | Active flag. `0x00` or `0x01`. |

#### Response (ack)

All writes return `Tag 7F = 000186A0` (4 bytes, value 100000). This is a universal write-accepted acknowledgment shared with other write commands.

#### Preset IDs

| ID | Preset |
|----|--------|
| `0x02` | Bass Boost |
| `0x03` | Treble Boost |
| `0x05` | Default |
| `0x09` | Voices |
| `0x0B` | Balanced |
| `0x0C` | Classical |
| `0xC8` | Symphony (factory preset with embedded EQ curve) |
| `0xC9` | Hi-Fi Live (factory preset with embedded EQ curve) |
| `0x64` | Custom slot 1 |

### 5.11 Wear detection toggle

Controls the proximity sensor used for in-ear detection. When disabled, the buds stop reading the sensor and default to "wearing" state, which causes ANC to engage.

#### Write — `(0x2B, 0x10)`

| Tag | Type | Description |
|-----|------|-------------|
| 01 | uint8 | `0x00` = wear detection OFF, `0x01` = wear detection ON. |

**Response:** `Tag 7F = 000186A0` (standard ack).

**Side effects:** Toggling wear detection OFF causes a `(0x2B, 0x25)` in-ear state change (the primary bud reports "in ear" even when in the case) and ANC engages at the last-used preset. Toggling ON restores accurate sensor readings.

### 5.12 Preferred device

Sets which paired device the buds prefer to connect to, or auto mode.

#### Write — `(0x2B, 0x32)`

| Tag | Type | Description |
|-----|------|-------------|
| 01 | 6 × uint8 | Bluetooth address of the preferred device, or `00 00 00 00 00 00` for auto mode. |

**Response:** `Tag 7F = 000186A0` (standard ack).

### 5.13 Ear tips

Configures which ear tip type is installed, affecting noise cancellation seal optimization.

#### Read / Write — `(0x2B, 0xB4)`

Uses a sub-command structure: Tag 01 identifies the setting category (`0x08` = ear tips), Tag 02 carries the value.

**Read request:**

| Tag | Type | Description |
|-----|------|-------------|
| 01 | uint8 | Sub-command. `0x08` for ear tips. |
| 02 | empty | Query (no value). |

**Response / Write:**

| Tag | Type | Description |
|-----|------|-------------|
| 01 | uint8 | Sub-command. Always `0x08`. |
| 02 | uint8 | Tip type: `0x01` = silicone, `0x02` = memory foam. |

The response echoes the current (or newly set) value — the same frame format is used for both reads and writes. To read, send Tag 02 empty; to write, send Tag 02 with the desired value.

### 5.14 Charging case tone & head gestures

Also uses `(0x2B, 0xB4)` with sub-command `0x0B`. This sub-command bundles the charging case tone setting with head gesture actions.

#### Read — `(0x2B, 0xB4)` T01=0x0B

**Request:** `TLV 01 = 0x0B`, `TLV 02 = (empty)`.

#### Response / Write

| Tag | Type | Description |
|-----|------|-------------|
| 01 | uint8 | Sub-command. Always `0x0B`. |
| 02 | uint8 | **Charging case tone.** `0x01` = on, `0x00` = off. |
| 03 | uint8 | **Nod head action.** `0x00` = none, `0x01` = answer call, `0x02` = reject call. |
| 04 | uint8 | **Shake head action.** Same values as nod. |

The response always echoes all four tags with the current state. To change a single setting, send T01 (sub-command) plus the tag you want to change — the buds respond with the full current state including the update.

### 5.15 Head control toggle

Enables or disables head gesture recognition.

#### Read — `(0x2B, 0x6C)`

**Request:** `TLV 02 = (empty)`. Response: `TLV 02 = [0/1]`.

#### Write — `(0x2B, 0x6C)`

| Tag | Type | Description |
|-----|------|-------------|
| 01 | uint8 | `0x00` = head control off, `0x01` = head control on. |

**Response:** `Tag 7F = 000186A0` (standard ack).

### 5.16 Low audio latency

Controls whether low-latency audio mode is active. Read and write use separate command IDs.

#### Read — `(0x2B, 0xA3)`

**Request:** empty. Response carries the current state.

| Tag | Type | Description |
|-----|------|-------------|
| 02 | uint8 | Low audio latency: `0x00` = off, `0x01` = on. |

#### Write — `(0x2B, 0xA2)`

| Tag | Type | Description |
|-----|------|-------------|
| 01 | uint8 | `0x00` = off, `0x01` = on. |

Writing triggers a push notification via `(0x2B, 0xA3)` with the updated T02 value.

### 5.17 Wear detection (read)

The wear detection state can be **read** via `(0x2B, 0x11)` and **written** via `(0x2B, 0x10)` (documented in §5.11).

#### Read — `(0x2B, 0x11)`

**Request:** `TLV 01 = (empty)`.

| Tag | Type | Description |
|-----|------|-------------|
| 01 | uint8 | `0x01` = wear detection on, `0x00` = off. |

### 5.18 Other observed messages (lower confidence)

| svc cmd | Seen | Notes |
|---------|------|-------|
| `(0x2B, 0x04)` | TX / RX | **ANC write command** (confirmed). TLV Tag 01 = `[enabled, mode]` sets the ANC state. The buds reply with TLV Tag 02 = `0x00` on success, non-zero on rejection. Also used as the ANC write on the 4i — the original assumption that it moved to `0x5D` on FreeBuds 4 Pro was incorrect; `0x5D` is used for Find My Buds (§5.6). |
| `(0x2B, 0x5F)` | RX rare | `TLV 01 = 0x00`. Appeared once near ANC state changes. Possibly a secondary sound attribute (wind noise? voice boost?). **Unidentified — not reliable enough to depend on.** |
| `(0x2B, 0x37)` | TX once | Sent with `TLV 01 = 0x00`. One parameter, value 0. Unresearched. |
| `(0x01, 0x26)` | TX once | `TLV 01 = 0x00`, `TLV 02 = 0x00`. Unresearched. |

---

## 6. Android implementation guide

### 6.1 Permissions (AndroidManifest.xml)

```xml
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT"
    tools:targetApi="31" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    tools:targetApi="31" />
<!-- For Android 11 and below -->
<uses-permission android:name="android.permission.BLUETOOTH"
    android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN"
    android:maxSdkVersion="30" />
<!-- Location was required for BT scanning on older versions -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"
    android:maxSdkVersion="30" />
```

`BLUETOOTH_CONNECT` must be runtime-requested before touching any `BluetoothDevice` API on Android 12+.

### 6.2 Connection lifecycle

```kotlin
class FreeBudsConnection(private val device: BluetoothDevice) {
    private val sppUuid = java.util.UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var socket: BluetoothSocket? = null
    private var reader: Thread? = null

    fun connect() {
        socket = device.createRfcommSocketToServiceRecord(sppUuid).apply {
            connect()
        }
        startReader()
    }

    private fun startReader() {
        reader = Thread {
            val input = socket!!.inputStream
            val framer = FrameReader()
            val buf = ByteArray(512)
            while (!Thread.currentThread().isInterrupted) {
                val n = input.read(buf)
                if (n <= 0) break
                framer.feed(buf, 0, n).forEach(::onFrame)
            }
        }.also { it.start() }
    }

    fun close() {
        reader?.interrupt()
        socket?.close()
    }

    private fun onFrame(frame: Frame) { /* dispatch */ }
}
```

### 6.3 Stream framer

SPP is a byte stream. Frames may arrive split across reads or concatenated, so a stateful framer is required:

```kotlin
class FrameReader {
    private val buf = java.io.ByteArrayOutputStream()

    fun feed(data: ByteArray, offset: Int, length: Int): List<Frame> {
        buf.write(data, offset, length)
        val bytes = buf.toByteArray()
        buf.reset()
        val frames = mutableListOf<Frame>()
        var i = 0
        while (i < bytes.size) {
            // Need at least magic + length (3 bytes) to know frame size
            if (bytes.size - i < 3) break
            if (bytes[i] != 0x5A.toByte()) {
                // Resync: scan for next magic
                i++
                continue
            }
            val len = ((bytes[i + 1].toInt() and 0xFF) shl 8) or (bytes[i + 2].toInt() and 0xFF)
            val totalSize = 1 + 2 + len + 2  // magic + len field + (len bytes) + CRC
            if (bytes.size - i < totalSize) break  // incomplete, wait for more
            val frame = Frame.parse(bytes, i, totalSize)
            if (frame != null) frames += frame
            i += totalSize
        }
        // Re-buffer any tail that wasn't consumed
        if (i < bytes.size) buf.write(bytes, i, bytes.size - i)
        return frames
    }
}

data class Frame(val service: Int, val command: Int, val tlvs: List<Tlv>) {
    companion object {
        fun parse(data: ByteArray, offset: Int, size: Int): Frame? {
            // magic(1) + len(2) + const0(1) + svc(1) + cmd(1) + tlv... + crc(2)
            if (size < 9) return null
            // Verify CRC
            val expected = ((data[offset + size - 2].toInt() and 0xFF) shl 8) or
                           (data[offset + size - 1].toInt() and 0xFF)
            val computed = Crc16Xmodem.compute(data, offset, size - 2)
            if (expected != computed) return null
            val svc = data[offset + 4].toInt() and 0xFF
            val cmd = data[offset + 5].toInt() and 0xFF
            val tlvBytes = data.copyOfRange(offset + 6, offset + size - 2)
            return Frame(svc, cmd, parseTlvs(tlvBytes))
        }
    }
}
```

### 6.4 Writing frames

```kotlin
fun buildFrame(service: Int, command: Int, tlvs: List<Tlv> = emptyList()): ByteArray {
    val tlvBytes = encodeTlvs(tlvs)
    val length = 3 + tlvBytes.size  // const + svc + cmd + tlv
    val header = ByteArray(6).apply {
        this[0] = 0x5A
        this[1] = ((length ushr 8) and 0xFF).toByte()
        this[2] = (length and 0xFF).toByte()
        this[3] = 0x00
        this[4] = service.toByte()
        this[5] = command.toByte()
    }
    val body = header + tlvBytes
    val crc = Crc16Xmodem.compute(body, 0, body.size)
    return body + byteArrayOf(((crc ushr 8) and 0xFF).toByte(), (crc and 0xFF).toByte())
}

// Usage
val getBattery = buildFrame(0x01, 0x08)
val setAncAwareness = buildFrame(0x2B, 0x04, listOf(Tlv(0x01, byteArrayOf(2, 0))))
val ringLeftBud = buildFrame(0x2B, 0x5D, listOf(Tlv(0x01, byteArrayOf(0, 0))))
socket.outputStream.write(setAncAwareness)
```

### 6.5 Dispatch

Incoming frames are routed by `(service, command)`:

```kotlin
fun onFrame(frame: Frame) {
    when (frame.service to frame.command) {
        0x01 to 0x08, 0x01 to 0x27 -> {
            BatteryStatus.fromTlvs(frame.tlvs)?.let(::onBatteryUpdate)
        }
        0x2B to 0x0A -> DeviceInfo.fromTlvs(frame.tlvs)?.let(::onDeviceInfo)
        0x2B to 0x25 -> InEarState.fromTlvs(frame.tlvs)?.let(::onInEarUpdate)
        0x2B to 0x2A -> {
            SoundControl.fromTlvs(frame.tlvs)?.let(::onAncUpdate)
        }
        0x2B to 0x5D, 0x2B to 0x5E -> {
            // Tag 02 carries Find My Buds ringing status (§5.6)
            frame.tlvs.find { it.type == 0x02 }?.let { tlv ->
                ringingStatus = RingingStatus.fromTlv(tlv, ringingStatus)
            }
        }
        0x2B to 0x04 -> {
            // ANC write ack — Tag 02 value 0x00 = success
            val status = frame.tlvs.firstOrNull { it.type == 0x02 }
                ?.value?.getOrNull(0)?.toInt()?.and(0xFF) ?: -1
            if (status != 0) Log.w("FreeBuds", "ANC write rejected, status=$status")
        }
        else -> Log.d("FreeBuds", "Unhandled frame: svc=${frame.service} cmd=${frame.command}")
    }
}
```

### 6.6 Threading notes

- The reader thread blocks on `InputStream.read()` and should run off the main thread.
- Writes are synchronous but fast. For safety, they should be serialized through a single thread or an `Actor`/`Channel`.
- Decoded state can be posted to a `StateFlow` or `LiveData` for the UI layer to observe.
- The socket should not be closed from inside the reader thread — the owner should signal and close it externally.

---

## 7. Minimum viable feature set

A usable first version requires only these message handlers, which cover roughly 95% of what AI Life does:

| Feature | Direction | Frame | Notes |
|---------|-----------|-------|-------|
| Identify device at connect | RX async | `(0x2B, 0x0A)` | The first received bundle provides model, serial, and firmware for an "About" screen. |
| Show battery | RX async | `(0x01, 0x08)` or `(0x01, 0x27)` | Both should be handled — 0x08 is the initial snapshot, 0x27 is the push update. |
| Force battery refresh | TX | `(0x01, 0x08)` with empty body | Only needed for a manual refresh button. |
| Show wear state | RX async | `(0x2B, 0x25)` | Three states per bud: in-ear, out, in-case. Tags 01/02 = in-ear, Tags 03/04 = in-case. Battery frames (TLV 05) only carry in-ear, not in-case. |
| Read current ANC mode | TX | `(0x2B, 0x2A)` with empty body | Called once after connect; then push updates maintain the state. |
| Set ANC mode | TX | `(0x2B, 0x04)` with `TLV 01 = [mode, intensity]` | See §5.4. Mode: 0=off, 1=NC, 2=awareness. Intensity: NC sub-mode or awareness voice toggle. |
| ANC change notification | RX async | `(0x2B, 0x5E)` | Pushed for both programmatic writes and user long-press on the bud. Keeps the UI in sync. |
| Ring earbud | TX | `(0x2B, 0x5D)` with `TLV 01 = [side, action]` | See §5.6. Side: 0=left, 1=right. Action: 0=ring, 1=stop. |
| Ring state notification | RX async | `(0x2B, 0x5E)` Tag 02 | Echoes ringing changes. Reports one side per notification. |
| Read current EQ | TX | `(0x2B, 0x4A)` with `TLV 02 = (empty)` | Returns available presets in Tag 03 and current preset in Tag 02. |
| Set EQ preset | TX | `(0x2B, 0x49)` with `TLV 01 = [preset_id]` | See §5.10. Ack via Tag 7F. |
| Read/set ear tips | TX | `(0x2B, 0xB4)` with `TLV 01 = 0x08` | See §5.13. T02 empty to read, T02=[type] to write. Response echoes value. |
| Read/set case tone | TX | `(0x2B, 0xB4)` with `TLV 01 = 0x0B` | See §5.14. T02 = tone on/off. Response includes T03/T04 for head gestures. |
| Read/set head gestures | TX | `(0x2B, 0xB4)` with `TLV 01 = 0x0B` | See §5.14. T03 = nod action, T04 = shake action. |
| Toggle head control | TX | `(0x2B, 0x6C)` with `TLV 01 = [0/1]` | See §5.15. Read via T02=(empty). |
| Read low latency | TX | `(0x2B, 0xA3)` with empty body | See §5.16. Response T02 = on/off. |
| Set low latency | TX | `(0x2B, 0xA2)` with `TLV 01 = [0/1]` | See §5.16. Triggers push via `(0x2B, 0xA3)`. |
| Read wear detection | TX | `(0x2B, 0x11)` with `TLV 01 = (empty)` | See §5.17. Response T01 = on/off. |

---

## 8. Known unknowns

Areas that require further reverse engineering:

- **`(0x2B, 0x2C)`** — Pushed in bursts of 2–3 after the buds transition to "in ear" state. Always `Tag 01 = 0x00`. Likely a media session / audio routing readiness signal.
- **`(0x2B, 0x7F)`** — Pushed when both buds are seated in the case. `Tag 04 = 0x01`. Possibly case lid closed or both-buds-seated indicator.
- **`(0x2B, 0x5F)`** — Seen once near an ANC change. Likely a secondary sound attribute.
- **`(0x2B, 0xAC)`** — Pushed after `(0x2B, 0x2A)` on connect. Carries 9 discrete tags (T1–T9), mostly zeros. Not an ANC state update. Possibly device capability flags.
- **`(0x2B, 0x8F)`** — Polled by AI Life every ~3s on the earbud settings screen. Always `T01=(empty)` query, `T7F=000186A3` response. Likely signal quality monitoring.
- **`(0x2B, 0x42)`** — Queried on screen init. Response `T01=00`. Purpose unknown.
- **`(0x2B, 0xB3)`** — Large initialization payload sent by AI Life on connect (16+ tags). Purpose unknown.
- **`(0x2B, 0x37)` and `(0x01, 0x26)`** — Sent by the phone once each, no visible effect. Unresearched.
- **Custom EQ band mapping** — The 10-byte EQ curve in `(0x2B, 0x49)` Tag 03 uses a signed byte per band, but the exact internal scale (how dB maps to byte values) needs calibration with isolated single-band changes.
- **Firmware update protocol** — Not researched. Huawei firmware is signed, so custom flashing is not realistic.
- **Gesture config (`(0x01, 0x20)` / `(0x01, 0x1F)`)** — Inherited from the 4i reference, not exercised in captures. Likely works identically but has not been verified on FreeBuds 4 Pro.
- **Voice language (`(0x0C, 0x01)` / `(0x0C, 0x02)`)** — Same status as gesture config: 4i reference exists, not verified on FreeBuds 4 Pro.

Investigation approach: pair the buds with AI Life, start `btsnoop` logging in developer options, toggle one setting, and diff the resulting frames. For binary analysis, use `analysis/parse_btsnoop.py` to decode btsnoop HCI logs directly.

---

## 9. References

- **MelianMiko — FreeBuds 4i protocol.** Closest documented relative. Most TLV conventions and the CRC algorithm originate from here. <https://mmk.pw/en/posts/freebuds-4i-proto/>
- **TheLastGimbus — FreeBuddy mbb-protocol notes.** Original source for the CRC16-XModem identification. <https://github.com/TheLastGimbus/FreeBuddy/blob/master/notes/mbb-protocol-wiki.md>
- **Gadgetbridge issue #4241** (FreeBuds 5i). Confirms frame structure `5A [len] 00 [svc cmd] [TLV...] [CRC]`. <https://codeberg.org/Freeyourgadget/Gadgetbridge/issues/4241>
- **OpenFreebuds.** Desktop/mobile open-source implementation for related devices; useful reference for command semantics. <https://github.com/melianmiko/OpenFreebuds>

---

## Appendix A — Quick reference card

```
FRAME   5A | LLLL | 00 | SS | CC | TLVs... | CRCCRC
        magic len      svc  cmd            CRC16-XM

LENGTH  length = 3 + len(TLV bytes)

TLV     TT | LL | VV...
        tag len   value

SERVICES
  0x01  SYSTEM   (battery, device info, language, gestures)
  0x2B  DEVICE   (ANC, in-ear, per-device settings)

ESSENTIAL COMMANDS
  TX  (0x01,0x08)  Get battery               → reply (0x01,0x08)
  RX  (0x01,0x27)  Battery push notification
  RX  (0x2B,0x0A)  Device info (auto on connect)
  RX  (0x2B,0x25)  Wear state change (in-ear / in-case / out)
  TX  (0x2B,0x2A)  Get ANC mode              → reply (0x2B,0x2A)
  TX  (0x2B,0x04)  Set ANC mode              → ack   (0x2B,0x04)
  TX  (0x2B,0x4A)  Get EQ state              → reply (0x2B,0x4A)
  TX  (0x2B,0x49)  Set EQ preset             → ack   T7F
  TX  (0x2B,0xB4)  Read/set ear tips (T01=08)  → echo  (0x2B,0xB4)
  TX  (0x2B,0xB4)  Read/set case tone (T01=0B) → echo (0x2B,0xB4)
  TX  (0x2B,0x6C)  Toggle head control        → ack   T7F
  TX  (0x2B,0xA3)  Read low latency           → reply (0x2B,0xA3)
  TX  (0x2B,0xA2)  Set low latency            → push  (0x2B,0xA3)
  TX  (0x2B,0x11)  Read wear detection        → reply (0x2B,0x11)
  TX  (0x2B,0x10)  Set wear detection         → ack   T7F
  TX  (0x2B,0x32)  Set preferred device       → ack   T7F
  TX  (0x2B,0x5D)  Ring earbud               → echo  (0x2B,0x5E)
  RX  (0x2B,0x5E)  Sound control / ringing changed
  RX  (0x2B,0x31)  Audio source info (device name + state)
  RX  (0x2B,0x36)  Playback state (double-tap)
  RX  (0x2B,0x4B)  Volume gesture (swipe up/down)

BATTERY TLVs
  01  uint8     aggregate %
  02  3×uint8   [L%, R%, case%]
  03  3×uint8   [L charging, R charging, case charging]  0/1
  05  2×uint8   [L in-ear, R in-ear]                     0/1

ANC ENCODING
  Write (0x2B,0x04) TLV 01 = [mode, intensity]
  Read  (0x2B,0x2A) TLV 01 = [intensity, mode]   (bytes swapped!)
    mode: 0=off  1=NC  2=awareness
    NC intensity: 0=general  1=cozy  2=ultra  3=dynamic
    Awareness:    1=voice mode  2=normal

FIND MY BUDS (write via 0x2B,0x5D / notify via 0x2B,0x5E Tag 02)
  TLV 01 value [side, action]  (write)
  TLV 02 value [side, action]  (notification)
    side:   0 = left, 1 = right
    action: 0 = ring, 1 = stop

EQ PRESETS (read via 0x2B,0x4A / write via 0x2B,0x49)
  Read:  TLV 02=(empty)  → T02=current_id  T03=available_ids
  Write: TLV 01=[preset_id]  → ack T7F=000186A0
    0x02=Bass Boost  0x03=Treble Boost  0x05=Default
    0x09=Voices      0x0B=Balanced      0x0C=Classical
    0xC8=Symphony    0xC9=Hi-Fi Live    0x64=Custom 1

EAR TIPS (0x2B,0xB4)
  TLV 01=0x08 (sub-cmd), TLV 02=[type]
  Read:  T02=(empty)  → response echoes T02=current
  Write: T02=[type]   → response echoes T02=new
    0x01=silicone  0x02=memory foam

CASE TONE + HEAD GESTURES (0x2B,0xB4 T01=0x0B)
  Read:  T02=(empty)  → T02=[tone] T03=[nod] T04=[shake]
  Write: T02/T03/T04=[value]  → echo all current values
    T02 case tone: 0x00=off  0x01=on
    T03 nod:       0x00=none  0x01=answer  0x02=reject
    T04 shake:     0x00=none  0x01=answer  0x02=reject

HEAD CONTROL (0x2B,0x6C)
  Read:  TLV 02=(empty)  → T02=[0/1]
  Write: TLV 01: 0x00=off  0x01=on  → ack T7F

LOW AUDIO LATENCY
  Read  (0x2B,0xA3) empty  → T02: 0x00=off  0x01=on
  Write (0x2B,0xA2) T01: 0x00=off  0x01=on  → push (0x2B,0xA3)

WEAR DETECTION
  Read  (0x2B,0x11) T01=(empty)  → T01: 0x00=off  0x01=on
  Write (0x2B,0x10) T01: 0x00=off  0x01=on  → ack T7F

PREFERRED DEVICE (0x2B,0x32)
  TLV 01: 6-byte BT addr, or 000000000000=auto  → ack T7F
```
