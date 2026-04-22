# Freebuddies Implementation Roadmap

Based on the Huawei FreeBuds 4 SPP Protocol Reference.

## Phase 1: Core Protocol Logic (Pure Kotlin)
- [x] **CRC16-XModem Implementation**: Implement the checksum logic to validate incoming frames and sign outgoing ones.
- [x] **TLV (Type-Length-Value) Engine**:
    - [x] Create a `Tlv` data class.
    - [x] Implement `parseTlvs(ByteArray)` for incoming payloads.
    - [x] Implement `encodeTlvs(List<Tlv>)` for outgoing commands.
- [x] **Frame Processing**:
    - [x] Implement `Frame` data class to represent the full Huawei MDN packet.
    - [x] Create `FrameReader`: A stateful byte-stream parser that handles split/concatenated packets.
    - [x] Create `FrameBuilder`: To construct bytes from service, command, and TLV parameters.

**Learnings from Phase 1:**
- Confirmed the "canonical formula" for length: `length = 3 + len(TLV bytes)`.
- CRC16-XModem is calculated over everything from the magic byte `0x5A` up to the last byte of the TLVs.
- Implemented `FrameReader` with a `ByteArrayOutputStream` to safely handle stream fragmentation, which is common in RFCOMM.

## Phase 2: Domain Models & Decoders
- [x] **Battery Status**: Implement `BatteryStatus` parser for `(0x01, 0x08)` and `(0x01, 0x27)`.
- [x] **Device Info**: Implement `DeviceInfo` parser for `(0x2B, 0x0A)`.
- [x] **In-Ear State**: Implement `InEarState` parser for `(0x2B, 0x25)`.
- [x] **ANC/Sound Control**:
    - [x] Implement `SoundControl` state model (Enabled/Mode).
    - [x] Map the ANC intensity matrix for `(0x2B, 0x2A)`.

**Learnings from Phase 2:**
- In-ear state uses non-contiguous tags (01 and 04) in its dedicated notification `(0x2B, 0x25)`.
- Sound control tags vary between command types: Tag 01 for writing `0x5D`, Tag 02 for reading/notification `0x5E`.
- `DeviceInfo` fields are standard ASCII strings.

## Phase 3: Bluetooth Connectivity (Android Service)
- [x] **Permission Handling**: Implement runtime requests for `BLUETOOTH_CONNECT` and `BLUETOOTH_SCAN`.
- [x] **Device Discovery**: Logic to find the paired FreeBuds 4 by name or model code (`T0022`).
- [x] **RFCOMM Connection Management**:
    - [x] Socket lifecycle (Connect/Disconnect/Reconnect).
    - [x] Background thread for blocking `InputStream.read()`.
    - [x] Thread-safe `OutputStream.write()` queue (via Coroutines).
- [x] **Protocol Dispatcher**: Route incoming `(svc, cmd)` pairs to the correct state updates.

**Learnings from Phase 3:**
- Standard RFCOMM socket `connect()` and `read()` are blocking, necessitating `Dispatchers.IO` and a dedicated loop.
- `StateFlow` simplifies data flow from the protocol parser to the UI.
- Initial state sync (battery and ANC) should be requested immediately after a successful connection.

## Phase 4: UI & State Integration
- [x] **State Repository**:
    - [x] Replace mock `HuaweiFreeBudsManager` with a `StateFlow`-based repository in `FreeBudsViewModel`.
    - [x] Expose connection status, battery levels, and ANC mode.
- [x] **Homepage Wiring**:
    - [x] Connect Noise Control chips to real `(0x2B, 0x5D)` writes.
    - [ ] Connect Find Device buttons (Research actual command IDs for "Find my buds" sound).
    - [x] Display live battery updates from the buds.
- [x] **Settings Screen**:
    - [ ] Implement ANC cycle preference (`0x2B, 0x18/19`).
    - [x] Show serial number and firmware version from `DeviceInfo`.

**Learnings from Phase 4:**
- `collectAsStateWithLifecycle` prevents flow collection when the app is in the background, saving battery and preventing unnecessary Bluetooth traffic.
- Used `flatMapLatest` in the ViewModel to dynamically bind UI observers to the current `FreeBudsManager` instance.
- Verified that `DeviceInfo` strings need to be parsed as ASCII for correct display.

## Phase 5: Reliability & Polish
- [ ] **Auto-Reconnect**: Handle Bluetooth drops or buds going into the case.
- [ ] **Error UI**: Show friendly messages when the socket fails or permissions are denied.
- [ ] **Background Monitoring**: Optional Foreground Service to show battery in a notification.
- [ ] **Edge Cases**: Verify behavior with single-bud usage.
