# Server Contract Map

Format: `Endpoint | Method | Request Body | Expected Response | Caller File | Status`

| Endpoint | Method | Request Body | Expected Response | Caller File | Status |
|---|---|---|---|---|---|
| `/identity/register` | POST | callId, displayName, publicKey, deviceIdHash, routeHint, updatedAt | `{ok,status,routeHint,updatedAt,error}` | `identity/IdentityServerClient.kt` | READY_IN_APP |
| `/identity/sync` | POST | sama seperti register | `{ok,status,...}` | `identity/IdentityServerClient.kt` | READY_IN_APP |
| `/identity/lookup/{callId}` | GET | n/a | `{ok, identity:{...}}` | `identity/IdentityServerClient.kt` | READY_IN_APP |
| `/identity/copy-reached-internet` | POST | identity copy payload | `{ok,...}` | `identity/IdentityServerClient.kt` | READY_IN_APP |
| `/identity/route-hint` | POST | callId, routeHint, updatedAt | `{ok,...}` | `identity/IdentityServerClient.kt` | READY_IN_APP |
| `/presence/heartbeat` | POST | globalId, nodeId, publicKeyHash, relayUrl, networkType, online | `{ok,status,online,lastSeen,presence}` | `online/OnlinePresenceManager.kt`, `diagnostics/VirtualPeerPresenceProbe.kt` | PROVEN_RUNTIME |
| `/presence/{targetGlobalId}` | GET | n/a | `{ok,online,lastSeen,presence}` | `online/OnlinePresenceManager.kt`, `diagnostics/VirtualPeerPresenceProbe.kt` | PROVEN_RUNTIME |
| `/relay/send` | POST | envelope chat/call/sos/signature | `{ok,status,messageId,eventId}` | `online/OnlineFallbackTransport.kt`, `diagnostics/VirtualPeerChatProbe.kt`, `diagnostics/VirtualPeerCallSignalProbe.kt` | PROVEN_RUNTIME |
| `/relay/inbox/{globalId}` | GET | n/a | `{ok,globalId,messages,receipts,callSignals}` | `online/OnlineFallbackTransport.kt`, `chat/ChatDeliveryManager.kt` | PROVEN_RUNTIME |
| `/receipt/delivered` | POST | sourceGlobalId,targetGlobalId,messageId,createdAt | `{ok,status,eventId}` | `online/OnlineFallbackTransport.kt`, `chat/ChatDeliveryManager.kt` | PROVEN_RUNTIME |
| `/receipt/read` | POST | sourceGlobalId,targetGlobalId,messageId,createdAt | `{ok,status,eventId}` | `online/OnlineFallbackTransport.kt`, `chat/ChatDeliveryManager.kt` | PROVEN_RUNTIME |
| `/relay/edits/{globalId}` | GET | n/a | `{events...}` | `online/OnlineFallbackTransport.kt` | READY_IN_APP |
| `/relay/deletes/{globalId}` | GET | n/a | `{events...}` | `online/OnlineFallbackTransport.kt` | READY_IN_APP |
| `/session/prepare-route` | POST | sessionId, peerGlobalId, primaryRoute, senderGlobalId | `{ready,recommendedMode,relaySessionId,...}` | `online/OnlineFallbackTransport.kt` | READY_IN_APP |
| `/session/validate-route` | POST | session payload | `{ready,healthScore,...}` | `online/OnlineFallbackTransport.kt` | READY_IN_APP |
| `/session/heartbeat` | POST | session heartbeat payload | `{ready,...}` | `online/OnlineFallbackTransport.kt` | READY_IN_APP |
| `/session/start` | POST | callId, sourceGlobalId, targetGlobalId, createdAt | `{ok,status,callId,targetGlobalId}` | `diagnostics/VirtualPeerCallSignalProbe.kt` | PROVEN_RUNTIME |
| `/session/ringing` | POST | callId, sourceGlobalId, targetGlobalId, createdAt | `{ok,status,callId,targetGlobalId}` | `diagnostics/InternetServerOperatorReadinessProbe.kt` | READY_IN_APP |
| `/session/accept` | POST | callId, sourceGlobalId, targetGlobalId, createdAt | `{ok,status,callId,targetGlobalId}` | `diagnostics/InternetServerOperatorReadinessProbe.kt` | READY_IN_APP |
| `/session/reject` | POST | callId, sourceGlobalId, targetGlobalId, createdAt | `{ok,status,callId,targetGlobalId}` | `diagnostics/InternetServerOperatorReadinessProbe.kt` | READY_IN_APP |
| `/session/end` | POST | callId, sourceGlobalId, targetGlobalId, createdAt | `{ok,status,callId,targetGlobalId}` | `diagnostics/VirtualPeerCallSignalProbe.kt` | PROVEN_RUNTIME |
| `/health` | GET | n/a | `{ok,service,status,timestamp}` | `diagnostics/InternetServerOperatorReadinessProbe.kt`, `diagnostics/ServerTruthProbe.kt` | READY_IN_APP |
| `/ping` | GET | n/a | `{ok}` | `diagnostics/InternetServerOperatorReadinessProbe.kt` fallback | READY_IN_APP |
