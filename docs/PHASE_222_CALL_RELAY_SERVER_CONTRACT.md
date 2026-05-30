# PHASE 222 — Call Relay Server Contract Audit

## Status

Android client branch: `restore-ui-from-codex`

Relevant Android phases:

- PHASE 220: Harden internet call signaling identity
- PHASE 221: Call relay server sync audit
- PHASE 222: Call relay server contract audit

## Audit Finding

No backend relay/server source implementation was found inside this Android repository for the following endpoints:

- `POST /relay/send`
- `GET /relay/inbox/{globalId}`
- `POST /session/prepare-route`
- `POST /session/validate-route`
- `POST /session/heartbeat`

The Android client currently calls these endpoints from `OnlineFallbackTransport` and the call path uses:

```text
CallSessionActivity
→ CallSignalQueue
→ GhalbitCallManager.dispatchSignalEvent()
→ InternetRelaySignalingChannel
→ OnlineFallbackTransport.sendCallSignalViaInternet()
→ POST /relay/send
```

This document is the required backend contract so the relay server can be implemented or audited without guessing Android payload semantics.

---

## Core Requirements

The server must support delayed, intermittent, and low-signal operation. A call signal must not be dropped just because the target is offline at send time.

The server must:

1. Accept signed call relay payloads from Android.
2. Store call signaling events by `targetGlobalId`.
3. Return pending call signaling events through the inbox endpoint.
4. Preserve `callId` across invite, accept, offer, answer, ICE, and end events.
5. Preserve all metadata needed by the receiving Android client.
6. Expire stale call events safely.
7. Log accept/reject/fetch behavior clearly.

---

## Event Types

The server must accept and store these call signal types:

```text
CALL_INVITE
CALL_ACCEPT
CALL_END
CALL_WEBRTC_OFFER
CALL_WEBRTC_ANSWER
CALL_WEBRTC_ICE
VOICE_PROBE
VOICE_PROBE_ACK
VOICE_HELLO
VOICE_HELLO_ACK
VOICE_TRANSPORT_PROBE
VOICE_TRANSPORT_ACK
VOICE_STREAM_START
VOICE_STREAM_ACTIVE_ACK
VOICE_HEARTBEAT
VOICE_STREAM_END
```

At minimum, these must work for internet call signaling:

```text
CALL_INVITE
CALL_ACCEPT
CALL_END
CALL_WEBRTC_OFFER
CALL_WEBRTC_ANSWER
CALL_WEBRTC_ICE
```

---

## POST /relay/send

### Purpose

Accepts chat, receipts, SOS, and call signaling events. For call signaling, the server must store the event for the target global identity.

### Request Headers

```http
Content-Type: application/json
```

### Android Call Request Shape

Android may send a wrapper like this:

```json
{
  "type": "CALL_INVITE",
  "payload": "{...json string...}"
}
```

or a richer relay object depending on the caller path. The server must support both:

1. Top-level `type` + stringified `payload`
2. Top-level metadata fields

### Required Call Payload Fields

The server must normalize call payloads into this shape:

```json
{
  "eventId": "CALL_INVITE-call-123-1700000000000",
  "type": "CALL_INVITE",
  "callId": "call-123",
  "signalType": "CALL_INVITE",
  "senderNodeId": "node-a",
  "senderGlobalId": "global-a",
  "sourceNodeId": "node-a",
  "sourceGlobalId": "global-a",
  "sourcePublicKeyHash": "hash-a",
  "targetNodeId": "node-b",
  "targetGlobalId": "global-b",
  "targetPublicKeyHash": "hash-b",
  "targetDisplayName": "Peer B",
  "routeType": "INTERNET_RELAY",
  "routeHint": "https://relay.example.com",
  "incoming": false,
  "payload": "original signaling payload or SDP/ICE body",
  "createdAt": 1700000000000,
  "expiresAt": 1700000300000
}
```

### Required Fields

For call signaling, reject or queue-with-warning if these are missing:

```text
callId
signalType or type
targetGlobalId
sourceGlobalId or senderGlobalId
createdAt
```

### Recommended Expiry

```text
CALL_INVITE: 60 seconds
CALL_ACCEPT: 60 seconds
CALL_END: 5 minutes
CALL_WEBRTC_OFFER: 2 minutes
CALL_WEBRTC_ANSWER: 2 minutes
CALL_WEBRTC_ICE: 2 minutes
VOICE_* control: 60 seconds
```

### Success Response

```json
{
  "ok": true,
  "status": "ACCEPTED",
  "messageId": "CALL_INVITE-call-123-1700000000000",
  "eventId": "CALL_INVITE-call-123-1700000000000",
  "callId": "call-123",
  "targetGlobalId": "global-b",
  "expiresAt": 1700000300000
}
```

### Failure Responses

Missing call id:

```json
{
  "ok": false,
  "status": "REJECTED",
  "error": "missing_call_id"
}
```

Missing target:

