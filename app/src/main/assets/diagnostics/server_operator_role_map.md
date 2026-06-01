# GHALBITNET Server Operator Role Map (PHASE 299G)

Tujuan audit: memetakan apakah mode internet sudah punya peran server operator inti.

## Ringkasan cepat

- Client Android **sudah** punya modul untuk relay send/inbox, identity sync/lookup, presence heartbeat, dan call signal relay.
- Repo ini belum memuat implementasi backend server (hanya client endpoints). Jadi sebagian peran operator masih bergantung layanan eksternal.

## Matriks peran server operator

| Peran operator | Status di client | Bukti file | Catatan |
|---|---|---|---|
| Presence Server | Sebagian hidup | `online/OnlinePresenceManager.kt`, `online/OnlineFallbackTransport.kt` | Ada bind/register online, perlu bukti backend stabil. |
| Login/Register Device | Ada | `identity/IdentityServerClient.kt` (`/identity/register`, `/identity/sync`) | Sudah ada request path. |
| Heartbeat online/offline | Ada | `OnlineFallbackTransport` (`/session/heartbeat`) + presence manager | Perlu observasi konsistensi timeout server. |
| Peer lookup | Ada | `IdentityServerClient.lookupIdentity()` (`/identity/lookup/:callId`) | Dipakai call route discovery. |
| Message relay | Ada | `OnlineFallbackTransport.sendMessageViaInternet()` (`/relay/send`) | Sudah bawa metadata identitas + signature. |
| Pending queue server | Ada (inbox pull) | `OnlineFallbackTransport.fetchInbox()` (`/relay/inbox/{globalId}`) | Store server-side belum terlihat dari repo client. |
| Delivery receipt | Sebagian | receipt fetch diproses dari inbox | ACK delivered/read end-to-end tergantung backend. |
| Call signaling | Ada | `call/InternetRelaySignalingChannel.kt`, `OnlineFallbackTransport.sendCallSignalViaInternet()` | Invite/accept/end sudah punya jalur. |
| Ringing/accept/reject/end | Sebagian | event call di `CallManager`/`CallSessionActivity` | Perlu bukti inbox peer menerima full sequence. |
| Media relay voice | Belum matang | ada fallback signaling, audio relay penuh belum final | Saat ini call media belum terbukti stabil lintas internet. |
| Fallback direct fail -> relay | Ada | `GhalbitCallManager`, `CallRouteDiscoveryManager`, `OnlineFallbackTransport` | Sudah ada, masih perlu tuning runtime. |

## Endpoint yang terdeteksi dipanggil client

- Identity:
  - `/identity/register`
  - `/identity/sync`
  - `/identity/lookup/{callId}`
- Relay/session:
  - `/relay/send`
  - `/relay/inbox/{globalId}`
  - `/session/prepare-route`
  - `/session/validate-route`
  - `/session/heartbeat`

## Gap utama

1. Tidak ada backend source di repo ini untuk memverifikasi rule server/operator secara penuh.
2. Delivery receipt call/message bergantung implementasi server inbox/receipt.
3. Media voice relay internet belum menjadi jalur sederhana tunggal yang tervalidasi.

## Rancangan minimal interface operator (proposal)

`GhalbitInternetOperator` (kontrak fungsional):

1. `registerDevice(peerId, publicKey)`
2. `heartbeat(peerId)`
3. `lookupPeer(peerId)`
4. `sendMessage(toPeerId, payload)`
5. `fetchPendingMessages(peerId)`
6. `ackDelivered(messageId)`
7. `ackRead(messageId)`
8. `startCall(toPeerId)`
9. `acceptCall(callId)`
10. `rejectCall(callId)`
11. `endCall(callId)`

Catatan: phase ini audit-only, jadi kontrak belum diimplementasi sebagai fitur baru.

