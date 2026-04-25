#!/usr/bin/env python3
"""
decode_inline.py — Decode the extracted SPP inline frames from spp_inline_frames.txt
and produce a human-readable timeline with action correlation.
"""

import re
import sys

# ---------------------------------------------------------------------------
# Decode Wireshark escaped-string format
# ---------------------------------------------------------------------------

def decode_wireshark_string(s: str) -> bytes:
    result = bytearray()
    i = 0
    while i < len(s):
        if s[i] == '\\' and i + 1 < len(s):
            nxt = s[i + 1]
            if nxt == '\\':
                result.append(ord('\\'))
                i += 2
            elif nxt == 'n':
                result.append(0x0A)
                i += 2
            elif nxt == 'r':
                result.append(0x0D)
                i += 2
            elif nxt == 't':
                result.append(0x09)
                i += 2
            elif nxt == 'a':
                result.append(0x07)
                i += 2
            elif nxt == 'b':
                result.append(0x08)
                i += 2
            elif nxt == 'f':
                result.append(0x0C)
                i += 2
            elif nxt == 'v':
                result.append(0x0B)
                i += 2
            elif nxt == 'x' and i + 3 < len(s):
                result.append(int(s[i + 2 : i + 4], 16))
                i += 4
            elif nxt.isdigit():
                end = i + 2
                while end < len(s) and end < i + 5 and s[end].isdigit() and int(s[end]) < 8:
                    end += 1
                octal_str = s[i + 1 : min(end, i + 4)]
                result.append(int(octal_str, 8))
                i += 1 + len(octal_str)
            else:
                result.append(ord(s[i]))
                i += 1
        else:
            result.append(ord(s[i]))
            i += 1
    return bytes(result)


# ---------------------------------------------------------------------------
# CRC / TLV / Frame
# ---------------------------------------------------------------------------

def crc16_xmodem(data: bytes) -> int:
    crc = 0
    for b in data:
        crc ^= b << 8
        for _ in range(8):
            crc = ((crc << 1) ^ 0x1021) & 0xFFFF if crc & 0x8000 else (crc << 1) & 0xFFFF
    return crc


def parse_tlvs(payload: bytes) -> list:
    tlvs = []
    i = 0
    while i + 2 <= len(payload):
        tag = payload[i]
        length = payload[i + 1]
        if i + 2 + length > len(payload):
            break
        tlvs.append((tag, payload[i + 2 : i + 2 + length]))
        i += 2 + length
    return tlvs


def parse_frame(data: bytes):
    if len(data) < 9 or data[0] != 0x5A:
        return None
    length = (data[1] << 8) | data[2]
    total = 1 + 2 + length + 2
    if len(data) < total:
        return None
    expected_crc = (data[total - 2] << 8) | data[total - 1]
    computed_crc = crc16_xmodem(data[: total - 2])
    svc = data[4]
    cmd = data[5]
    tlv_bytes = data[6 : total - 2]
    return {
        "svc": svc,
        "cmd": cmd,
        "tlvs": parse_tlvs(tlv_bytes),
        "raw": data[:total],
        "crc_ok": expected_crc == computed_crc,
    }


# ---------------------------------------------------------------------------
# Describe known commands
# ---------------------------------------------------------------------------

NAMES = {
    (0x01, 0x08): "BATTERY_REPLY",
    (0x01, 0x27): "BATTERY_PUSH",
    (0x2B, 0x04): "ANC_ACK",
    (0x2B, 0x0A): "DEVICE_INFO",
    (0x2B, 0x25): "IN_EAR_STATE",
    (0x2B, 0x2A): "ANC_MODE",
    (0x2B, 0x5D): "FIND_BUDS_TX",
    (0x2B, 0x5E): "SOUND_NOTIFY",
}


def tlv_val(tlvs, tag):
    for t, v in tlvs:
        if t == tag:
            return v
    return None