```json
{
  "ok": false,
  "status": "REJECTED",
  "error": "missing_target_global_id"
}
```

Unsupported signal:

```json
{
  "ok": false,
  "status": "REJECTED",
  "error": "unsupported_call_signal_type"
}
```

Temporary storage failure:

```json
{
  "ok": false,
  "status": "RETRYABLE",
  "error": "storage_unavailable"
}
```

### Required Server Logs

```text
GHALBIT-CALL-SERVER received type=<type> callId=<callId> source=<sourceGlobalId> target=<targetGlobalId>
GHALBIT-CALL-SIGNAL missingCallId source=<sourceGlobalId> target=<targetGlobalId>
GHALBIT-CALL-SIGNAL missingTargetGlobalId callId=<callId> source=<sourceGlobalId>
GHALBIT-CALL-SIGNAL relayAccepted type=<type> callId=<callId> target=<targetGlobalId>
GHALBIT-CALL-SIGNAL relayRejected type=<type> callId=<callId> reason=<error>
```

---

## GET /relay/inbox/{globalId}

### Purpose

Returns pending messages, receipts, edits, deletes, and call signaling events for a global identity.

The Android client already fetches inbox for chat relay. For call support, the server must include call events in a field that the client can detect and route into the existing `NEW_MESH_PACKET` path.

### Response Shape

```json
{
  "ok": true,
  "globalId": "global-b",
  "messages": [],
  "receipts": [],
  "callSignals": [
    {
      "eventId": "CALL_INVITE-call-123-1700000000000",
      "type": "CALL_INVITE",
      "callId": "call-123",
      "signalType": "CALL_INVITE",
      "senderNodeId": "node-a",
      "senderGlobalId": "global-a",
      "sourceNodeId": "node-a",
      "sourceGlobalId": "global-a",
      "sourcePublicKeyHash": "hash-a",
      "targetNodeId": "node-b",
      "targetGlobalId": "global-b",
      "targetPublicKeyHash": "hash-b",
      "routeType": "INTERNET_RELAY",
      "routeHint": "https://relay.example.com",
      "payload": "{...}",
      "createdAt": 1700000000000,
      "expiresAt": 1700000300000
    }
  ]
}
```

### Delivery Behavior

The server may either:

1. Keep events until explicit ACK support is added, or
2. Mark events as delivered after inbox fetch and retain briefly for duplicate-safe replay.

Recommended initial behavior:

```text
Return call events for up to 60 seconds after first fetch.
Deduplicate by eventId on client/server.
Expire by expiresAt.
```

### Required Server Logs

```text
GHALBIT-CALL-INBOX fetch globalId=<globalId> count=<count>
GHALBIT-CALL-INBOX delivered eventId=<eventId> type=<type> callId=<callId>
GHALBIT-CALL-INBOX expired eventId=<eventId> type=<type> callId=<callId>
```

---

## POST /session/prepare-route

### Purpose

Coordinates a prepared relay route for call/audio sessions before full signaling begins.

### Request

```json
{
  "sessionId": "call-123",
  "peerGlobalId": "global-b",
  "primaryRoute": "LOCAL_MESH_DIRECT",
  "senderGlobalId": "global-a"
}
```

### Success Response

```json
{
  "ok": true,
  "ready": true,
  "relaySessionId": "relay-call-123",
  "relayUrl": "https://relay.example.com",
  "routeToken": "opaque-route-token",
  "expiresAt": 1700000600000,
  "recommendedMode": "AUTO_HYBRID",
  "healthScore": 75
}
```

### Failure Response

```json
{
  "ok": false,
  "ready": false,
  "status": "RETRYABLE",
  "error": "peer_not_online",
  "recommendedMode": "MESH_ONLY",
  "healthScore": 0
}
```

### Required Logs

```text
GHALBIT-ROUTE-COORD prepare sessionId=<sessionId> sender=<senderGlobalId> peer=<peerGlobalId>
GHALBIT-ROUTE-COORD prepared sessionId=<sessionId> relaySessionId=<relaySessionId> ready=<true|false>
```

---

## POST /session/validate-route

### Purpose

Validates a prepared route token/session before using it for call relay.

### Request

```json
{
  "sessionId": "call-123",
  "peerGlobalId": "global-b",
  "relaySessionId": "relay-call-123",
  "relayUrl": "https://relay.example.com",
  "routeToken": "opaque-route-token",
  "senderGlobalId": "global-a"
}
```

### Response

```json
{
  "ok": true,
  "ready": true,
  "relaySessionId": "relay-call-123",
  "relayUrl": "https://relay.example.com",
  "routeToken": "opaque-route-token",
  "expiresAt": 1700000600000,
  "recommendedMode": "AUTO_HYBRID",
  "healthScore": 80
}
```

### Error Response

```json
{
  "ok": false,
  "ready": false,
  "status": "INVALID_ROUTE_TOKEN",
  "error": "invalid_route_token",
  "recommendedMode": "MESH_ONLY",
  "healthScore": 0
}
```

