# Protocol Exploration — 2026-04-25 Session

Wireshark btsnoop capture of AI Life ↔ FreeBuds Pro 4 traffic while performing
known actions in AI Life. Two capture files: `btsnoop_hci.log` (1.2MB, AI Life
session with all actions) and `btsnoop_hci.log.last` (12MB, FreeBuddies passive
observation). Both earbuds remained in the case for the entire session.

## Setup

- **Phone:** Samsung Galaxy S22 ("Adrian's S22")
- **Buds:** HUAWEI FreeBuds Pro 4 (`HuaweiDevice_3d:46:f3`)
- **Apps running:** AI Life (performing actions), FreeBuddies (observing SPP)
- **Capture files:** `btsnoop_hci.log` (AI Life traffic), `btsnoop_hci.log.last` (FreeBuddies traffic)

## Tools

- [`analysis/parse_btsnoop.py`](analysis/parse_btsnoop.py) — **Primary tool.** Parses btsnoop binary files directly, extracts all MDN frames with CRC validation
- [`analysis/decode_session.py`](analysis/decode_session.py) — Parses Wireshark text exports
- [`analysis/decode_inline.py`](analysis/decode_inline.py) — Decodes extracted inline frames
- [`analysis/actions.md`](analysis/actions.md) — Structured list of every action taken during the session

Usage: `python3 analysis/parse_btsnoop.py btsnoop_hci.log --raw`

---

## New Command Discovery

The binary btsnoop parse of `btsnoop_hci.log` (AI Life session) revealed **13 new
command IDs** not previously documented. 270 frames in the FreeBuddies capture,
533 frames in the AI Life capture.

### Complete Command Map

| Cmd | Name | Dir | Count | Status |
|-----|------|-----|-------|--------|
| `01:06` | ??? | RX | 13 | New — empty payload, appears on every connect |
| `01:07` | Device info (alternate) | TX/RX | 21/23 | Known from docs, now confirmed |
| `01:08` | Battery reply | TX/RX | ~20 | Known |
| `01:27` | Battery push | RX | ~29 | Known |
| `2B:04` | ANC write/ack | TX/RX | — | Known |
| `2B:0A` | Device info | TX/RX | 5/5 | Known |
| `2B:10` | **Wear detection toggle** | TX/RX | 6/6 | **New** |
| `2B:11` | **Ear tips setting** | TX/RX | 2/2 | **New** |
| `2B:25` | In-ear state | RX | 38 | Known |
| `2B:2A` | ANC mode read | TX/RX | 21/37 | Known |
| `2B:2C` | **Audio session ready** | RX | 21 | **New** |
| `2B:2D` | **??? (init query)** | TX/RX | 5/5 | New — always T01=03 in reply |
| `2B:2F` | **??? (init query)** | TX/RX | 2/2 | New — always T01=01 in reply |
| `2B:31` | Audio source | TX/RX | 1/14 | Known |
| `2B:32` | **Preferred device** | TX/RX | 6/6 | **New** |
| `2B:36` | Playback state | RX | 13–16 | Known |
| `2B:37` | ??? | TX/RX | 1/1 | Known from docs, unresearched |
| `2B:49` | **EQ preset select** | TX/RX | 58/58 | **New — major find** |
| `2B:4A` | **EQ capabilities** | TX/RX | 13/71 | **New** |
| `2B:4B` | Volume gesture | RX | — | Known |
| `2B:5D` | Find my buds write | TX | 2 | Known |
| `2B:5E` | Sound control notify | RX | 18 | Known |
| `2B:5F` | ??? | RX | 1 | Rare, near ANC changes |
| `2B:61` | **??? (init query)** | TX | 11 | New — queried on every connect |
| `2B:70` | **??? (reconnect)** | TX/RX | 5/5 | New — sent on reconnect |
| `2B:7F` | **Case lid state?** | RX | 8 | New — T04=01, appears with case events |
| `2B:9A` | ??? | TX/RX | 1/1 | New — single occurrence |
| `2B:A8` | ??? | TX/RX | 3/3 | New — always T01=01 in reply |
| `2B:AC` | **Capabilities dump** | TX/RX | 9/11–13 | Partially known — 9 boolean tags |
| `2B:B1` | **??? (init query)** | TX/RX | 5/5 | New — T02=01, T03=01 in reply |
| `2B:B3` | **??? (init config)** | TX/RX | 6/8 | New — large init payload |
| `2B:B4` | **??? (init query)** | TX/RX | 8/8 | New — T01=08, T02=01 in reply |

---

## Finding 1: EQ Preset Select — `(0x2B, 0x49)`

**Confirmed.** 58 TX/58 RX frames. This is the sound effect / EQ preset command.

