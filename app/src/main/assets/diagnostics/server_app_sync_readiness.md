# Server App Sync Readiness

Status ringkas: **APP_READY_SERVER_PARTIAL_PROVEN**

Dasar penilaian ini memakai bukti runtime dan laporan yang sudah ada, bukan asumsi baru:

- `virtual_peer_server_presence_check.md`
- `virtual_peer_sos_server_proof.md`
- `virtual_peer_chat_server_proof.md`
- `virtual_peer_call_server_proof.md`
- `virtual_peer_chat_popup_flow.md`

## 1) Base URL yang dipakai app

- `BuildConfig.BASE_RELAY_URL`
- `BuildConfig.BASE_PRESENCE_URL`
- `BuildConfig.INTERNET_RELAY_CONFIGURED`

Sumber:

- `app/build.gradle`
- `online/OnlineFallbackTransport.kt`
- `online/OnlinePresenceManager.kt`
- `diagnostics/ServerTruthProbe.kt`

## 2) File inti yang memakai server

- `online/OnlineFallbackTransport.kt`
- `online/OnlinePresenceManager.kt`
- `chat/ChatDeliveryManager.kt`
- `diagnostics/VirtualPeerPresenceProbe.kt`
- `diagnostics/VirtualPeerChatProbe.kt`
- `diagnostics/VirtualPeerCallSignalProbe.kt`
- `diagnostics/InternetServerOperatorReadinessProbe.kt`

## 3) Endpoint app-side dan status terbaru

| Endpoint Group | Status terbaru | Catatan |
|---|---|---|
| `GET /health` | READY_IN_APP | Dipakai probe readiness/operator |
| `POST /presence/heartbeat` | PROVEN_RUNTIME | `REGISTER_OK`, `HEARTBEAT_OK`, `VISIBLE_ON_SERVER` sudah terbukti |
| `GET /presence/{globalId}` | PROVEN_RUNTIME | lookup presence runtime terbukti |
| `POST /relay/send` | PROVEN_RUNTIME | terbukti untuk SOS, chat, call signaling |
| `GET /relay/inbox/{globalId}` | PROVEN_RUNTIME | inbox HP A terbukti menerima SOS/chat/call signal |
| `POST /receipt/delivered` | PROVEN_RUNTIME | delivered receipt balik ke virtual peer terbukti |
| `POST /receipt/read` | PROVEN_RUNTIME | read receipt balik ke virtual peer terbukti |
| `POST /session/start` | PROVEN_RUNTIME | virtual call start ke HP A terbukti |
| `POST /session/end` | PROVEN_RUNTIME | virtual call end ke HP A terbukti |
| `POST /session/ringing` | READY_IN_APP | endpoint tersedia, belum ada proof terpisah terbaru |
| `POST /session/accept` | READY_IN_APP | endpoint tersedia, proof runtime khusus belum dipisah |
| `POST /session/reject` | READY_IN_APP | endpoint tersedia, proof runtime khusus belum dipisah |
| `POST /session/prepare-route` | READY_IN_APP | dipakai koordinasi route internet |
| `POST /session/validate-route` | READY_IN_APP | dipakai koordinasi route internet |
| `POST /session/heartbeat` | READY_IN_APP | dipakai route/session health |
| media relay fetch URL | READY_IN_APP | fetch URL ada; upload/media relay penuh belum terbukti runtime |

## 4) Kecocokan request/response model

- App dan operator server sekarang sinkron untuk:
  - presence
  - relay send/inbox
  - receipt delivered/read
  - call start/end signaling
- Endpoint receipt lama `relay/ack` dan `relay/read` sudah tidak lagi menjadi kontrak utama. Jalur aktif sekarang adalah:
  - `/receipt/delivered`
  - `/receipt/read`

## 5) Auth / token / signature

- App memakai payload JSON dengan signature/hash di beberapa jalur.
- Belum ada bukti mekanisme bearer-token global wajib.
- Untuk readiness saat ini, kontrak signature cukup untuk jalur operator yang sudah dibuktikan.

## 6) Timeout / retry / fallback

- `HttpURLConnection` timeout sudah ada di jalur transport online.
- `ChatDeliveryManager` + `PendingMessageStore` menangani retry aman.
- Fallback lokal -> server tidak merusak flow dasar yang sudah terbukti:
  - chat tetap delivered/read
  - SOS tetap masuk ke `SosAlertManager`
  - call signaling tetap masuk ke inbox peer

## 7) Pending queue server

- Sudah terbukti secara praktis melalui:
  - virtual chat
  - virtual SOS
  - inbox consume-once
- Replay berulang sudah dibenahi di server operator inbox fetch.

## 8) Delivery / read receipt server

- **PROVEN_RUNTIME**
- HP A sudah terbukti mengirim:
  - delivered
  - read
- dan virtual peer/server sudah menerima bukti itu.

## 9) Gap yang masih tersisa

- call signaling `ringing/accept/reject` belum dipisahkan proof runtime setegas `start/end`
- media relay internet penuh untuk audio/call belum proven end-to-end via server operator
- auto status “server down vs server ready” sudah ada di probe/recovery, tetapi belum dijadikan satu indikator UI operator tunggal
