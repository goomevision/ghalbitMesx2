PHASE 302D - Virtual HP B Call Server Proof

Goal:
- Prove the operator server can deliver call signaling from virtual HP B to HP A.
- Keep the proof server-first and debug-triggerable, similar to presence, SOS, and chat.

Debug actions:
- `com.ghalbitnet.meshx2.debug.RUN_VIRTUAL_CALL_SERVER_START`
- `com.ghalbitnet.meshx2.debug.RUN_VIRTUAL_CALL_SERVER_END`

Proof flow:
1. `RUN_VIRTUAL_CALL_SERVER_START` posts `/session/start` with:
   - `sourceGlobalId=GX-VIRTUAL-HP-B`
   - `sourceNodeId=virtual-peer-b`
   - `targetGlobalId=<HP A runtime globalId>`
2. The probe immediately triggers relay inbox sync on HP A.
3. Expected logs on HP A:
   - `GHALBIT-VIRTUAL-PEER: CALL_START_OK callId=...`
   - `GHALBIT-CALL-INBOX: received type=CALL_START callId=... source=virtual-peer-b`
   - normal incoming call handling logs from the app
4. `RUN_VIRTUAL_CALL_SERVER_END` posts `/session/end` for the last stored virtual callId.
5. Expected logs on HP A:
   - `GHALBIT-VIRTUAL-PEER: CALL_END_OK callId=...`
   - `GHALBIT-CALL-INBOX: received type=CALL_END callId=... source=virtual-peer-b`

Purpose:
- This keeps the operator server in the middle of the call proof.
- It helps separate:
  - server/session signaling issues
  - inbox/sync issues
  - call UI handling issues
  - realtime audio issues

Proven runtime result:
- Runtime target globalId on HP A: `GX-3762AC7DFBC7`
- `RUN_VIRTUAL_CALL_SERVER_START`:
  - `GHALBIT-VIRTUAL-PEER: CALL_START_OK callId=virt-server-1780438133443 target=GX-3762AC7DFBC7 code=200`
  - `GHALBIT-CALL-INBOX: received type=CALL_START callId=virt-server-1780438133443 source=GX-VIRTUAL-HP-B`
- `RUN_VIRTUAL_CALL_SERVER_END`:
  - `GHALBIT-VIRTUAL-PEER: CALL_END_OK callId=virt-server-1780438133443 target=GX-3762AC7DFBC7 code=200`
  - `GHALBIT-CALL-INBOX: received type=CALL_END callId=virt-server-1780438133443 source=GX-VIRTUAL-HP-B`

Important client fix:
- The server already enqueued `callSignals`, but the Android client inbox parser only read `messages` and `receipts`.
- The client now maps `callSignals` into `RelayInboxMessage` objects so the existing call inbox path can process:
  - `CALL_START`
  - `CALL_END`
  - and future `CALL_ACCEPT`/`CALL_REJECT`/`CALL_WEBRTC_*` server-first proofs.