### Write encoding

| Tag | Type | Description |
|-----|------|-------------|
| 01 | uint8 or multi-byte | Preset ID. Simple presets use 1 byte. Custom EQ uses multi-tag format. |

### Preset IDs (Tag 01, 1-byte)

| T01 | Preset | Sequence |
|-----|--------|----------|
| `05` | Default | First, and returned to multiple times |
| `0B` | Balanced | 2nd (from Default) |
| `0C` | Classical? | 3rd |
| `02` | Bass Boost? | 4th |
| `03` | Treble Boost? | 5th |
| `09` | Voices/Symphony/Hi-Fi? | 6th |

**Note:** Preset IDs `0B` and `0C` appeared after the initial Default→Balanced→Default
cycle, suggesting `0B`=Balanced. The mapping after Classical needs more isolated tests
to pin down exactly.

### Built-in presets with EQ curve (multi-tag)

Two presets include full EQ curve data:

```
T01=C8  T02=0A  T05=01  T03=0f0f0afb0f190ffb322d  T04=323030
T01=C9  T02=0A  T05=01  T03=fb141e0a0000e7f60a00  T04=323031
```

- **T01:** Preset ID (`C8`, `C9` — internal preset codes)
- **T02:** Number of EQ bands (0x0A = 10)
- **T05:** Active flag? (`01` = enabled)
- **T03:** 10-byte EQ curve — one signed byte per band (dB offset, range roughly -20 to +50)
- **T04:** Preset name in ASCII (`"200"`, `"201"`)

### Custom EQ

```
T01=64  T02=0A  T05=00  T03=0a000000000000000000  T04="My sound effect 1"
```

