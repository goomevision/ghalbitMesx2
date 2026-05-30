# PHASE 223 — Relay Server Discovery Audit

## Status

Android client branch: `restore-ui-from-codex`

Previous server-related documents:

- `docs/PHASE_222_CALL_RELAY_SERVER_CONTRACT.md`

## Discovery Result

Repository discovery was performed for relay/backend/server candidates related to GhalbitNet.

Search terms used:

```text
ghalbit relay server backend api
relay
```

No connected GitHub repository was found that clearly contains the backend relay/server implementation for:

- `POST /relay/send`
- `GET /relay/inbox/{globalId}`
- `POST /session/prepare-route`
- `POST /session/validate-route`
- `POST /session/heartbeat`

The Android repository also does not contain backend route handlers for those endpoints. The Android client only contains the relay client caller logic.

## Conclusion

The relay backend should be treated as **not yet present in the connected repository set**, unless it exists in an unconnected/private repository, VPS folder, or external deployment not visible to this audit.

The next required step is to create the first relay server implementation that follows the PHASE 222 contract.

---

## Recommended New Backend Repository

Recommended repository name:

```text
ghalbit-relay-server
```

Recommended stack:

```text
Node.js
Express
TypeScript
Redis for fast pending inbox / call signal TTL
PostgreSQL optional for durable audit history
Docker
Nginx reverse proxy
HTTPS via Certbot or managed proxy
```

Minimal first version may use only:

```text
Node.js + Express + in-memory Map
```

for local testing, then upgrade to Redis/PostgreSQL before real field testing.

---

## Minimal Server Feature Set

The first relay server must implement:

### Health

```http
GET /health
```

Response:

```json
{
  "ok": true,
  "service": "ghalbit-relay-server",
  "status": "READY"
}
```

### Send Relay Event

```http
POST /relay/send
```

Must accept:

- chat message
- receipt
- call signal
- WebRTC offer/answer/ICE
- route control signal

For PHASE 223, call signal support is the priority.

### Fetch Inbox

```http
GET /relay/inbox/:globalId
```

Must return:

```json
{
  "ok": true,
  "globalId": "global-b",
  "messages": [],
  "receipts": [],
  "callSignals": []
}
```

### Prepared Route Coordinator

```http
POST /session/prepare-route
POST /session/validate-route
POST /session/heartbeat
```

These may initially return a simple ready response while the real coordinator is developed.

---

## Required Call Signal Flow

```text
Caller Android
→ POST /relay/send CALL_INVITE
→ Server stores under targetGlobalId
→ Receiver Android GET /relay/inbox/{targetGlobalId}
→ Receiver gets CALL_INVITE
→ Receiver sends CALL_ACCEPT
→ Caller fetches inbox
→ Caller gets CALL_ACCEPT
→ WebRTC OFFER / ANSWER / ICE are exchanged the same way
```

The same `callId` must be preserved across the entire flow.

---

## Required Server Logs

The first backend must emit these logs:

```text
GHALBIT-RELAY-SERVER health ready
GHALBIT-CALL-SERVER received type=<type> callId=<callId> source=<sourceGlobalId> target=<targetGlobalId>
GHALBIT-CALL-SIGNAL relayAccepted type=<type> callId=<callId> target=<targetGlobalId>
GHALBIT-CALL-SIGNAL relayRejected type=<type> callId=<callId> reason=<reason>
GHALBIT-CALL-INBOX fetch globalId=<globalId> count=<count>
GHALBIT-CALL-INBOX delivered eventId=<eventId> type=<type> callId=<callId>
GHALBIT-ROUTE-COORD prepare sessionId=<sessionId> sender=<senderGlobalId> peer=<peerGlobalId>
GHALBIT-ROUTE-COORD validate sessionId=<sessionId> relaySessionId=<relaySessionId> ready=<true|false>
GHALBIT-ROUTE-COORD heartbeat sessionId=<sessionId> relaySessionId=<relaySessionId> ready=<true|false>
```

