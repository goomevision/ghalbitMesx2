PHASE 302D - Virtual HP B Call Server Proof

Goal:
- Prove the operator server can deliver call signaling from virtual HP B to HP A.
- Keep the proof server-first and debug-triggerable, similar to presence, SOS, and chat.

Debug actions:
- `com.ghalbitnet.meshx2.debug.RUN_VIRTUAL_CALL_SERVER_START`
- `com.ghalbitnet.meshx2.debug.RUN_VIRTUAL_CALL_SERVER_RINGING`
- `com.ghalbitnet.meshx2.debug.RUN_VIRTUAL_CALL_SERVER_ACCEPT`
- `com.ghalbitnet.meshx2.debug.RUN_VIRTUAL_CALL_SERVER_REJECT`
- `com.ghalbitnet.meshx2.debug.RUN_VIRTUAL_CALL_SERVER_END`
- `com.ghalbitnet.meshx2.debug.RUN_VIRTUAL_CALL_SERVER_FULL_ACCEPT`
- `com.ghalbitnet.meshx2.debug.RUN_VIRTUAL_CALL_SERVER_FULL_REJECT`

Proof flow:
1. `RUN_VIRTUAL_CALL_SERVER_START` posts `/session/start` with:
   - `sourceGlobalId=GX-VIRTUAL-HP-B`
   - `sourceNodeId=virtual-peer-b`
   - `targetGlobalId=<HP A runtime globalId>`
2. The probe immediately triggers relay inbox sync on HP A.
3. Expected logs on HP A:
   - `GHALBIT-VIRTUAL-PEER: CALL_START_OK callId=...`
   - `GHALBIT-CALL-INBOX: received type=CALL_START callId=... source=virtual-peer-b`
4. `RUN_VIRTUAL_CALL_SERVER_RINGING` reuses the last stored callId and posts `/session/ringing`.
5. `RUN_VIRTUAL_CALL_SERVER_ACCEPT` reuses the last stored callId and posts `/session/accept`.
6. `RUN_VIRTUAL_CALL_SERVER_REJECT` reuses the last stored callId and posts `/session/reject`.
7. `RUN_VIRTUAL_CALL_SERVER_END` posts `/session/end` for the last stored virtual callId.

Purpose:
- Keep the operator server in the middle of the call proof.
- Separate:
  - server/session signaling issues
  - inbox/sync issues
  - call UI handling issues
  - realtime audio issues

Already proven runtime:
- Runtime target globalId on HP A: `GX-3762AC7DFBC7`
- `RUN_VIRTUAL_CALL_SERVER_START`:
  - `GHALBIT-VIRTUAL-PEER: CALL_START_OK callId=virt-server-1780438133443 target=GX-3762AC7DFBC7 code=200`
  - `GHALBIT-CALL-INBOX: received type=CALL_START callId=virt-server-1780438133443 source=GX-VIRTUAL-HP-B`
- `RUN_VIRTUAL_CALL_SERVER_END`:
  - `GHALBIT-VIRTUAL-PEER: CALL_END_OK callId=virt-server-1780438133443 target=GX-3762AC7DFBC7 code=200`
  - `GHALBIT-CALL-INBOX: received type=CALL_END callId=virt-server-1780438133443 source=GX-VIRTUAL-HP-B`

Ready in app but not yet separated into dedicated runtime proof:
- `CALL_RINGING`
- `CALL_ACCEPT`
- `CALL_REJECT`

Important client fix:
- The server already enqueued `callSignals`, but the Android client inbox parser originally only read `messages` and `receipts`.
- The client now maps `callSignals` into `RelayInboxMessage` objects so the existing call inbox path can process:
  - `CALL_START`
  - `CALL_END`
  - `CALL_ACCEPT`
  - `CALL_REJECT`
  - `CALL_WEBRTC_*`

Current readiness conclusion:
- Server-first call signaling is already proven for `start/end`.
- Server-first `ringing/accept/reject` is now wired for the same virtual-peer proof path and ready to be exercised without changing the call architecture.
- Full-flow triggers now exist so the sequence can be exercised as one coherent operator-session proof:
  - `start -> ringing -> accept -> end`
  - `start -> ringing -> reject`
