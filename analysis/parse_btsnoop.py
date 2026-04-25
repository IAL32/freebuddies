#!/usr/bin/env python3
"""
parse_btsnoop.py — Parse btsnoop_hci.log binary files and extract all
Huawei MDN protocol frames (0x5A magic) from RFCOMM/SPP data.

btsnoop format:
  File header: 16 bytes
    - "btsnoop\0" (8 bytes)
    - version (4 bytes BE)
    - datalink type (4 bytes BE) — 1002 = HCI
  Record header: 24 bytes
    - original length (4 bytes BE)
    - included length (4 bytes BE)
    - packet flags (4 bytes BE) — bit 0: 0=sent, 1=received; bit 1: 0=data, 1=cmd/event
    - cumulative drops (4 bytes BE)
    - timestamp (8 bytes BE) — microseconds since 0000-01-01 00:00:00
  Record data: included_length bytes (HCI packet)

HCI packet:
  - For ACL data (type 0x02): handle(2) + length(2) + L2CAP data
  - L2CAP: length(2) + CID(2) + payload
  - RFCOMM is on a dynamic L2CAP CID, carries UIH frames
  - UIH frame: address(1) + control(1) + [length] + data + FCS(1)

We skip the complex L2CAP/RFCOMM dissection and just scan every HCI ACL
payload for the 0x5A magic byte followed by a valid MDN frame structure.
"""

import struct
import sys
import os
from datetime import datetime, timedelta

# btsnoop epoch: 0000-01-01 00:00:00
# Unix epoch:    1970-01-01 00:00:00
# Difference in microseconds:
BTSNOOP_EPOCH_DELTA = 0x00dcddb30f2f8000  # microseconds between 0000-01-01 and 1970-01-01


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


def try_parse_mdn(data: bytes, offset: int):
    """Try to parse an MDN frame at the given offset. Returns (frame_dict, total_size) or None."""
    if offset + 9 > len(data):
        return None
    if data[offset] != 0x5A:
        return None
    length = (data[offset + 1] << 8) | data[offset + 2]
    if length < 3 or length > 500:
        return None
    total = 1 + 2 + length + 2
    if offset + total > len(data):
        return None
    frame_bytes = data[offset : offset + total]
    expected_crc = (frame_bytes[-2] << 8) | frame_bytes[-1]
    computed_crc = crc16_xmodem(frame_bytes[:-2])
    if expected_crc != computed_crc:
        return None
    if frame_bytes[3] != 0x00:
        return None
    svc = frame_bytes[4]
    cmd = frame_bytes[5]
    tlv_bytes = frame_bytes[6:-2]
    return {
        "svc": svc,
        "cmd": cmd,
        "tlvs": parse_tlvs(tlv_bytes),
        "raw": frame_bytes,
        "crc_ok": True,
    }, total


def scan_for_mdn_frames(data: bytes):
    """Scan a byte buffer for all valid MDN frames."""
    frames = []
    i = 0
    while i < len(data):
        if data[i] == 0x5A:
            result = try_parse_mdn(data, i)
            if result:
                frame, size = result
                frames.append(frame)
                i += size
                continue
        i += 1
    return frames


