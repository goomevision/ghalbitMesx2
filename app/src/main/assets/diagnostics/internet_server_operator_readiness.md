# Internet Server Operator Readiness

## Scope

Server-first runtime readiness for:

- presence register / heartbeat / lookup
- chat relay send / inbox / delivered / read
- SOS relay to app path
- call signaling start / end, with ringing / accept / reject endpoint support present

## Ringkasan status saat ini

**Server readiness: PARTIAL-BUT-PROVEN**

Yang sudah terbukti oleh log dan diagnostics terakhir:

- HP A **visible on server**
- register / heartbeat / lookup **berhasil**
- virtual HP B -> server -> HP A **chat** berhasil
- delivered / read receipt **berhasil**
- virtual HP B -> server -> HP A **SOS** berhasil
- virtual HP B -> server -> HP A **CALL_START / CALL_END** berhasil
- background message notification saat app di belakang **berhasil**

Yang belum terbukti penuh:

- `CALL_RINGING / CALL_ACCEPT / CALL_REJECT` sebagai proof runtime terpisah
- media relay internet dua arah penuh untuk audio call via server operator

## Runtime Step

Auto Diagnostic step:

- `SERVER_OPERATOR_FULL_CHECK`

Source utama:

- `diagnostics/InternetServerOperatorReadinessProbe.kt`
- `diagnostics/VirtualPeerPresenceProbe.kt`
- `diagnostics/VirtualPeerChatProbe.kt`
- `diagnostics/VirtualPeerCallSignalProbe.kt`
- `chat/ChatDeliveryManager.kt`

## Status Model

- `READY`: semua operator checks lulus
- `PARTIAL`: server aktif dan sebagian besar jalur dasar terbukti, tetapi belum semua jalur call/media lengkap
- `FAILED`: server terkonfigurasi tetapi checks gagal
- `SERVER_NOT_CONFIGURED`: relay/presence URL kosong atau invalid
- `FAKE_SERVER_PASS_ONLY`: simulasi lolos tetapi server nyata belum terbukti

## Bukti runtime terakhir yang relevan

### Presence

- `REGISTER_OK`
- `HEARTBEAT_OK`
- `LOOKUP_OK`
- `VISIBLE_ON_SERVER`

### Chat

- inbox relay HP A menerima message virtual
- notification popup dan background notification terbukti
- tap notification membuka chat yang benar
- `DELIVERED`
- `READ`

### SOS

- inbox relay HP A menerima SOS virtual
- routed ke `SosAlertManager`
- UI broadcast dan notification terbukti

### Call signaling

- `CALL_START_OK`
- `CALL_END_OK`
- event masuk ke `callSignals` inbox HP A

## Smart recovery / online-offline detection

App sudah punya dasar deteksi server online/offline melalui:

- `RelayConfigValidator`
  - health check ke `/health`
  - validasi URL relay/presence
- `InternetServerOperatorReadinessProbe`
  - cek base URL, health, presence, relay, receipt, session
- `OnlinePresenceManager`
  - saat heartbeat gagal, presence lokal ditandai offline
- `SmartRecoveryEngine`
  - memetakan:
    - missing base URL -> `SERVER_NOT_CONFIGURED`
    - timeout -> `SERVER_TIMEOUT`
    - 401/403 -> `AUTH_REQUIRED`
    - 404 -> `ENDPOINT_MISSING`
    - 500 -> `SERVER_ERROR`
    - refused/down -> `SERVER_DOWN`

## Fallback lokal -> server

Status saat ini: **tidak ada bukti bahwa fallback lokal -> server merusak chat/SOS/call signaling dasar**.

Bukti:

- chat tetap terkirim dan receipt tetap bersih
- SOS tetap masuk ke jalur alert asli app
- call signaling `start/end` tetap masuk ke inbox peer
- background notification tetap muncul setelah identity runtime relay diselaraskan

## Small safe patch yang diterapkan pada fase audit ini

- `ServerTruthProbe.endpointCatalog()` diselaraskan dengan kontrak/operator runtime yang sekarang:
  - menambahkan presence endpoints
  - menambahkan session `ringing/reject`
  - mengubah endpoint health/session yang sudah dipakai probe menjadi `READY_IN_APP`

## Kesimpulan

App **siap terkoneksi server** untuk fondasi operator berikut:

- online presence
- relay message
- delivered/read receipt
- SOS relay
- basic call signaling

App **belum sepenuhnya siap** untuk mengklaim operator server sebagai pengganti penuh WhatsApp-grade call/media, karena:

- proof runtime `ringing/accept/reject` belum dipisahkan rapi
- media voice dua arah melalui operator server belum terbukti penuh