def describe(f, direction):
    svc, cmd, tlvs = f["svc"], f["cmd"], f["tlvs"]
    cid = f"{svc:02X}:{cmd:02X}"
    name = NAMES.get((svc, cmd), "???")
    extra = ""

    if (svc, cmd) == (0x2B, 0x2A):
        v = tlv_val(tlvs, 0x01)
        if v and len(v) >= 2:
            intensity, mode = v[0], v[1]
            mn = {0: "OFF", 1: "NC", 2: "Awareness"}.get(mode, f"?{mode}")
            if mode == 1:
                sub = {0: "general", 1: "cozy", 2: "ultra", 3: "dynamic"}.get(intensity, f"?{intensity}")
                extra = f"{mn} ({sub})"
            elif mode == 2:
                extra = f"Awareness ({'voice' if intensity == 1 else 'normal'})"
            else:
                extra = mn
        elif v is not None and len(v) == 0:
            extra = "(query)"

    elif (svc, cmd) == (0x2B, 0x25):
        def get(tag):
            v = tlv_val(tlvs, tag)
            return v[0] if v else 0
        t1, t2, t3, t4 = get(1), get(2), get(3), get(4)

        def w(ear, case):
            return "in-ear" if ear else ("in-case" if case else "out")
        extra = f"L={w(t1, t3)} R={w(t2, t4)}"

    elif (svc, cmd) in ((0x01, 0x08), (0x01, 0x27)):
        v2 = tlv_val(tlvs, 0x02)
        v3 = tlv_val(tlvs, 0x03)
        v5 = tlv_val(tlvs, 0x05)
        parts = []
        if v2 and len(v2) == 3:
            parts.append(f"L={v2[0]}% R={v2[1]}% C={v2[2]}%")
        if v3 and len(v3) == 3:
            ch = [s for s, b in [("L", v3[0]), ("R", v3[1]), ("C", v3[2])] if b]
            if ch:
                parts.append(f"charging={','.join(ch)}")
        if v5 and len(v5) == 2:
            parts.append(f"wear=[{'in' if v5[0] else 'out'},{'in' if v5[1] else 'out'}]")
        extra = " ".join(parts)

    elif (svc, cmd) == (0x2B, 0x5E):
        parts = []
        v1 = tlv_val(tlvs, 0x01)
        v2 = tlv_val(tlvs, 0x02)
        if v1:
            parts.append(f"snd_tag01={v1.hex()}")
        if v2 and len(v2) >= 2:
            side = "left" if v2[0] == 0 else "right"
            act = "ringing" if v2[1] == 0 else "stopped"
            parts.append(f"{side} {act}")
        extra = ", ".join(parts)

    elif (svc, cmd) == (0x2B, 0x5D):
        v1 = tlv_val(tlvs, 0x01)
        if v1 and len(v1) >= 2:
            side = "left" if v1[0] == 0 else "right"
            act = "ring" if v1[1] == 0 else "stop"
            extra = f"{side} {act}"

    return f"[{direction}] {cid} {name:16s} {extra}"


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    path = sys.argv[1] if len(sys.argv) > 1 else "spp_inline_frames.txt"

    LINE_RE = re.compile(r"^([\d.]+)\s+(TX|RX)\s+\"(.+)\"$")

    frames = []
    with open(path) as f:
        for line in f:
            line = line.strip()
            if line.startswith("#") or not line:
                continue
            m = LINE_RE.match(line)
            if not m:
                continue
            ts, direction, escaped = m.groups()
            raw = decode_wireshark_string(escaped)

            # Parse all frames in the buffer (in case of concatenation)
            offset = 0
            while offset < len(raw):
                if raw[offset] != 0x5A:
                    offset += 1
                    continue
                frame = parse_frame(raw[offset:])
                if frame:
                    frames.append((float(ts), direction, frame))
                    offset += len(frame["raw"])
                else:
                    offset += 1

    # Print timeline
    print(f"{'Timestamp':>12s}  {'Dir':3s}  {'Cmd':5s}  {'Name':16s}  Description")
    print("-" * 90)

    prev_ts = None
    for ts, d, f in frames:
        if prev_ts and ts - prev_ts > 5.0:
            gap = ts - prev_ts
            print(f"{'':>12s}  {'':3s}  {'':5s}  {'--- gap ---':16s}  {gap:.1f}s")
        desc = describe(f, d)
        crc = "OK" if f["crc_ok"] else "!CRC"
        raw_hex = f["raw"].hex().upper()
        print(f"{ts:12.3f}  {desc}  [{crc}]")
        prev_ts = ts

    # Summarize unique command IDs
    print()
    print("=" * 60)
    print("UNIQUE COMMANDS SEEN")
    print("=" * 60)
    seen = {}
    for ts, d, f in frames:
        key = (f["svc"], f["cmd"], d)
        if key not in seen:
            seen[key] = 0
        seen[key] += 1
    for (svc, cmd, d), count in sorted(seen.items()):
        name = NAMES.get((svc, cmd), "???")
        print(f"  [{d}] {svc:02X}:{cmd:02X}  {name:20s}  x{count}")

    # Print the in-ear state sequence for wear-detection correlation
    print()
    print("=" * 60)
    print("IN-EAR STATE SEQUENCE (for wear detection toggle correlation)")
    print("=" * 60)
    for ts, d, f in frames:
        if (f["svc"], f["cmd"]) == (0x2B, 0x25):
            desc = describe(f, d)
            print(f"  {ts:12.3f}  {desc}")

    # Print ANC mode changes
    print()
    print("=" * 60)
    print("ANC MODE CHANGES")
    print("=" * 60)
    prev_anc = None
    for ts, d, f in frames:
        if (f["svc"], f["cmd"]) == (0x2B, 0x2A):
            desc = describe(f, d)
            v = tlv_val(f["tlvs"], 0x01)
            if v != prev_anc:
                marker = " <<<" if prev_anc is not None else ""
                print(f"  {ts:12.3f}  {desc}{marker}")
                prev_anc = v


if __name__ == "__main__":
    main()
