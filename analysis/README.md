# Protocol Analysis Tools

Python scripts for extracting and decoding Huawei FreeBuds SPP protocol frames from Bluetooth HCI logs. Used to reverse-engineer new commands by capturing AI Life traffic.

## Prerequisites

- Python 3.10+
- No external dependencies (stdlib only)

## Capturing a btsnoop log

1. On your Android device, enable **Developer options**
2. Enable **Enable Bluetooth HCI snoop log**
3. Toggle Bluetooth off and on to start a fresh capture
4. Perform the actions you want to analyze in AI Life
5. Pull the log: `adb pull /data/misc/bluetooth/logs/btsnoop_hci.log`

The file location may vary by device and Android version. On Samsung devices, the logs are typically at `/data/misc/bluetooth/logs/`.

## Scripts

### parse_btsnoop.py (primary tool)

Parses btsnoop HCI binary files directly. Scans every HCI ACL packet for valid Huawei MDN protocol frames (0x5A magic + CRC16-XModem validation), decodes TLVs, and outputs a human-readable timeline.

```bash
# Basic usage — decoded timeline + command summary
python3 parse_btsnoop.py btsnoop_hci.log

# Include raw hex for each frame
python3 parse_btsnoop.py btsnoop_hci.log --raw
```

Output includes:
- **Decoded timeline** with relative timestamps, direction (TX/RX), command names, and decoded parameters
- **Command summary** with occurrence counts per command ID
- **Unknown commands** section with full TLV dumps for unrecognized command IDs

Known commands are decoded with human-readable descriptions (ANC mode, battery levels, EQ presets, wear state, etc.). Unknown commands show raw TLV hex.

### decode_session.py

Parses Wireshark text exports (not binary pcap). Useful when you have a Wireshark text-format log instead of a raw btsnoop file.

```bash
python3 decode_session.py wireshark_export.txt
```

Extracts SPP lines, decodes Wireshark's C-escaped string format (`\000`, `\a`, etc.) to hex bytes, and parses MDN frames.

### decode_inline.py

Decodes a pre-extracted list of SPP frames from `spp_inline_frames.txt` format. This is a convenience script for working with manually extracted frames.

```bash
python3 decode_inline.py spp_inline_frames.txt
```

Output includes a decoded timeline, unique command summary, in-ear state sequence, and ANC mode change history.

## Workflow for discovering new commands

1. **Capture**: Enable btsnoop, perform a single known action in AI Life (e.g., toggle one setting), pull the log
2. **Decode**: `python3 parse_btsnoop.py btsnoop_hci.log --raw`
3. **Identify**: Look at the "Unknown commands" section for new command IDs. Correlate timestamps with your actions
4. **Document**: Add the new command to `app/README.md` with its TLV encoding
5. **Implement**: Add the handler to `FreeBudsManager.kt` and wire through to the UI

Performing one action per capture session makes correlation straightforward. For sessions with multiple actions, note the order and approximate timing to match against the timeline.