NAMES = {
    (0x01, 0x07): "GET_DEVICE_INFO",
    (0x01, 0x08): "BATTERY_REPLY",
    (0x01, 0x27): "BATTERY_PUSH",
    (0x01, 0x26): "UNKNOWN_0126",
    (0x02, 0x04): "UNKNOWN_0204",
    (0x0C, 0x01): "VOICE_LANG_R",
    (0x0C, 0x02): "VOICE_LANG_W",
    (0x2B, 0x04): "ANC_WRITE/ACK",
    (0x2B, 0x0A): "DEVICE_INFO",
    (0x2B, 0x18): "ANC_CYCLE_W",
    (0x2B, 0x19): "ANC_CYCLE_R",
    (0x2B, 0x25): "IN_EAR_STATE",
    (0x2B, 0x2A): "ANC_MODE",
    (0x2B, 0x31): "AUDIO_SOURCE",
    (0x2B, 0x36): "PLAYBACK",
    (0x2B, 0x37): "UNKNOWN_2B37",
    (0x2B, 0x4B): "VOLUME",
    (0x2B, 0x5D): "FIND_BUDS_TX",
    (0x2B, 0x5E): "SOUND_NOTIFY",
    (0x2B, 0x5F): "UNKNOWN_2B5F",
    (0x2B, 0x7F): "UNKNOWN_2B7F",
    (0x2B, 0xAC): "UNKNOWN_2BAC",
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
                parts.append(f"chg={','.join(ch)}")
        if v5 and len(v5) == 2:
            parts.append(f"wear=[{'in' if v5[0] else 'out'},{'in' if v5[1] else 'out'}]")
        extra = " ".join(parts)

    elif (svc, cmd) == (0x2B, 0x5E):
        parts = []
        v1 = tlv_val(tlvs, 0x01)
        v2 = tlv_val(tlvs, 0x02)
        if v1:
            parts.append(f"T01={v1.hex()}")
        if v2 and len(v2) >= 2:
            side = "L" if v2[0] == 0 else "R"
            act = "ring" if v2[1] == 0 else "stop"
            parts.append(f"{side}:{act}")
        extra = " ".join(parts)

    elif (svc, cmd) == (0x2B, 0x5D):
        v1 = tlv_val(tlvs, 0x01)
        if v1 and len(v1) >= 2:
            side = "left" if v1[0] == 0 else "right"
            act = "ring" if v1[1] == 0 else "stop"
            extra = f"{side} {act}"

    elif (svc, cmd) == (0x2B, 0x0A):
        parts = []
        for tag in range(1, 7):
            v = tlv_val(tlvs, tag)
            if v:
                try:
                    parts.append(f"T{tag:02X}=\"{v.decode('ascii', errors='replace')}\"")
                except:
                    parts.append(f"T{tag:02X}={v.hex()}")
        extra = " ".join(parts)

    # For unknown commands, just dump TLVs
    if not extra and name.startswith("???") or name.startswith("UNKNOWN"):
        parts = []
        for tag, val in tlvs:
            if len(val) <= 16:
                parts.append(f"T{tag:02X}={val.hex() if val else '(empty)'}")
            else:
                parts.append(f"T{tag:02X}=[{len(val)}B]")
        extra = " ".join(parts)

    return f"[{direction}] {cid} {name:16s} {extra}"


def main():
    if len(sys.argv) < 2:
        print(f"Usage: {sys.argv[0]} <btsnoop_hci.log> [--raw]")
        sys.exit(1)

    filepath = sys.argv[1]
    show_raw = "--raw" in sys.argv

    with open(filepath, "rb") as f:
        # File header
        magic = f.read(8)
        if magic != b"btsnoop\x00":
            print(f"Not a btsnoop file: {magic!r}")
            sys.exit(1)
        version, datalink = struct.unpack(">II", f.read(8))
        print(f"btsnoop v{version}, datalink={datalink}")

        records = []
        while True:
            hdr = f.read(24)
            if len(hdr) < 24:
                break
            orig_len, incl_len, flags, drops, ts_raw = struct.unpack(">IIIIq", hdr)
            data = f.read(incl_len)
            if len(data) < incl_len:
                break

            # Convert timestamp to seconds since unix epoch
            ts_us = ts_raw - BTSNOOP_EPOCH_DELTA
            ts_sec = ts_us / 1_000_000.0

            # Direction: bit 0 of flags
            is_received = (flags & 1) == 1
            direction = "RX" if is_received else "TX"

            records.append((ts_sec, direction, data))

        print(f"Total HCI records: {len(records)}")

    # Scan all records for MDN frames
    all_frames = []
    for ts, direction, data in records:
        # Scan the entire HCI packet for 0x5A frames
        frames = scan_for_mdn_frames(data)
        for frame in frames:
            all_frames.append((ts, direction, frame))

    print(f"MDN frames found: {len(all_frames)}")
    print()

    # Deduplicate: the same frame can appear in both the L2CAP reassembly and the
    # final RFCOMM payload. Dedupe by (timestamp, raw_hex).
    seen_keys = set()
    unique_frames = []
    for ts, d, f in all_frames:
        key = (round(ts, 3), f["raw"].hex())
        if key not in seen_keys:
            seen_keys.add(key)
            unique_frames.append((ts, d, f))

    print(f"Unique MDN frames: {len(unique_frames)}")
    print()

    # Normalize timestamps to relative
    if unique_frames:
        t0 = unique_frames[0][0]
    else:
        t0 = 0

    # Print timeline
    print(f"{'Rel.Time':>10s}  {'Dir':3s}  {'Cmd':5s}  {'Name':16s}  Description")
    print("=" * 100)

    prev_ts = None
    for ts, d, f in unique_frames:
        rel = ts - t0
        if prev_ts is not None and rel - prev_ts > 5.0:
            gap = rel - prev_ts
            print(f"{'':>10s}  {'':3s}  {'':5s}  {'--- gap ---':16s}  {gap:.1f}s")
        desc = describe(f, d)
        print(f"{rel:10.3f}  {desc}")
        if show_raw:
            print(f"{'':>10s}  raw: {f['raw'].hex()}")
        prev_ts = rel

    # Summary
    print()
    print("=" * 80)
    print("COMMAND SUMMARY")
    print("=" * 80)
    counts = {}
    for ts, d, f in unique_frames:
        key = (f["svc"], f["cmd"], d)
        counts[key] = counts.get(key, 0) + 1
    for (svc, cmd, d), count in sorted(counts.items()):
        name = NAMES.get((svc, cmd), "???")
        print(f"  [{d}] {svc:02X}:{cmd:02X}  {name:20s}  x{count}")

    # Dump unknown commands with full TLV detail
    print()
    print("=" * 80)
    print("UNKNOWN / NEW COMMANDS (full TLV dump)")
    print("=" * 80)
    seen_unknown = set()
    for ts, d, f in unique_frames:
        name = NAMES.get((f["svc"], f["cmd"]), "???")
        if name == "???" or name.startswith("UNKNOWN"):
            rel = ts - t0
            cmd_key = (f["svc"], f["cmd"])
            if cmd_key not in seen_unknown:
                seen_unknown.add(cmd_key)
                print(f"\n  First seen at {rel:.3f}s [{d}]")
            print(f"  {rel:10.3f} [{d}] {f['svc']:02X}:{f['cmd']:02X}")
            for tag, val in f["tlvs"]:
                if len(val) <= 32:
                    print(f"             T{tag:02X} ({len(val):3d}B): {val.hex()}")
                else:
                    print(f"             T{tag:02X} ({len(val):3d}B): {val[:32].hex()}...")
            print(f"             raw: {f['raw'].hex()}")


if __name__ == "__main__":
    main()