---

## Minimal Data Model

### Call Signal

```ts
type CallSignal = {
  eventId: string
  type: string
  callId: string
  signalType: string
  senderNodeId?: string
  senderGlobalId?: string
  sourceNodeId?: string
  sourceGlobalId?: string
  sourcePublicKeyHash?: string
  targetNodeId?: string
  targetGlobalId: string
  targetPublicKeyHash?: string
  targetDisplayName?: string
  routeType?: string
  routeHint?: string
  payload?: unknown
  createdAt: number
  expiresAt: number
}
```

### Inbox Key

```text
relay:inbox:<targetGlobalId>
```

### Dedup Key

```text
relay:event:<eventId>
```

### Session Key

```text
relay:session:<sessionId>
```

---

## Minimal Endpoint Behavior

### POST /relay/send

1. Parse body.
2. Normalize wrapped payload.
3. Detect call signal type.
4. Validate:
   - `callId`
   - `targetGlobalId`
   - `sourceGlobalId` or `senderGlobalId`
5. Generate `eventId` if missing.
6. Store event under target inbox.
7. Return accepted response.

### GET /relay/inbox/:globalId

1. Load inbox events.
2. Remove expired events.
3. Return current messages/receipts/callSignals.
4. Keep call signals briefly after first fetch or deduplicate by `eventId`.

### POST /session/prepare-route

Initial version can return:

```json
{
  "ok": true,
  "ready": true,
  "relaySessionId": "relay-<sessionId>",
  "relayUrl": "https://your-relay-domain",
  "routeToken": "dev-token",
  "expiresAt": 1700000000000,
  "recommendedMode": "AUTO_HYBRID",
  "healthScore": 70
}
```

---

## Environment Variables

Recommended `.env`:

```env
PORT=8080
PUBLIC_RELAY_URL=https://relay.your-domain.com
REDIS_URL=redis://localhost:6379
NODE_ENV=production
CALL_SIGNAL_TTL_SECONDS=120
CALL_INVITE_TTL_SECONDS=60
SESSION_TTL_SECONDS=600
```

---

## Android Configuration Needed

Android must point to the relay server via build config values:

```text
BASE_RELAY_URL=https://relay.your-domain.com
BASE_PRESENCE_URL=https://relay.your-domain.com
INTERNET_RELAY_CONFIGURED=true
```

The exact Gradle/build config source must be verified before field test.

---

## Phase 224 Recommendation

Create a new backend repository:

```text
ghalbit-relay-server
```

with initial files:

```text
package.json
tsconfig.json
src/index.ts
src/routes/relay.ts
src/routes/session.ts
src/store/memoryStore.ts
src/types.ts
Dockerfile
docker-compose.yml
.env.example
README.md
```

First target:

```text
GET /health
POST /relay/send
GET /relay/inbox/:globalId
POST /session/prepare-route
POST /session/validate-route
POST /session/heartbeat
```

---

## Verification Plan

### Server Local

```powershell
npm install
npm run dev
curl http://localhost:8080/health
```

### Android Local/Dev

1. Configure `BASE_RELAY_URL` to server.
2. Run Android app on two devices.
3. Send call invite through relay.
4. Verify logs:

```text
GHALBIT-CALL-SERVER send
GHALBIT-CALL-SERVER result ok=true
GHALBIT-CALL-INBOX received
GHALBIT-CALL-OFFER received
GHALBIT-CALL-ANSWER received
GHALBIT-CALL-ICE received
```

### Backend Logs

Verify:

```text
GHALBIT-CALL-SERVER received
GHALBIT-CALL-SIGNAL relayAccepted
GHALBIT-CALL-INBOX fetch
GHALBIT-CALL-INBOX delivered
```

---

## Final Finding

Android is now ready to call a relay server, but no relay backend repository was discovered in the connected GitHub scope. PHASE 224 should create the first relay server implementation or connect the existing server repository if it exists outside the current GitHub connector scope.
