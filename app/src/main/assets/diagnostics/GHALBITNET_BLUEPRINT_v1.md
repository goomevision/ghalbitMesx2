# GHALBITNET BLUEPRINT v1

Dokumen ini adalah patokan resmi fase teknis GHALBITNET agar implementasi tetap fokus, terukur, dan stabil.

## 1) Prinsip Inti

1. Bukti runtime di atas asumsi.
2. Internet operator dulu stabil, mesh menjadi penguat.
3. Call core minimal dulu, call advanced belakangan.
4. Jangan tambah fitur baru sebelum fondasi lulus uji.

## 2) Urutan Prioritas Arsitektur

1. Presence + register + heartbeat server.
2. Chat internet (send/receive).
3. Pending queue + delivery/read receipt.
4. PTT/voice note stabil.
5. Call signaling (invite/accept/reject/end).
6. Media voice relay.
7. Mesh/LAN/Nearby/Wi-Fi Direct sebagai fallback/penguat.

## 3) Definition of Done (Wajib Lulus)

Satu phase hanya dianggap selesai jika:

1. `assembleDebug` sukses.
2. Tidak ada `FATAL EXCEPTION` baru.
3. Ada log bukti runtime (bukan hanya kode).
4. Uji 2 HP lulus untuk skenario phase.
5. Ada laporan diagnosa markdown.

## 4) Log Bukti Wajib

- `GHALBIT-NET-TRUTH SEND`
- `GHALBIT-NET-TRUTH RECEIVE`
- `GHALBIT-NET-TRUTH PONG`
- `GHALBIT-NET-TRUTH RESULT`
- `GHALBIT-TCP-HEALTH ...`
- `GHALBIT-ROUTE-LOCK ...`
- `GHALBIT-ROUTE-EVIDENCE ...`
- `GHALBIT-CALL-AUDIO ...`
- `GHALBIT-VOICE-METRICS ...`
- `GHALBIT-MEDIA-PENDING ...`

## 5) Kontrak Stabilitas Route

1. Route lock aktif 15–30 detik setelah sukses.
2. Host `ECONNREFUSED` cooldown 30 detik.
3. Jangan turun cepat ke `PENDING_QUEUE` jika masih rediscovery.
4. Slot scheduler adalah soft priority, bukan pemaksa tunggal.

## 6) Kontrak Media/Offline

1. Peer offline/route belum ada -> status `PENDING`.
2. `FAILED_FINAL` hanya setelah TTL (>=24 jam).
3. Retry otomatis + retry manual tersedia.
4. Jangan drop data secara diam-diam.

## 7) Kontrak Call

1. `CallCoreMinimal` wajib hidup dulu:
   - mic -> encode -> kirim -> decode -> speaker (LAN direct)
2. Jika `rxFrames > 0` dan `played = 0` >3 detik -> aktifkan safe playback mode.
3. Call tidak boleh freeze saat relay lambat/gagal.

## 8) Dokumen Blueprint Wajib di Repo

- `app/src/main/assets/diagnostics/network_architecture_map.md`
- `app/src/main/assets/diagnostics/network_dead_code_candidates.md`
- `app/src/main/assets/diagnostics/server_operator_role_map.md`
- `app/src/main/assets/diagnostics/GHALBITNET_BLUEPRINT_v1.md`

## 9) Aturan Eksekusi Phase

1. Audit dulu, patch kecil aman, build, uji, baru lanjut.
2. Tidak refactor besar tanpa peta kabel.
3. Tidak hapus kode sebelum label:
   - `KEEP`
   - `REFACTOR`
   - `DELETE LATER`
   - `DELETE SAFE`

## 10) Gate Sebelum Fitur Baru

Fitur baru hanya boleh masuk jika:

1. Chat + pending + receipt stabil.
2. PTT stabil.
3. Call signaling stabil.
4. Network truth probe terbukti.
5. Dead code map selesai.

---

Status: **ACTIVE BASELINE**  
Branch utama kerja saat ini: `restore-ui-from-codex`
