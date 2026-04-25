#!/usr/bin/env python3
"""
decode_session.py — Parse a Wireshark text-export btsnoop log and extract
decoded Huawei MDN protocol frames from SPP traffic.

Usage:
    python3 decode_session.py session_log.txt
    python3 decode_session.py session_log.txt --timeline actions.md
"""

import re
import sys
import struct
from dataclasses import dataclass


# ---------------------------------------------------------------------------
# CRC16-XModem (for validation)
# ---------------------------------------------------------------------------

def crc16_xmodem(data: bytes) -> int:
    crc = 0x0000
    for b in data:
        crc ^= b << 8
        for _ in range(8):
            if crc & 0x8000:
                crc = ((crc << 1) ^ 0x1021) & 0xFFFF
            else:
                crc = (crc << 1) & 0xFFFF
    return crc


# ---------------------------------------------------------------------------
# Decode Wireshark escaped-string format to raw bytes
# ---------------------------------------------------------------------------

def decode_wireshark_string(s: str) -> bytes:
    """Decode a Wireshark-style C-escaped string to bytes.

    Handles: \\NNN (octal), \\xNN (hex), \\n \\r \\t \\a \\b \\f \\v \\\\
    and literal ASCII characters.
    """
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
                result.append(int(s[i+2:i+4], 16))
                i += 4
            elif nxt.isdigit():
                # Octal: consume up to 3 octal digits
                end = i + 2
                while end < len(s) and end < i + 5 and s[end].isdigit() and int(s[end]) < 8:
                    end += 1
                # Take at most 3 octal digits after the backslash
                octal_str = s[i+1:min(end, i+4)]
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
# TLV and Frame parsing
# ---------------------------------------------------------------------------

@dataclass
class Tlv:
    tag: int
    value: bytes

    def __repr__(self):
        hex_val = self.value.hex().upper() if self.value else "(empty)"
        return f"T{self.tag:02X}={hex_val}"


@dataclass
class Frame:
    service: int
    command: int
    tlvs: list
    raw: bytes
    crc_ok: bool

    @property
    def cmd_id(self):
        return f"{self.service:02X}:{self.command:02X}"

    def __repr__(self):
        tlv_str = " ".join(str(t) for t in self.tlvs)
        crc = "OK" if self.crc_ok else "BAD"
        return f"({self.cmd_id}) [{crc}] {tlv_str}"


def parse_tlvs(payload: bytes) -> list:
    tlvs = []
    i = 0
    while i + 2 <= len(payload):
        tag = payload[i]
        length = payload[i + 1]
        if i + 2 + length > len(payload):
            break
        value = payload[i + 2 : i + 2 + length]
        tlvs.append(Tlv(tag, value))
        i += 2 + length
    return tlvs


def parse_frame(data: bytes) -> Frame | None:
    if len(data) < 9 or data[0] != 0x5A:
        return None
    length = (data[1] << 8) | data[2]
    total = 1 + 2 + length + 2
    if len(data) < total:
        return None
    frame_bytes = data[:total]
    expected_crc = (data[total - 2] << 8) | data[total - 1]
    computed_crc = crc16_xmodem(data[: total - 2])
    svc = data[4]
    cmd = data[5]
    tlv_bytes = data[6 : total - 2]
    return Frame(
        service=svc,
        command=cmd,
        tlvs=parse_tlvs(tlv_bytes),
        raw=frame_bytes,
        crc_ok=(expected_crc == computed_crc),
    )


# ---------------------------------------------------------------------------
# Known command names
# ---------------------------------------------------------------------------