- **T01=64** (100 decimal) — custom preset slot
- **T03:** 10 bytes, all zero except first byte `0A` (+10 on the 60Hz band — matches
  the user's "increased band 60 to 1" action, which maps to +10 in the internal scale)
- **T04:** User-visible name

### Response

All writes get the same ack: `T7F=000186A0` (Tag 0x7F, value 100000 — likely a
status/timeout value).

---

## Finding 2: Wear Detection Toggle — `(0x2B, 0x10)`

**Confirmed.** 6 TX / 6 RX frames matching the 6 toggles performed.

### Encoding

| Tag | Type | Description |
|-----|------|-------------|
| 01 | uint8 | `0x00` = wear detection OFF, `0x01` = wear detection ON |

### Timeline

```
135.667  TX  2B:10  T01=00  → OFF (toggle 1: ON→OFF)
138.395  TX  2B:10  T01=01  → ON  (toggle 2: OFF→ON)
139.954  TX  2B:10  T01=00  → OFF (toggle 3)
141.562  TX  2B:10  T01=01  → ON  (toggle 4)
143.203  TX  2B:10  T01=00  → OFF (toggle 5)
144.812  TX  2B:10  T01=01  → ON  (toggle 6)
```

Response: `T7F=000186A0` (same ack as EQ).

### Side effects

When wear detection is toggled OFF, the buds stop reading the proximity sensor
and report `L=in-ear` via `(0x2B, 0x25)`, which triggers ANC to engage at the
last-used NC preset. When toggled ON, the sensor correctly reads "in case" and
ANC turns off.

---

## Finding 3: Ear Tips Setting — `(0x2B, 0x11)`

**Confirmed.** 2 TX / 2 RX frames. Only 2 because AI Life queries the current
tip setting and only writes when it changes. The user's 6 switches between
silicone and memory foam produce writes only when the value actually changes.

### Encoding (inferred)

| Tag | Type | Description |
|-----|------|-------------|
| 01 | empty (query) | Read request |

Response: `T01=01` — likely `01` = silicone tips (default), other value = memory foam.

**Note:** Needs more data — the AI Life session only had 2 TX (both queries), and
the response was `T01=01` both times. The actual write for switching may use a
different command or a different tag.

---

## Finding 4: Preferred Device — `(0x2B, 0x32)`

**Confirmed.** 6 TX / 6 RX frames matching the 6 preference switches.

### Encoding

| Tag | Type | Description |
|-----|------|-------------|
| 01 | 6 bytes | Device Bluetooth address or `000000000000` for "Auto" |

### Timeline

```
180.362  TX  T01=771b87b975a4  → "Adrian's S22"
181.900  TX  T01=4bb00f74d0bc  → "Adrian's MacBook Pro"
183.525  TX  T01=000000000000  → Auto
184.848  TX  T01=771b87b975a4  → "Adrian's S22"
186.121  TX  T01=4bb00f74d0bc  → "Adrian's MacBook Pro"
187.495  TX  T01=000000000000  → Auto
```

This matches the action log perfectly: S22 → MacBook → Auto × 2.

Response: `T7F=000186A0` (standard ack).

---

## Finding 5: EQ Capabilities — `(0x2B, 0x4A)`

**Observed.** 13 TX (queries) / 71 RX (responses — many duplicates from reconnects).

### Response format

```
T01=01  T02=05  T03=020305090b0c  T04=05  T08=(empty)
```

- **T01=01:** Status or version
- **T02=05:** Currently selected preset ID (`05` = Default)
- **T03:** List of available preset IDs: `02 03 05 09 0B 0C`
- **T04=05:** Default preset ID
- **T08:** Empty (reserved?)

This gives us the full preset ID list:
| ID | Preset (inferred) |
|----|-------------------|
| `02` | Bass Boost |
| `03` | Treble Boost |
| `05` | Default |
| `09` | ??? |
| `0B` | ??? |
| `0C` | ??? |

The user cycled: Default → Balanced → Default → Classical → Bass Boost → Treble Boost
→ Voices → Symphony → Hi-Fi Live. The 6 IDs need to be mapped to the 8 named presets
(some presets may share IDs or be sub-modes).

---

## Finding 6: Audio Session Ready — `(0x2B, 0x2C)`

**Observed.** 21 RX frames. Always `T01=00`. Appears in bursts of 2-3 after the
buds transition to "in-ear" state (wear detection OFF). Likely a media session
signal telling the phone the buds are ready for audio routing.

Pattern: appears ~1-4 seconds after `(0x2B, 0x25) L=in-ear`, then repeats at
~3s intervals until audio state stabilizes.

---

## Finding 7: Case Lid / Proximity — `(0x2B, 0x7F)`

**Observed.** 8 RX frames. Always `T04=01`. Appears when both buds are detected
in the case (co-occurs with `(0x2B, 0x25) L=in-case R=in-case`). May indicate
case lid closed or both buds seated.

---

## Finding 8: Standard Ack — `T7F=000186A0`

Many write commands share the same response format: **Tag 0x7F with value
`000186A0`** (100000 decimal). This appears to be a universal "write accepted"
acknowledgment. Observed on:
- `0x2B:0x49` (EQ select)
- `0x2B:0x10` (wear detection)
- `0x2B:0x32` (preferred device)
- `0x2B:0x4A` (EQ capabilities — some responses)

---

## AI Life Init Sequence

When AI Life connects, it sends a deterministic initialization sequence:

```
01:07  GET_DEVICE_INFO (×3)
2B:B3  Large config query (16 tags)
2B:AC  Capabilities query (T01=01, T02=01, T0A=00)
01:08  Battery request
2B:27  ??? (T02=00, T03=00)
2B:B4  ??? (T01=08)
2B:2D  ??? (query → T01=03)
2B:B1  ??? (query → T02=01, T03=01)
2B:61  ??? (T01 and T02 queries — no visible response in capture)
2B:4A  EQ capabilities query (→ preset list)
2B:0A  Device info
2B:2A  ANC mode query
2B:A8  ??? (→ T01=01)
2B:4A  EQ capabilities (again)
2B:49  EQ preset query (T01=05 → current preset)
2B:2F  ??? (query → T01=01)
2B:31  Audio source query
```

---

## Summary

| Action | Command | Encoding | Confidence |
|--------|---------|----------|------------|
| **EQ preset** | `2B:49` | T01 = preset ID (1 byte) | High |
| **Custom EQ** | `2B:49` | T01=64, T02=bands, T03=curve, T04=name | High |
| **EQ capabilities** | `2B:4A` | T02=current, T03=available list | High |
| **Wear detection** | `2B:10` | T01: 00=off, 01=on | High |
| **Ear tips** | `2B:11` | T01 query → T01=01 (silicone?) | Low |
| **Preferred device** | `2B:32` | T01 = 6-byte BT addr, or 000000000000=auto | High |
| **Audio session** | `2B:2C` | T01=00 (notification only) | Medium |
| **Case state** | `2B:7F` | T04=01 (both buds seated) | Medium |

## Next Steps

1. **Map EQ preset IDs** to names — perform isolated single-preset changes
2. **Ear tips write command** — the `0x2B:0x11` captures only show queries;
   the actual write may use a different tag or be part of a compound operation
3. **Custom EQ band mapping** — determine which of the 10 T03 bytes maps to
   which frequency band (60Hz, 120Hz, 250Hz, 500Hz, 1kHz, 2kHz, 4kHz, 8kHz, 12kHz, 16kHz)
4. **Implement EQ read/write** in FreeBuddies using `0x2B:0x4A` to read
   capabilities and `0x2B:0x49` to set presets