### Required Logs

```text
GHALBIT-ROUTE-COORD validate sessionId=<sessionId> relaySessionId=<relaySessionId> ready=<true|false>
```

---

## POST /session/heartbeat

### Purpose

Keeps a prepared call relay route alive while the session is active.

### Request

```json
{
  "sessionId": "call-123",
  "peerGlobalId": "global-b",
  "relaySessionId": "relay-call-123",
  "senderGlobalId": "global-a"
}
```

### Response

```json
{
  "ok": true,
  "ready": true,
  "relaySessionId": "relay-call-123",
  "relayUrl": "https://relay.example.com",
  "routeToken": "opaque-route-token",
  "expiresAt": 1700000660000,
  "recommendedMode": "AUTO_HYBRID",
  "healthScore": 78
}
```

### Required Logs

```text
GHALBIT-ROUTE-COORD heartbeat sessionId=<sessionId> relaySessionId=<relaySessionId> ready=<true|false>
```

---

## Call Signal Storage Rules

### Storage Key

Recommended key:

```text
callSignal:<targetGlobalId>:<callId>:<eventId>
```

### Deduplication

Deduplicate by:

```text
eventId
```

If eventId is missing, derive:

```text
<signalType>-<callId>-<sourceGlobalId>-<createdAt>
```

### Ordering

For each `callId`, preserve event order by `createdAt`:

```text
CALL_INVITE
CALL_ACCEPT
CALL_WEBRTC_OFFER
CALL_WEBRTC_ANSWER
CALL_WEBRTC_ICE
CALL_END
```

ICE may appear multiple times.

### Expiration

Expire stale events by `expiresAt`. Do not return expired events to clients.

---

## Android Compatibility Notes

Android expects relay post results compatible with:

```json
{
  "ok": true,
  "status": "ACCEPTED",
  "messageId": "...",
  "expiresAt": 1700000000000
}
```

Android treats non-2xx or `{ "ok": false }` as send failure and keeps the signal queued with backoff.

Android logs expected after server compatibility:

```text
GHALBIT-CALL-SERVER send type=<type> callId=<callId>
GHALBIT-CALL-SERVER result type=<type> callId=<callId> ok=true
GHALBIT-CALL-INBOX received type=<type> callId=<callId>
GHALBIT-CALL-OFFER received callId=<callId>
GHALBIT-CALL-ANSWER received callId=<callId>
GHALBIT-CALL-ICE received callId=<callId>
```

---

## Minimal Backend Pseudocode

```ts
app.post('/relay/send', async (req, res) => {
  const normalized = normalizeRelayPayload(req.body)
  if (isCallSignal(normalized.type)) {
    if (!normalized.callId) return res.status(400).json({ ok: false, status: 'REJECTED', error: 'missing_call_id' })
    if (!normalized.targetGlobalId) return res.status(400).json({ ok: false, status: 'REJECTED', error: 'missing_target_global_id' })
    await storeCallSignal(normalized.targetGlobalId, normalized)
    return res.json({
      ok: true,
      status: 'ACCEPTED',
      messageId: normalized.eventId,
      eventId: normalized.eventId,
      callId: normalized.callId,
      targetGlobalId: normalized.targetGlobalId,
      expiresAt: normalized.expiresAt
    })
  }
  return handleNormalRelayMessage(req, res)
})

app.get('/relay/inbox/:globalId', async (req, res) => {
  const globalId = req.params.globalId
  const messages = await loadMessages(globalId)
  const receipts = await loadReceipts(globalId)
  const callSignals = await loadCallSignals(globalId)
  return res.json({ ok: true, globalId, messages, receipts, callSignals })
})
```

---

## Verification Checklist

### Android Client

- [ ] `git pull`
- [ ] `git status` clean
- [ ] `./gradlew.bat assembleDebug --no-daemon` succeeds
- [ ] `GHALBIT-CALL-SERVER send` appears when internet call signal is sent
- [ ] `GHALBIT-CALL-SERVER result ok=true` appears when server accepts
- [ ] `GHALBIT-CALL-INBOX received` appears on receiver

### Backend Server

- [ ] `POST /relay/send` accepts `CALL_INVITE`
- [ ] `POST /relay/send` accepts `CALL_ACCEPT`
- [ ] `POST /relay/send` accepts `CALL_WEBRTC_OFFER`
- [ ] `POST /relay/send` accepts `CALL_WEBRTC_ANSWER`
- [ ] `POST /relay/send` accepts `CALL_WEBRTC_ICE`
- [ ] `GET /relay/inbox/{globalId}` returns pending `callSignals`
- [ ] Session prepare/validate/heartbeat endpoints return `PreparedRouteResponse` compatible JSON

---

## Conclusion

This Android repository does not currently contain the relay backend implementation. The Android call relay client path is present, and this contract defines the required backend behavior for synchronized call signaling, prepared route coordination, and low-signal relay operation.
