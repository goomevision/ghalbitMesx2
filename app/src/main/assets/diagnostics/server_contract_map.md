# Server Contract Map (PHASE 300B)

Format: `Endpoint | Method | Request Body | Expected Response | Caller File | Status`

| Endpoint | Method | Request Body | Expected Response | Caller File | Status |
|---|---|---|---|---|---|
| `/identity/register` | POST | callId, displayName, publicKey, deviceIdHash, routeHint, updatedAt | `{ok,status,routeHint,updatedAt,error}` | `identity/IdentityServerClient.kt` | READY_IN_APP |
| `/identity/sync` | POST | sama seperti register | `{ok,status,...}` | `identity/IdentityServerClient.kt` | READY_IN_APP |
| `/identity/lookup/{callId}` | GET | n/a | `{ok, identity:{...}}` | `identity/IdentityServerClient.kt` | READY_IN_APP |
| `/identity/copy-reached-internet` | POST | identity copy payload | `{ok,...}` | `identity/IdentityServerClient.kt` | READY_IN_APP |
| `/identity/route-hint` | POST | callId, routeHint, updatedAt | `{ok,...}` | `identity/IdentityServerClient.kt` | READY_IN_APP |
| `/presence/heartbeat` | POST | globalId,nodeId,signature,networkType,... | `{ok,status,online,lastSeen,...}` | `online/OnlinePresenceManager.kt` | READY_IN_APP |
| `/presence/{targetGlobalId}` | GET | n/a | `{ok,presence:{...}}` | `online/OnlinePresenceManager.kt` | READY_IN_APP |
| `/relay/send` | POST | envelope chat/call/sos/signature | `{ok,status,messageId,ready,error}` | `online/OnlineFallbackTransport.kt` | READY_IN_APP |
| `/relay/inbox/{globalId}` | GET | n/a | `{messages,receipts}` | `online/OnlineFallbackTransport.kt` | READY_IN_APP |
| `/relay/ack` | POST | sender/target/messageId/signature | `{ok,...}` | `online/OnlineFallbackTransport.kt`, `chat/ChatDeliveryManager.kt` | READY_IN_APP |
| `/relay/read` | POST | sender/target/messageId/signature | `{ok,...}` | `online/OnlineFallbackTransport.kt`, `chat/ChatDeliveryManager.kt` | READY_IN_APP |
| `/relay/edits/{globalId}` | GET | n/a | `{events...}` | `online/OnlineFallbackTransport.kt` | READY_IN_APP |
| `/relay/deletes/{globalId}` | GET | n/a | `{events...}` | `online/OnlineFallbackTransport.kt` | READY_IN_APP |
| `/session/prepare-route` | POST | sessionId,peerGlobalId,primaryRoute,senderGlobalId | `{ready,recommendedMode,relaySessionId,...}` | `online/OnlineFallbackTransport.kt` | READY_IN_APP |
| `/session/validate-route` | POST | session payload | `{ready,healthScore,...}` | `online/OnlineFallbackTransport.kt` | READY_IN_APP |
| `/session/heartbeat` | POST | session heartbeat payload | `{ready,...}` | `online/OnlineFallbackTransport.kt` | READY_IN_APP |
| `/health` | GET | n/a | `{ok}` | (tidak ada caller aktif) | CODE_ONLY |
| `/ping` | GET | n/a | `{ok}` | (tidak ada caller aktif) | CODE_ONLY |
| `/session/start` | POST | call session start payload | `{ok,...}` | (tidak ada caller aktif, signaling via relay/send) | CODE_ONLY |
| `/session/accept` | POST | call session accept payload | `{ok,...}` | (tidak ada caller aktif, signaling via relay/send) | CODE_ONLY |
| `/session/end` | POST | call session end payload | `{ok,...}` | (tidak ada caller aktif, signaling via relay/send) | CODE_ONLY |