KNOWN_COMMANDS = {
    (0x01, 0x07): "GET_DEVICE_INFO",
    (0x01, 0x08): "GET_BATTERY / BATTERY_REPLY",
    (0x01, 0x27): "BATTERY_PUSH",
    (0x2B, 0x04): "SET_ANC / ANC_ACK",
    (0x2B, 0x0A): "DEVICE_INFO",
    (0x2B, 0x19): "GET_ANC_CYCLE",
    (0x2B, 0x18): "SET_ANC_CYCLE",
    (0x2B, 0x25): "IN_EAR_STATE",
    (0x2B, 0x2A): "ANC_MODE_READ",
    (0x2B, 0x31): "AUDIO_SOURCE",
    (0x2B, 0x36): "PLAYBACK_STATE",
    (0x2B, 0x4B): "VOLUME_GESTURE",
    (0x2B, 0x5D): "FIND_MY_BUDS_WRITE",
    (0x2B, 0x5E): "SOUND_CTRL_NOTIFY",
    (0x2B, 0x5F): "UNKNOWN_5F",
    (0x2B, 0x7F): "UNKNOWN_7F",
    (0x2B, 0xAC): "UNKNOWN_AC",
}


def describe_frame(frame: Frame, direction: str) -> str:
    name = KNOWN_COMMANDS.get((frame.service, frame.command), "???")
    extra = ""

    if frame.cmd_id == "2B:2A":
        # ANC read response
        t1 = next((t for t in frame.tlvs if t.tag == 0x01), None)
        if t1 and len(t1.value) >= 2:
            intensity, mode = t1.value[0], t1.value[1]
            mode_name = {0: "OFF", 1: "NC", 2: "Awareness"}.get(mode, f"?{mode}")
            if mode == 1:
                int_name = {0: "general", 1: "cozy", 2: "ultra", 3: "dynamic"}.get(intensity, f"?{intensity}")
                extra = f" → {mode_name} ({int_name})"
            elif mode == 2:
                extra = f" → Awareness ({'voice' if intensity == 1 else 'normal'})"
            else:
                extra = f" → {mode_name}"

    elif frame.cmd_id == "2B:25":
        # In-ear state
        t1 = next((t for t in frame.tlvs if t.tag == 0x01), None)
        t2 = next((t for t in frame.tlvs if t.tag == 0x02), None)
        t3 = next((t for t in frame.tlvs if t.tag == 0x03), None)
        t4 = next((t for t in frame.tlvs if t.tag == 0x04), None)
        v1 = t1.value[0] if t1 and t1.value else 0
        v2 = t2.value[0] if t2 and t2.value else 0
        v3 = t3.value[0] if t3 and t3.value else 0
        v4 = t4.value[0] if t4 and t4.value else 0

        def wear(ear, case):
            if ear: return "in-ear"
            if case: return "in-case"
            return "out"

        extra = f" → L={wear(v1, v3)} R={wear(v2, v4)}"

    elif frame.cmd_id in ("01:08", "01:27"):
        # Battery
        t2 = next((t for t in frame.tlvs if t.tag == 0x02), None)
        t3 = next((t for t in frame.tlvs if t.tag == 0x03), None)
        t5 = next((t for t in frame.tlvs if t.tag == 0x05), None)
        if t2 and len(t2.value) == 3:
            extra = f" → L={t2.value[0]}% R={t2.value[1]}% C={t2.value[2]}%"
        if t3 and len(t3.value) == 3:
            charging = []
            if t3.value[0]: charging.append("L")
            if t3.value[1]: charging.append("R")
            if t3.value[2]: charging.append("C")
            if charging:
                extra += f" charging=[{','.join(charging)}]"
        if t5 and len(t5.value) == 2:
            extra += f" wear=[{'in' if t5.value[0] else 'out'},{'in' if t5.value[1] else 'out'}]"

    elif frame.cmd_id == "2B:5E":
        # Sound control / ringing notification
        t1 = next((t for t in frame.tlvs if t.tag == 0x01), None)
        t2 = next((t for t in frame.tlvs if t.tag == 0x02), None)
        parts = []
        if t1:
            parts.append(f"snd={t1.value.hex()}")
        if t2 and len(t2.value) >= 2:
            side = "left" if t2.value[0] == 0 else "right"
            action = "ringing" if t2.value[1] == 0 else "stopped"
            parts.append(f"ring:{side}={action}")
        extra = " → " + ", ".join(parts) if parts else ""

    elif frame.cmd_id == "2B:5D":
        # Find my buds write
        t1 = next((t for t in frame.tlvs if t.tag == 0x01), None)
        if t1 and len(t1.value) >= 2:
            side = "left" if t1.value[0] == 0 else "right"
            action = "start" if t1.value[1] == 0 else "stop"
            extra = f" → {side} {action}"

    return f"[{direction}] {frame.cmd_id} {name}{extra}  |  {frame}"


