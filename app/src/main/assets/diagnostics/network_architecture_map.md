# GHALBITNET Network Architecture Map (PHASE 299A–299F)

Status: audit-only snapshot from branch `restore-ui-from-codex`.

## 1) Modul jaringan utama

| Area | File utama | Fungsi |
|---|---|---|
| Discovery UDP | `discovery/UdpDiscovery.kt`, `discovery/DiscoveryManager.kt`, `discovery/PeerDiscoveryHandler.kt` | Broadcast HELLO, terima node, isi cache node. |
| Socket TCP | `network/MeshSocketServer.kt`, `network/MeshSocketClient.kt` | Listener port mesh + kirim paket point-to-point. |
| Reliability | `network/ReliablePacketSender.kt`, `network/AckTracker.kt` | Retry + tracking ACK untuk paket kritikal. |
| Routing chat | `chat/AdaptiveRouteManager.kt`, `chat/RouteTimeSlotScheduler.kt` | Ranking jalur + slot boost + route lock + evidence. |
| Routing call | `routing/CallRouteDiscoveryManager.kt`, `call/GhalbitCallManager.kt`, `call/CallManager.kt` | Lookup route call, pilih signaling channel. |
| Internet fallback | `online/OnlineFallbackTransport.kt`, `online/OnlinePresenceManager.kt` | Relay send/inbox + presence online/offline. |
| Local link | `wifi/WifiDirectManager.kt`, `nearby/NearbyManager.kt` | Fallback link lokal tambahan. |
| Keepalive | `chat/ConversationKeepAliveManager.kt`, `core/runtime/MeshHeartbeatTicker.kt` | Ping/Pong conversation + heartbeat runtime. |
| SOS | `sos/SosAlertManager.kt`, `MainActivity.sendSos()` | Broadcast SOS mesh + fallback internet SOS. |
| File/media | `file/FileTransferManager.kt`, `chat/ChatActivity.kt` | File/audio/image transfer dan status. |
| Call runtime | `call/CallSessionActivity.kt`, `call/FullDuplexCallEngine.kt` | State call, RX/TX audio, fallback PTT. |
| Network handoff | `core/runtime/NetworkHandoffMonitor.kt` | Deteksi subnet/IP berubah + restart discovery/listener. |

## 2) Siapa memanggil siapa (ringkas)

- `MainActivity.startMesh()`:
  - start `MeshSocketServer`
  - start `UdpDiscovery` + listener + heartbeat broadcast
  - bind `NetworkHandoffMonitor`
- `MeshSocketServer.onPacketReceived -> MainActivity.processIncomingPacket()`
- `ChatActivity` kirim pesan/media via:
  - `ChatDeliveryManager` (state + retry)
  - `FileTransferManager` (media/file)
- `CallSessionActivity` pakai:
  - `CallManager` / `GhalbitCallManager`
  - `FullDuplexCallEngine` (realtime)
  - fallback PTT via file route
- `OnlineFallbackTransport` dipakai saat mode internet aktif:
  - `/relay/send`
  - `/relay/inbox/{globalId}`
  - `/session/prepare-route`, `/session/validate-route`, `/session/heartbeat`

## 3) Jalur data nyata per fitur

### Chat
1. UI `ChatActivity` -> `ChatDeliveryManager`
2. Route dipilih `AdaptiveRouteManager`
3. Kirim via TCP mesh (`MeshSocketClient`) atau internet (`OnlineFallbackTransport.sendMessageViaInternet`)
4. ACK/READ diproses `ChatDeliveryManager.handleAck/handleRead`

### SOS
1. `MainActivity.sendSos()`
2. Mesh broadcast per node (`MeshSocketClient.sendBlocking`)
3. Jika node kosong + internet ada -> `OnlineFallbackTransport.sendSosViaInternet()`
4. Incoming -> `handleIncomingSos()`

### PTT / audio note
1. `ChatActivity` record voice
2. Kirim sebagai file via `FileTransferManager`
3. Di call fallback: `CallSessionActivity.sendFallbackVoice()` + playback file

### Voice call
1. `CallSessionActivity` start/accept
2. Signaling via `CallManager` + `GhalbitCallManager`
3. Realtime audio via `FullDuplexCallEngine`
4. Jika realtime gagal -> fallback PTT mode

### File/gambar
1. `ChatActivity` -> `DraftAttachmentStore` / metadata reader
2. `FileTransferManager.sendFile(...)`
3. Delivery state tetap dikelola `ChatDeliveryManager`

### Internet path
- Presence + relay inbox/send via `OnlineFallbackTransport` dan manager online identity/presence.

### Mesh lokal
- Discovery UDP + socket TCP + route manager.

## 4) NetworkTruthProbe (PHASE 299B)

Lokasi:
- `java/com/ghalbitnet/meshx2/diagnostics/NetworkTruthProbe.kt`

Fungsi:
- kirim `NET_TRUTH_PING`
- balas `NET_TRUTH_PONG`
- hitung RTT
- catat transport (`TCP` saat ini)
- log bukti:
  - `GHALBIT-NET-TRUTH SEND`
  - `GHALBIT-NET-TRUTH RECEIVE`
  - `GHALBIT-NET-TRUTH PONG`
  - `GHALBIT-NET-TRUTH RESULT`

Integrasi ringan:
- dipanggil dari `MainActivity.updateUI()` (throttle interval)
- diproses dari `MainActivity.processIncomingPacket()`

## 5) Kebenaran runtime (saat audit ini)

Sudah terbukti dari log:
- route slot aktif
- route lock aktif
- media offline masuk pending 24 jam

Belum terbukti konsisten:
- call audio rx/play stabil lintas handoff
- route evidence dari semua sumber (SOS/PTT/CALL) di semua skenario dua HP
- internet relay inbox end-to-end untuk semua call signaling

