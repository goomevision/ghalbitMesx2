# INTERNET OPERATOR GAP REPORT (PHASE 300A REVISION)

Fokus: Apa yang dibutuhkan agar GhalbitNet bisa beroperasi seperti WhatsApp melalui internet tanpa bergantung mesh.

## 1) Kondisi saat ini (evidence-based)

- Client sudah memiliki komponen:
  - identity register/sync/lookup
  - relay send/inbox
  - session prepare/heartbeat
  - call signaling channel internet
- Namun runtime yang terbukti kuat masih dominan di jalur mesh/PTT/pending lokal.
- Operator internet belum menjadi jalur utama yang deterministic untuk semua state komunikasi.

## 2) Gap terbesar menuju internet-first matang

### GAP-1: Presence dan session authority belum menjadi sumber kebenaran tunggal
- Saat ini status online/offline masih bercampur antara sinyal lokal dan relay.
- Dibutuhkan server presence authoritative (TTL, heartbeat timeout, single source of truth).

### GAP-2: Pending queue server belum terbukti sebagai store-and-forward utama
- Client bisa pull inbox, tetapi perilaku queue server (retention, ordering, dedup, replay) belum terdokumentasi end-to-end.

### GAP-3: Delivery semantics belum setara chat app internet matang
- Perlu model status ketat:
  - SENT (server accepted)
  - DELIVERED (peer fetched)
  - READ (peer opened)
- Saat ini sebagian status masih bergantung konteks mesh/local.

### GAP-4: Call signaling belum punya orchestration server yang tegas
- Invite/accept/reject/end ada, tapi belum ada bukti konsisten untuk:
  - ringing timeout policy
  - re-invite / re-sync session
  - recovery saat peer reconnect

### GAP-5: Media call internet belum punya jalur minimal stabil tunggal
- Layer call terlalu bercabang (advanced) dibanding bukti runtime.
- Perlu jalur minimal internet audio yang deterministic dulu.

### GAP-6: Observability server-side belum cukup
- Butuh correlation id tunggal lintas:
  - messageId / callId / sessionId
  - send -> queue -> fetch -> ack
- Tanpa ini, debug tetap spekulatif.

## 3) Audit sistem call (kompleksitas)

## 3.1 Jumlah file call aktif (indikatif)
- Folder `call/` berisi sangat banyak file (puluhan; >50) dengan state/mode/adapter berlapis.

## 3.2 File call tidak terbukti runtime kuat (indikatif evidence)
- Adapter/fitur lanjutan tertentu ada di kode tetapi bukti runtime lapangan belum kuat/merata:
  - Linphone/SIP/WebRTC multi-adapter path
  - sebagian AI voice/transcript path
  - beberapa interface transport lanjutan

## 3.3 Apakah call terlalu kompleks?
- **Ya, relatif terlalu kompleks terhadap bukti runtime saat ini.**
- Dampak:
  1. Sulit isolasi root cause audio diam.
  2. Banyak state transisi menambah ambiguity.
  3. Debugging membutuhkan effort tinggi untuk setiap regresi.

## 3.4 Kandidat pemecahan aman

### CallCoreMinimal
Tujuan:
- Jalur tunggal sederhana untuk voice call internet/LAN direct.
- State minimum: dialing/ringing/connected/ended/failed.
- Audio pipeline minimum: capture -> encode -> transport -> decode -> playback.
- Log minimal dan deterministik.

### CallAdvanced
Isi:
- mode adaptif berlapis
- AI transcript fallback
- multipath switching agresif
- advanced retransmit/capacitor policy

Aturan:
- `CallAdvanced` aktif hanya setelah `CallCoreMinimal` lulus KPI runtime.

## 4) Kebutuhan minimum agar setara “internet operator”

Implementasi operasional server (bukan hanya endpoint ada):
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

Ditambah kebijakan server:
- idempotency key
- dedup
- TTL queue
- retry/backoff server-side
- authoritative presence timeout
- audit log per transaction

## 5) Rekomendasi phase berikutnya (tanpa fitur besar baru)

### PHASE 300B — Internet Operator Contract Hardening
- Standarisasi kontrak payload/status (message + call) dan status lifecycle server-authoritative.

### PHASE 300C — CallCoreMinimal Isolation Plan
- Pisahkan jalur call minimal dari call advanced (tanpa menghapus file dulu).

### PHASE 300D — Delivery Semantics Enforcement
- Pastikan transisi status SENT/DELIVERED/READ berbasis event server, bukan asumsi lokal.

### PHASE 300E — Observability & Correlation IDs
- Wajib correlation id lintas chat/call untuk audit end-to-end.

---

Kesimpulan:
- GhalbitNet sudah kuat di fondasi mesh adaptif.
- Untuk menjadi internet-first matang, server harus naik kelas dari sekadar relay endpoint menjadi **operator komunikasi authoritative**.

