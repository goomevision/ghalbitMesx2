# Server-First Call Media Analysis Prep

Goal:
- Make current call behavior analyzable without pretending server-call media is already complete.
- Distinguish clearly between:
  - direct mesh audio path
  - virtual diagnostic audio path
  - future operator-server media path

## Current proven state

### Proven

- Presence through operator server
- Chat relay through operator server
- SOS relay through operator server
- Delivered/read receipts through operator server
- Call signaling through operator server:
  - `CALL_START`
  - `CALL_END`
- Full-flow signaling trigger now prepared:
  - `start -> ringing -> accept -> end`
  - `start -> ringing -> reject`

### Not yet proven

- Real-time call media relayed through operator server
- Two-way voice frames carried by server instead of direct mesh socket

## Important code reality

Current voice packet transmission still uses:

- `CallManager.sendVoicePacket(...)`
- `MeshSocketClient.sendBlocking(targetIp, meshPacket)`

This means:

- call signaling can already be server-first
- call media is still primarily direct socket / mesh-first
- if `routeHint` looks like `internet:` or a server URL, the current media layer still does not become a true operator media relay

## Diagnostic hardening added

### New media path logs

- `GHALBIT-CALL-MEDIA-PATH path=virtual_diagnostic ...`
- `GHALBIT-CALL-MEDIA-PATH path=direct_mesh_socket ...`
- `GHALBIT-CALL-MEDIA-PATH path=internet_route_hint_without_media_relay ...`
- `GHALBIT-CALL-MEDIA-PATH path=server_operator_route_hint ...`

These logs now appear from:

- `FullDuplexCallEngine` when capture starts using a specific endpoint
- `CallManager.sendVoicePacket(...)` when a real voice frame is sent
- `OnlineFallbackTransport.sendVoiceFrameViaOperator(...)` as the prepared operator-media contract hook

## Operator media contract preparation

Android now has a non-invasive preparation helper:

- `OnlineFallbackTransport.sendVoiceFrameViaOperator(...)`
- `OperatorMediaContractProbe`
- `OperatorMediaLoopbackProbe`

What it does:

- accepts a real `VoicePacket`
- wraps it into a relay envelope with:
  - `contentType=CALL_AUDIO_FRAME`
  - `payload={callId, sourceNodeId, targetNodeId, sequenceNumber, mode, priority, audioData}`
- sends it through the existing operator relay path
- returns a structured `OperatorMediaSendResult`

What it does **not** do yet:

- it does not replace `CallManager.sendVoicePacket(...)`
- it does not yet switch live calls to server media transport
- it does not yet provide dedicated server-side media playback/forwarding semantics

This is intentional, so current working calls are not destabilized while we prepare the operator-media layer.

## Loopback proof

The app now also has a diagnostic loopback proof:

- `OperatorMediaLoopbackProbe.run(...)`
- `CallSessionActivity` tone diagnostic lab now triggers one debug-only operator loopback sample after voice path becomes active

What it proves:

1. app can send one voice frame through the operator relay contract
2. the same frame can return through `/relay/inbox/{globalId}`
3. the returned payload can be parsed again as a real `VoicePacket`

Expected diagnostic statuses:

- `LOOPBACK_OK`
- `LOOPBACK_NOT_RETURNED`
- `LOOPBACK_PARSE_FAIL`
- `SERVER_NOT_CONFIGURED`

This still does **not** mean live voice playback is fully operator-relayed, but it is a much stronger proof that the server can already carry voice-frame envelopes end-to-end in diagnostic mode.

## Meaning of each path

| Path label | Meaning |
|---|---|
| `virtual_diagnostic` | local virtual loopback for proof/lab |
| `direct_mesh_socket` | realtime audio sent directly to peer host/IP |
| `internet_route_hint_without_media_relay` | route says internet, but media relay is not implemented yet |
| `server_operator_route_hint` | route hint points at server-style target, but still needs real operator media transport implementation to be complete |

## Honest conclusion

The app is now much easier to analyze, but current code still shows:

- signaling can be server-first
- media is not yet a full server-first operator relay

So if a call “connects but voice does not behave perfectly”, we can now tell whether the failure is happening on:

- direct mesh socket path
- virtual diagnostic path
- a route that claims internet/server but still lacks true media relay behavior

## Next safe step

To reach truly analyzable server-mediated call behavior, the next implementation should focus on:

1. a dedicated operator media transport contract for voice frames
2. a clear distinction between signaling success and media transport success
3. RX/TX/PLAY metrics tied to `serverOperator=true/false`