# ---------------------------------------------------------------------------
# Log line parsing
# ---------------------------------------------------------------------------

# Regex to match lines with inline SPP data (the "Z..." escaped strings)
LINE_RE = re.compile(
    r"(\d+)\s+"              # packet number
    r"([\d.]+)\s+"           # timestamp
    r"(.+?)\s{2,}"           # source
    r"(.+?)\s{2,}"           # destination
    r"SPP\s+"                # protocol
    r"(\d+)\s+"              # length
    r'(Sent|Rcvd)\s+'        # direction
    r'(?:UIH Channel=\d+\s*(?:UID\s*)?)?'
    r'"(.*)"'                # data in quotes (optional)
)

# Simpler regex for SPP lines without inline data
SPP_LINE_RE = re.compile(
    r"(\d+)\s+"
    r"([\d.]+)\s+"
    r"(.+?)\s{2,}"
    r"(.+?)\s{2,}"
    r"SPP\s+"
    r"(\d+)\s+"
    r"(Sent|Rcvd)"
)


def process_log(filepath: str):
    decoded_frames = []
    spp_lines = []

    with open(filepath) as f:
        for line in f:
            line = line.rstrip()

            # Try to match SPP line with inline data
            m = LINE_RE.match(line)
            if m:
                pkt, ts, src, dst, size, direction, data_str = m.groups()
                raw_bytes = decode_wireshark_string(data_str)
                direction = "TX" if direction == "Sent" else "RX"

                # Try to parse one or more frames from the bytes
                offset = 0
                while offset < len(raw_bytes):
                    if raw_bytes[offset] != 0x5A:
                        offset += 1
                        continue
                    frame = parse_frame(raw_bytes[offset:])
                    if frame:
                        desc = describe_frame(frame, direction)
                        decoded_frames.append((float(ts), desc, frame))
                        offset += len(frame.raw)
                    else:
                        offset += 1

                spp_lines.append((float(ts), direction, int(size), "decoded"))
                continue

            # Match SPP line without inline data
            m2 = SPP_LINE_RE.match(line)
            if m2:
                pkt, ts, src, dst, size, direction = m2.groups()
                direction = "TX" if direction == "Sent" else "RX"
                spp_lines.append((float(ts), direction, int(size), "opaque"))

    return decoded_frames, spp_lines


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    if len(sys.argv) < 2:
        print(f"Usage: {sys.argv[0]} <wireshark_log.txt>")
        sys.exit(1)

    filepath = sys.argv[1]
    decoded, all_spp = process_log(filepath)

    print("=" * 80)
    print("DECODED SPP FRAMES")
    print("=" * 80)
    for ts, desc, frame in decoded:
        print(f"  {ts:>12.3f}  {desc}")
        print(f"               raw: {frame.raw.hex().upper()}")

    print()
    print("=" * 80)
    print("ALL SPP LINES (including opaque)")
    print("=" * 80)
    for ts, direction, size, kind in all_spp:
        marker = "***" if kind == "opaque" and size > 26 else "   "
        print(f"  {ts:>12.3f}  [{direction}] {size:3d}B  {kind}  {marker}")

    print()
    print("=" * 80)
    print("OPAQUE FRAMES (potential unknown commands)")
    print("=" * 80)
    for ts, direction, size, kind in all_spp:
        if kind == "opaque" and size > 14:
            print(f"  {ts:>12.3f}  [{direction}] {size:3d}B")


if __name__ == "__main__":
    main()
