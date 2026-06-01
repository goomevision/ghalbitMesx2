# CURRENT NETWORK TRUTH (PHASE 300A REVISION — EVIDENCE-FIRST MODE)

Sumber bukti yang dipakai:
- Log runtime historis yang sudah ada (termasuk discovery multi-device, route slot/lock, media pending, SOS/PTT).
- Log terbaru yang sudah diambil:
  - `GHALBIT-ROUTE-SLOT`
  - `GHALBIT-ROUTE-LOCK`
  - `GHALBIT-MEDIA-PENDING`
- Dokumen:
  - `network_architecture_map.md`
  - `network_dead_code_candidates.md`
  - `server_operator_role_map.md`

## A. Jalur yang terbukti hidup (runtime evidence)

1. **Discovery (UDP HELLO loop + roster update)**
   - Terbukti dari log historis discovery aktif dan update kontak/roster.
2. **Route scheduler + route lock**
   - Terbukti:
     - `GHALBIT-ROUTE-SLOT ... preferred=... boosted=...`
     - `GHALBIT-ROUTE-LOCK peer=... route=LOCAL_MESH_DIRECT ...`
3. **Media offline pending**
   - Terbukti:
     - `GHALBIT-MEDIA-PENDING id=... reason=peerOffline expiresIn=24h`
4. **SOS path (mesh + internet fallback path di kode dan event runtime historis)**
   - Log historis menunjukkan event SOS dikirim/diterima.
5. **PTT fallback path**
   - Log dan flow runtime historis menunjukkan PTT aktif saat realtime voice tidak stabil.
6. **TCP listener watchdog/cooldown logic**
   - Sudah diterapkan dan build stabil; route cooldown terlihat dari perilaku lock release/failure handling.

## B. Jalur yang belum terbukti penuh (partial runtime)

1. **Chat internet end-to-end dengan receipt konsisten**
   - Path ada dan dipakai sebagian, tapi bukti sent/delivered/read penuh lintas sesi belum konsisten terdokumentasi.
2. **Call signaling internet end-to-end penuh**
   - Invite/accept/reject/end path ada, namun bukti urutan lengkap stabil lintas kondisi belum kuat.
3. **Route evidence dari semua sumber (SOS/PTT/CALL/CHAT)**
   - Infrastruktur sudah ada, bukti runtime konsisten semua sumber belum lengkap.
4. **Network handoff call recovery**
   - Handoff monitor aktif; kualitas recovery call realtime lintas perpindahan jaringan masih parsial.

## C. Jalur yang ada di kode namun belum terbukti runtime matang

1. **Voice media relay internet sebagai jalur utama**
   - Ada fondasi, belum ada bukti stabil setara aplikasi internet-first.
2. **Sebagian adapter call lanjutan (SIP/Linphone/WebRTC multilayer fallback)**
   - Ada di kode, bukti operasional lapangan belum merata.
3. **Fitur AI voice/transcript lanjutan**
   - Ada implementasi, bukan jalur bukti utama saat ini.

## D. Klasifikasi bukti per komponen

| Komponen | Klasifikasi | Bukti |
|---|---|---|
| Discovery | PROVEN_RUNTIME | Log historis discovery aktif + roster/live update |
| UDP | PROVEN_RUNTIME | HELLO/discovery diproses |
| TCP | PARTIAL_RUNTIME | Listener/watchdog aktif, namun acceptance pattern lintas skenario belum terekam lengkap |
| Chat | PARTIAL_RUNTIME | Path aktif + pending/delivery manager hidup, internet receipt belum full-proof |
| SOS | PROVEN_RUNTIME | Event SOS historis + flow aktif |
| PTT | PROVEN_RUNTIME | PTT fallback path hidup dan dipakai |
| File transfer | PARTIAL_RUNTIME | Jalur aktif di kode + dipakai, bukti reliabilitas multi-skenario belum penuh |
| Media pending | PROVEN_RUNTIME | `GHALBIT-MEDIA-PENDING ... expiresIn=24h` |
| Call (realtime voice) | PARTIAL_RUNTIME | Signaling/engine ada, audio E2E stabil belum terbukti menyeluruh |
| Relay internet | PARTIAL_RUNTIME | Endpoint dipanggil, namun operator-level behavior belum final |
| Server operator role | CODE_ONLY / PARTIAL_RUNTIME | Client contract ada; backend behavior tidak seluruhnya dapat diverifikasi dari repo ini |

## E. PROVEN_RUNTIME / PARTIAL_RUNTIME / CODE_ONLY / UNKNOWN

### PROVEN_RUNTIME
- Discovery
- UDP discovery
- Route slot
- Route lock
- Media pending 24 jam
- SOS (runtime event historis)
- PTT fallback

### PARTIAL_RUNTIME
- TCP listener health in-field behavior
- Chat internet sent/delivered/read
- File transfer reliability
- Call signaling end-to-end
- Realtime call audio stability
- Relay fallback consistency

### CODE_ONLY
- Full internet operator parity (WhatsApp-like) sebagai satu sistem terintegrasi
- Voice relay internet matang
- Beberapa adapter call advanced yang belum jadi jalur bukti utama

### UNKNOWN
- Kualitas operasional backend server (retention policy queue, scaling, retry policy server-side) karena source backend tidak lengkap di repo ini.

## F. Ringkasan kebenaran jaringan saat ini

1. **Fondasi mesh + routing adaptif sudah hidup.**
2. **Offline behavior media sudah membaik (pending, bukan gagal instan).**
3. **Lapisan call terlalu kompleks dibanding bukti runtime yang ada.**
4. **Internet operator role masih belum setara aplikasi internet-first matang.**

